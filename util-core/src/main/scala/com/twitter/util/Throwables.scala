package com.twitter.util

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.collection.Seq

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
    @tailrec def rec(ex: Throwable, buf: ArrayBuffer[String]): Seq[String] = {
      if (ex eq null)
        buf.result
      else
        rec(ex.getCause, buf += ex.getClass.getName)
    }

    rec(ex, ArrayBuffer.empty)
  }

  object RootCause {
    def unapply(e: Throwable): Option[Throwable] = Option(e.getCause)
  }
}
