package com.twitter.util.tunable

import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction
import scala.annotation.varargs
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * A Map that can be used to access [[Tunable]]s using [[TunableMap.Key]]s.
 */
abstract class TunableMap { self =>

  /**
   * Returns a [[Tunable]] for the given `key.id` in the [[TunableMap]]. If the [[Tunable]] is not
   * of type `T` or a subclass of type `T`, throws a [[ClassCastException]]
   */
  def apply[T](key: TunableMap.Key[T]): Tunable[T]

  /**
   * Returns a String representation of a [[TunableMap]], in the form:
   * TunableMap(id1 -> value1, id2 -> value2)
   *
   * Entries are sorted alphabetically by id.
   */
  def contentString: String = {
    val contents = entries.toSeq
      .sortBy(_.key.id)
      .map {
        case TunableMap.Entry(key, value, _) =>
          s"${key.id} -> $value"
      }
      .mkString(", ")
    s"TunableMap($contents)"
  }

  /**
   * Returns an Iterator over [[TunableMap.Entry]] for each [[Tunable]] in the map with a value.
   */
  def entries: Iterator[TunableMap.Entry[_]]

  /**
   * Compose this [[TunableMap]] with another [[TunableMap]]. [[Tunables]] retrieved from
   * the composed map prioritize the values of [[Tunables]] in the this map over the other
   * [[TunableMap]].
   */
  def orElse(that: TunableMap): TunableMap = (self, that) match {
    case (NullTunableMap, map) => map
    case (map, NullTunableMap) => map
    case _ =>
      new TunableMap with TunableMap.Composite {
        def apply[T](key: TunableMap.Key[T]): Tunable[T] =
          self(key).orElse(that(key))

        def entries: Iterator[TunableMap.Entry[_]] = {
          val dedupedTunables = mutable.Map.empty[String, TunableMap.Entry[_]]
          that.entries.foreach {
            case entry @ TunableMap.Entry(key, _, _) =>
              dedupedTunables.put(key.id, entry)
          }

          // entries in `self` take precedence.
          self.entries.foreach {
            case entry @ TunableMap.Entry(key, _, _) =>
              dedupedTunables.put(key.id, entry)
          }
          dedupedTunables.valuesIterator
        }

        def components: Seq[TunableMap] =
          Seq(self, that)
      }
  }
}

object TunableMap {

  /**
   * A marker interface in support of [[components(TunableMap)]]
   */
  private[tunable] trait Composite {
    def components: Seq[TunableMap]
  }

  /**
   * Get the component [[TunableMap]]s that make up `tunableMap`. For Composite [[TunableMap]]s,
   * this returns a Seq of the components. Otherwise, it returns a Seq of `tunableMap`.
   *
   * Used for testing.
   */
  def components(tunableMap: TunableMap): Seq[TunableMap] =
    tunableMap match {
      case composite: Composite =>
        composite.components.flatMap(components)
      case _ =>
        Seq(tunableMap)
    }

  private case class TypeAndTunable[T](tunableType: Class[T], tunable: Tunable.Mutable[T])

  case class Entry[T](key: TunableMap.Key[T], value: T, source: String)

  /**
   * Class used to retrieve a [[Tunable]] with id `id` of type `T`.
   */
  final case class Key[T](id: String, clazz: Class[T])

  /**
   * Create a [[TunableMap]] out of the given [[TunableMap TunableMaps]].
   *
   * If `tunableMaps` is empty, [[NullTunableMap]] will be returned.
   */
  @varargs
  def of(tunableMaps: TunableMap*): TunableMap = {
    val start: TunableMap = NullTunableMap
    tunableMaps.foldLeft(start) {
      case (acc, tm) =>
        acc.orElse(tm)
    }
  }

  /**
   * A [[TunableMap]] that forwards all calls to `underlying`.
   */
  trait Proxy extends TunableMap {

    protected def underlying: TunableMap

    def apply[T](key: TunableMap.Key[T]): Tunable[T] =
      underlying(key)

    def entries: Iterator[TunableMap.Entry[_]] = underlying.entries
  }

  object Key {
    def apply[T](id: String)(implicit m: Manifest[T]): Key[T] =
      Key[T](id, m.runtimeClass.asInstanceOf[Class[T]])
  }

  /**
   * A [[TunableMap]] that can be updated via `put` and `clear` operations. Putting
   * a value for a given `id` will update the current value for the `id`, or create
   * a new [[Tunable]] if it does not exist. The type of the new value must match that of the
   * existing value, or a [[ClassCastException]] will be thrown.
   *
   * `apply` returns a [[Tunable]] for a given [[TunableMap.Key]] and creates it if does not already
   * exist. Updates to the [[TunableMap]] update the underlying [[Tunable]]s; for example, a
   * [[Tunable]] returned from `TunableMap.apply` will produce an updated value when
   * `Tunable.apply()` is called if the [[TunableMap]] is updated. `clear` clears the
   * underlying [[Tunable]] for a given [[TunableMap.Key]] but does not remove it from the map; this
   * has the effect that calling `Tunable.apply()` on a previously retrieved [[Tunable]] produces
   * `None`. This behavior is desirable because a [[Tunable]]'s value may be cleared and re-set,
   * and we want [[Tunable]] applications to see those updates.
   */
  abstract class Mutable extends TunableMap {

    /**
     * Update map to have the contents of `newMap`.
     *
     * Updates to each [[Tunable]] in the map are atomic, but the change
     * is not atomic at the macro level.
     */
    private[tunable] def replace(newMap: TunableMap): Unit = {

      // clear keys no longer present
      (entries.map(_.key).toSet -- newMap.entries.map(_.key).toSet).foreach { key =>
        clear(key)
      }

      // add/update new tunable values
      ++=(newMap)
    }

