package com.twitter.util.tunable

import com.twitter.util.Var

/**
 * A [[Tunable]] is an abstraction for an object that produces a Some(value) or None when applied.
 * Implementations may enable mutation, such that successive applications of the [[Tunable]]
 * produce different values.
 *
 * For more information about Tunables, see
 * [[https://twitter.github.io/finagle/guide/Configuration.html#tunables]]
 *
 * @param id  id of this [[Tunable]], used in `toString`. Must not be empty and should be unique.
 * @tparam T  type of value this [[Tunable]] holds
 */
sealed abstract class Tunable[T](val id: String) { self =>

  /**
   * Returns a [[Var]] that observes this [[Tunable]].  Observers of the [[Var]] will be notified
   * whenever the value of this [[Tunable]] is updated.
   */
  def asVar: Var[Option[T]]

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

  /**
   * Compose this [[Tunable]] with another [[Tunable]]. Application
   * of the returned [[Tunable]] will return the result of applying this [[Tunable]],
   * if it is defined and the result of applying the other [[Tunable]] if not.
   *
   * @note the returned [[Tunable]] will have the `id` of this [[Tunable]]
   */
  def orElse(that: Tunable[T]): Tunable[T] =
    new Tunable[T](id) {

      def asVar: Var[Option[T]] = self.asVar.flatMap {
        case Some(_) => self.asVar
        case None => that.asVar
      }

      override def toString: String =
        s"${self.toString}.orElse(${that.toString})"

      def apply(): Option[T] =
        self().orElse(that())
    }

  /**
   * Returns a [[Tunable]] containing the result of applying f to this [[Tunable]] value.
   *
   * @note the returned [[Tunable]] will have the `id` of this [[Tunable]]
   */
  def map[A](f: T => A): Tunable[A] =
    new Tunable[A](id) {

      def asVar: Var[Option[A]] = self.asVar.map(_.map(f))

      def apply(): Option[A] = self().map(f)
    }
}

object Tunable {

  /**
   * A [[Tunable]] that always returns `value` when applied.
   */
  final class Const[T](id: String, value: T) extends Tunable[T](id) {
    private val holder = Var.value(Some(value))

    def apply(): Option[T] = holder()

    def asVar: Var[Option[T]] = holder
  }

  object Const {
    def unapply[T](tunable: Tunable[T]): Option[T] = tunable match {
      case tunable: Tunable.Const[T] => tunable.holder()
      case _ => None
    }
  }

  /**
   * Create a new [[Tunable.Const]] with id `id` and value `value`.
   */
  def const[T](id: String, value: T): Const[T] = new Const(id, value)

  private[this] val NoneTunable = new Tunable[Any]("com.twitter.util.tunable.NoneTunable") {

    def apply(): Option[Any] =
      None

    val asVar: Var[Option[Any]] = Var.value(None)
  }

  /**
   * Returns a [[Tunable]] that always returns `None` when applied.
   */
  def none[T]: Tunable[T] =
    NoneTunable.asInstanceOf[Tunable[T]]

  /**
   * A [[Tunable]] whose value can be changed. Operations are thread-safe.
   */
  final class Mutable[T] private[tunable] (id: String, _value: Option[T]) extends Tunable[T](id) {

    private[this] final val mutableHolder = Var(_value)

    def asVar: Var[Option[T]] = mutableHolder

    /**
     * Set the value of the [[Tunable.Mutable]].
     *
     * Note that setting the value to `null` will result in a value of Some(null) when the
     * [[Tunable]] is applied.
     */
    def set(value: T): Unit = mutableHolder.update(Some(value))

    /**
     * Clear the value of the [[Tunable.Mutable]]. Calling `apply` on the [[Tunable.Mutable]]
     * will produce `None`.
     */
    def clear(): Unit = mutableHolder.update(None)

    /**
     * Get the current value of the [[Tunable.Mutable]]
     */
    def apply(): Option[T] = mutableHolder()
  }

  /**
   * Create a new [[Tunable.Mutable]] with id `id` and value initial value `initialValue`.
   */
  def mutable[T](id: String, initialValue: T): Mutable[T] =
    new Mutable(id, Some(initialValue))

  /**
   * Create a [[Tunable.Mutable]] without an initial value
   */
  def emptyMutable[T](id: String): Mutable[T] =
    new Mutable(id, None)
}
