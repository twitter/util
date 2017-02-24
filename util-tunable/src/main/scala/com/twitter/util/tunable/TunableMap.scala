package com.twitter.util.tunable

import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction

/**
 * A Map that can be used to access [[Tunable]]s using [[TunableMap.Key]]s.
 */
private[twitter] abstract class TunableMap {

  /**
   * Returns a [[Tunable]] for the given `key.id` in the [[TunableMap]]. If the [[Tunable]] is not
   * of type `T` or a subclass of type `T`, throws a [[ClassCastException]]
   */
  def apply[T](key: TunableMap.Key[T]): Tunable[T]

  /**
   * Returns the size of the TunableMap. Currently only used for testing.
   */
  private[tunable] def size: Int
}

private[twitter] object TunableMap {

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

    private[this] type TypeAndTunable = (Class[_], Tunable.Mutable[_])

    // We use a ConcurrentHashMap to synchronize operations on non-thread-safe [[Tunable.Mutable]]s.
    private[this] val tunables = new ConcurrentHashMap[String, TypeAndTunable]()

    private[this] def clearTunable[T](key: Key[T]): BiFunction[String, TypeAndTunable, TypeAndTunable] =
      new BiFunction[String, TypeAndTunable, TypeAndTunable] {
        def apply(id: String, curr: TypeAndTunable): TypeAndTunable = {
          if (curr != null) {
            curr._2.clear()
          }
          curr
        }
      }

    private[this] def getOrAdd[T](key: Key[T]): BiFunction[String, TypeAndTunable, TypeAndTunable] =
      new BiFunction[String, TypeAndTunable, TypeAndTunable] {
        def apply(id: String, curr: TypeAndTunable): TypeAndTunable =
          if (curr != null) {
            if (key.clazz.isAssignableFrom(curr._1)) {
              curr
            } else {
              throw new ClassCastException(
                s"Tried to retrieve a Tunable of type ${key.clazz}, but TunableMap contained a " +
                s"Tunable of type ${curr._1} for id ${key.id}")
            }
          } else {
            (key.clazz, Tunable.emptyMutable[T](key.id))
          }
      }

    private[this] def updateOrAdd[T](
      key: Key[T],
      value: T
    ): BiFunction[String, TypeAndTunable, TypeAndTunable] =
      new BiFunction[String, TypeAndTunable, TypeAndTunable] {
        def apply(id: String, curr: TypeAndTunable): TypeAndTunable =
          if (curr != null) {
            if (curr._1 == key.clazz) {
              curr._2.asInstanceOf[Tunable.Mutable[T]].set(value)
              curr
            } else {
              throw new ClassCastException(
                s"Tried to update a Tunable of type ${key.clazz}, but TunableMap contained a " +
                s"Tunable of type ${curr._1} for id ${key.id}")
            }
          } else {
            (key.clazz, Tunable.mutable[T](id, value))
          }
      }

    private[tunable] def size: Int = tunables.size

    def put[T](id: String, clazz: Class[T], value: T): Key[T] = {
      val key = Key(id, clazz)
      tunables.compute(id, updateOrAdd(key, value))
      key
    }

    def clear[T](key: Key[T]): Unit =
      tunables.computeIfPresent(key.id, clearTunable(key))

    def apply[T](key: Key[T]): Tunable[T] = {
      tunables.compute(key.id, getOrAdd[T](key))._2.asInstanceOf[Tunable.Mutable[T]]
    }
  }
}

/**
 * A [[TunableMap]] that returns a [[Tunable.none]] for every [[Tunable.Key]]
 */
private[tunable] object NullTunableMap extends TunableMap {

  def apply[T](key: TunableMap.Key[T]): Tunable[T] =
    Tunable.none[T]

  def size: Int = 0
}
