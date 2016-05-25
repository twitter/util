package com.twitter.util

import scala.util.control.{NonFatal => ScalaNonFatal}

/**
 * A classifier of non-fatal Exceptions.
 *
 * Developers should prefer using `scala.util.control.NonFatal` from the
 * Scala standard library.
 *
 * @see Scala's [[http://www.scala-lang.org/api/current/#scala.util.control.NonFatal$ NonFatal]]
 *      for usage notes.
 *
 * @note Scala added `NonFatal` to the standard library in Scala 2.10, while
 *       Twitter's util needed to provide this for users who were still on
 *       Scala 2.9 at the time.
 */
object NonFatal {

  /**
   * Determines whether `t` is a non-fatal Exception.
   *
   * @return true when `t` is '''not''' a fatal Exception.
   *
   * @note This is identical in behavior to `scala.util.control.NonFatal.apply`.
   */
  def isNonFatal(t: Throwable): Boolean =
    apply(t)

  /**
   * Determines whether `t` is a non-fatal Exception.
   *
   * @return true when `t` is '''not''' a fatal Exception.
   *
   * @note This is identical in behavior to `scala.util.control.NonFatal.apply`.
   */
  def apply(t: Throwable): Boolean =
    ScalaNonFatal(t)

  /**
   * A deconstructor to be used in pattern matches, allowing use in Exception
   * handlers.
   *
   * {{{
   * try dangerousOperation() catch {
   *   case NonFatal(e) => log.error("Chillax")
   *   case e => log.error("Freak out")
   * }
   * }}}
   *
   * @note This is identical in behavior to `scala.util.control.NonFatal.unapply`.
   */
  def unapply(t: Throwable): Option[Throwable] =
    ScalaNonFatal.unapply(t)
}
