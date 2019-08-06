package com.twitter.util

import scala.annotation.tailrec

object Throwables {

  /**
   * Useful for Java developers calling an API that has thrown
   * `Exceptions` declared and there is a need to treat these
   * as an unchecked `Exception`.
   *
   * For example:
   * {{{
   * try {
   *   return Await.result(aFuture);
   * } catch (Exception e) {
   *   return Throwables.unchecked(e);
   * }
   * }}}
   */
  def unchecked[Z](t: Throwable): Z =
    throw t

  /**
   * Traverse a nested `Throwable`, flattening all causes into a Seq of
   * classname strings.
   */
  def mkString(ex: Throwable): Seq[String] = {
    @tailrec def rec(ex: Throwable, buf: List[String]): Seq[String] = {
      if (ex eq null)
        buf.reverse
      else
        rec(ex.getCause, ex.getClass.getName :: buf)
    }

    rec(ex, Nil)
  }

  object RootCause {
    def unapply(e: Throwable): Option[Throwable] = Option(e.getCause)
  }
}
