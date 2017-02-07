package com.twitter.util.tunable


/**
 * A [[Tunable]] is an abstraction for an object that produces a Some(value) or None when applied.
 * Implementations may enable mutation, such that successive applications of the [[Tunable]]
 * produce different values.
 *
 * @param id  id of this [[Tunable]], used in `toString`. Must not be empty and should be unique.
 * @tparam T  type of value this [[Tunable]] holds
 *
 * @note These APIs are still in flux and should NOT be used at this time.
 */
private[twitter] abstract class Tunable[T](val id: String) { self =>

  // validate id is not empty
  if (id.trim.isEmpty)
    throw new IllegalArgumentException("Tunable id must not be empty")

  /**
   * Returns the current value of this [[Tunable]] as an `Option`.
   * If the [[Tunable]] has a value, returns `Some(value)`. Otherwise, returns `None`.
   */
  def apply(): Option[T]

  override def toString: String =
    s"Tunable($id)"
}

private[twitter] object Tunable {

  /**
   * A [[Tunable]] that always returns `value` when applied.
   */
  class Const[T](id: String, private val value: T) extends Tunable[T](id) {
    private[this] val SomeValue = Some(value)

    def apply(): Option[T] =
      SomeValue
  }

  object Const {
    def unapply[T](tunable: Tunable[T]): Option[T] = tunable match {
      case tunable: Tunable.Const[T] => Some(tunable.value)
      case _ => None
    }
  }

  /**
   * Create a new [[Tunable.Const]] with id `id` and value `value`.
   */
  def const[T](id: String, value: T): Const[T] = new Const(id, value)
}
