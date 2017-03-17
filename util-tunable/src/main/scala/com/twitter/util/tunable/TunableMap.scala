package com.twitter.util.tunable

import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction
import scala.collection.JavaConversions._
import scala.collection.mutable

/**
 * A Map that can be used to access [[Tunable]]s using [[TunableMap.Key]]s.
 */
private[twitter] abstract class TunableMap { self =>

  /**
   * Returns a [[Tunable]] for the given `key.id` in the [[TunableMap]]. If the [[Tunable]] is not
   * of type `T` or a subclass of type `T`, throws a [[ClassCastException]]
   */
  def apply[T](key: TunableMap.Key[T]): Tunable[T]

  /**
   * Returns an Iterator over [[TunableMap.Entry]] for each [[Tunable]] in the map with a value.
   */
  private[tunable] def entries: Iterator[TunableMap.Entry[_]]

  /**
   * Compose this [[TunableMap]] with another [[TunableMap]]. [[Tunables]] retrieved from
   * the composed map prioritize the values of [[Tunables]] in the this map over the other
   * [[TunableMap]].
   */
  private[tunable] def orElse(that: TunableMap): TunableMap = new TunableMap {
    def apply[T](key: TunableMap.Key[T]): Tunable[T] =
      self(key).orElse(that(key))

    def entries: Iterator[TunableMap.Entry[_]] = {
      val dedupedTunables = mutable.Map.empty[String, TunableMap.Entry[_]]
      that.entries.foreach { case entry@TunableMap.Entry(key, _) =>
        dedupedTunables.put(key.id, entry)
      }

      // entries in `self` take precedence.
      self.entries.foreach { case entry@TunableMap.Entry(key, _) =>
        dedupedTunables.put(key.id, entry)
      }
      dedupedTunables.valuesIterator
    }
  }

}

private[twitter] object TunableMap {

  private case class TypeAndTunable[T](tunableType: Class[T], tunable: Tunable.Mutable[T])

  private[tunable] case class Entry[T](key: TunableMap.Key[T], value: T)

  /**
   * Class used to retrieve a [[Tunable]] with id `id` of type `T`.
   */
  final case class Key[T](id: String, clazz: Class[T])

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
      val currEntries = entries.toSeq
      val newEntries = newMap.entries.toSeq

      // clear keys no longer present
      (currEntries.map(_.key).toSet -- newEntries.map(_.key).toSet).foreach { key =>
        clear(key)
      }

      // add/update new tunable values
      newEntries.foreach { case TunableMap.Entry(key, value) =>
        put(key.id, key.clazz, value)
      }
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
   * Create a thread-safe map of [[Tunable.Mutable]]s.
   */
  def newMutable(): Mutable = new Mutable {

    // We use a ConcurrentHashMap to synchronize operations on non-thread-safe [[Tunable.Mutable]]s.
    private[this] val tunables = new ConcurrentHashMap[String, TypeAndTunable[_]]()

    private[this] def clearTunable[T](key: Key[T]): BiFunction[String, TypeAndTunable[_], TypeAndTunable[_]] =
      new BiFunction[String, TypeAndTunable[_], TypeAndTunable[_]] {
        def apply(id: String, curr: TypeAndTunable[_]): TypeAndTunable[_] = {
          if (curr != null) {
            curr.tunable.clear()
          }
          curr
        }
      }

    private[this] def getOrAdd[T](key: Key[T]): BiFunction[String, TypeAndTunable[_], TypeAndTunable[_]] =
      new BiFunction[String, TypeAndTunable[_], TypeAndTunable[_]] {
        def apply(id: String, curr: TypeAndTunable[_]): TypeAndTunable[_] =
          if (curr != null) {
            if (key.clazz.isAssignableFrom(curr.tunableType)) {
              curr
            } else {
              throw new ClassCastException(
                s"Tried to retrieve a Tunable of type ${key.clazz}, but TunableMap contained a " +
                s"Tunable of type ${curr.tunableType} for id ${key.id}")
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
                s"Tunable of type ${curr.tunableType} for id ${key.id}")
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

    private[tunable] def entries: Iterator[TunableMap.Entry[_]] = {
      val entryOpts: Iterator[Option[TunableMap.Entry[_]]] = tunables.iterator.map {
        case (id, TypeAndTunable(tunableType, tunable)) => tunable().map { value =>
          TunableMap.Entry(TunableMap.Key(id, tunableType), value)
        }
      }
      entryOpts.flatten
    }
  }
}

/**
 * A [[TunableMap]] that returns a [[Tunable.none]] for every [[TunableMap.Key]]
 */
private[tunable] object NullTunableMap extends TunableMap {

  def apply[T](key: TunableMap.Key[T]): Tunable[T] =
    Tunable.none[T]

  def entries: Iterator[TunableMap.Entry[_]] = Iterator.empty
}
