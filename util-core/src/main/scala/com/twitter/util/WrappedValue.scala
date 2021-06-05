package com.twitter.util

/**
 * [[WrappedValue]] is a marker interface intended for use by case classes wrapping a single value.
 * The intent is to signal that the underlying value should be directly serialized
 * or deserialized ignoring the wrapping case class.
 *
 * [[WrappedValue]] is a [[https://docs.scala-lang.org/overviews/core/value-classes.html Universal Trait]]
 * so that it can be used by [[https://docs.scala-lang.org/overviews/core/value-classes.html value classes]].
 *
 * ==Usage==
 * {{{
 *   case class WrapperA(underlying: Int) extends WrappedValue[Int]
 *   case class WrapperB(underlying: Int) extends AnyVal with WrappedValue[Int]
 * }}}
 */
trait WrappedValue[T] extends Any { self: Product =>

  def onlyValue: T = self.productElement(0).asInstanceOf[T]

  def asString: String = onlyValue.toString

  /**
   * @note we'd rather not override `toString`, but this is required to correctly handle
   *       [[WrappedValue]] instances used as Map keys.
   */
  override def toString: String = asString
}