    /**
     * Add all entries in `that` [[TunableMap]] to this [[TunableMap]]. Entries already present in
     * this map are updated. Updates to each [[Tunable]] in the map are atomic, but the change
     * is not atomic at the macro level.
     */
    def ++=(that: TunableMap): Unit =
      that.entries.foreach {
        case TunableMap.Entry(key, value, _) =>
          put(key.id, key.clazz, value)
      }

    /**
     * Remove all entries by key in `that` [[TunableMap]] from this [[TunableMap]]. Removal of each
     * [[Tunable]] in the map are atomic, but the change is not atomic at the macro level.
     */
    def --=(that: TunableMap): Unit =
      that.entries.foreach {
        case TunableMap.Entry(key, _, _) =>
          clear(key)
      }

    /**
     * Java-friendly API
     */
    def put[T](id: String, clazz: Class[T], value: T): Key[T]

    /**
     * Put a [[Tunable]] with id `id` and value `value` into the [[TunableMap]]. If the [[Tunable]]
     * for that `id` already exists, update the value to `value`.
     */
    final def put[T](id: String, value: T)(implicit m: Manifest[T]): Key[T] =
      put[T](id, m.runtimeClass.asInstanceOf[Class[T]], value)

    /**
     * Clear a [[Tunable]] in [[TunableMap]]. This does not remove the Tunable from the Map, but
     * rather clears its value such that applying it will produce `None`.
     */
    def clear[T](key: Key[T]): Unit
  }

  /**
   * Create a thread-safe map of [[Tunable.Mutable]]s with the given `source`
   */
  def newMutable(source: String): Mutable =
    newMutable(Some(source))

  /*
   * Create a thread-safe map of [[Tunable.Mutable]]s with no specified source
   */
  def newMutable(): Mutable =
    newMutable(None)

  private[this] def newMutable(source: Option[String]): Mutable = new Mutable {

    override def toString: String = source match {
      case Some(src) => src
      case None => s"TunableMap.Mutable@${Integer.toHexString(hashCode())}"
    }

    // We use a ConcurrentHashMap to synchronize operations on non-thread-safe [[Tunable.Mutable]]s.
    private[this] val tunables = new ConcurrentHashMap[String, TypeAndTunable[_]]()

    private[this] def clearTunable[T](
      key: Key[T]
    ): BiFunction[String, TypeAndTunable[_], TypeAndTunable[_]] =
      new BiFunction[String, TypeAndTunable[_], TypeAndTunable[_]] {
        def apply(id: String, curr: TypeAndTunable[_]): TypeAndTunable[_] = {
          if (curr != null) {
            curr.tunable.clear()
          }
          curr
        }
      }

    private[this] def getOrAdd[T](
      key: Key[T]
    ): BiFunction[String, TypeAndTunable[_], TypeAndTunable[_]] =
      new BiFunction[String, TypeAndTunable[_], TypeAndTunable[_]] {
        def apply(id: String, curr: TypeAndTunable[_]): TypeAndTunable[_] =
          if (curr != null) {
            if (key.clazz.isAssignableFrom(curr.tunableType)) {
              curr
            } else {
              throw new ClassCastException(
                s"Tried to retrieve a Tunable of type ${key.clazz}, but TunableMap contained a " +
                  s"Tunable of type ${curr.tunableType} for id ${key.id}"
              )
            }
          } else {
            TypeAndTunable(key.clazz, Tunable.emptyMutable[T](key.id))
          }
      }

    private[this] def updateOrAdd[T](
      key: Key[T],
      value: T
    ): BiFunction[String, TypeAndTunable[_], TypeAndTunable[_]] =
      new BiFunction[String, TypeAndTunable[_], TypeAndTunable[_]] {
        def apply(id: String, curr: TypeAndTunable[_]): TypeAndTunable[_] =
          if (curr != null) {
            if (curr.tunableType == key.clazz) {
              curr.tunable.asInstanceOf[Tunable.Mutable[T]].set(value)
              curr
            } else {
              throw new ClassCastException(
                s"Tried to update a Tunable of type ${key.clazz}, but TunableMap contained a " +
                  s"Tunable of type ${curr.tunableType} for id ${key.id}"
              )
            }
          } else {
            TypeAndTunable(key.clazz, Tunable.mutable[T](id, value))
          }
      }

    def put[T](id: String, clazz: Class[T], value: T): Key[T] = {
      val key = Key(id, clazz)
      tunables.compute(id, updateOrAdd(key, value))
      key
    }

    def clear[T](key: Key[T]): Unit =
      tunables.computeIfPresent(key.id, clearTunable(key))

    def apply[T](key: Key[T]): Tunable[T] = {
      tunables.compute(key.id, getOrAdd[T](key)).tunable.asInstanceOf[Tunable.Mutable[T]]
    }

    def entries: Iterator[TunableMap.Entry[_]] = {
      val entryOpts: Iterator[Option[TunableMap.Entry[_]]] = tunables.asScala.iterator.map {
        case (id, TypeAndTunable(tunableType, tunable)) =>
          tunable().map { value =>
            TunableMap.Entry(TunableMap.Key(id, tunableType), value, toString)
          }
      }
      entryOpts.flatten
    }
  }
}

/**
 * A [[TunableMap]] that returns a [[Tunable.none]] for every [[TunableMap.Key]]
 */
object NullTunableMap extends TunableMap {

  def apply[T](key: TunableMap.Key[T]): Tunable[T] =
    Tunable.none[T]

  def entries: Iterator[TunableMap.Entry[_]] = Iterator.empty
}
