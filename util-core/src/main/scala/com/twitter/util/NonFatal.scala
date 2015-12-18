package com.twitter.util

import scala.util.control.ControlThrowable

/**
 * A classifier of fatal exceptions
 */
object NonFatal {
  /**
   * This is identical in behavior to the upcoming
   * [[scala.util.control.NonFatal]] (which appears in scala 2.10).
   */
  def isNonFatal(t: Throwable): Boolean = t match {
    // StackOverflowError ok even though it is a VirtualMachineError
    case _: StackOverflowError => true
    // VirtualMachineError includes OutOfMemoryError and other fatal errors
    case _: VirtualMachineError | _: ThreadDeath | _: InterruptedException |
      _: LinkageError | _: ControlThrowable /*scala 2.10 | _: NotImplementedError*/ => false
    case _ => true
  }

  /**
   * Determines whether `t` is a fatal exception.
   *
   * @return true when `t` is '''not''' a fatal exception.
   */
  def apply(t: Throwable): Boolean = t match {
    case _: NoSuchMethodException => false
    case t => isNonFatal(t)
  }

  /**
   * A deconstructor to be used in pattern matches, allowing use in exception
   * handlers.
   *
   * {{{
   * try dangerousOperation() catch {
   *   case NonFatal(e) => log.error("Chillax")
   *   case e => log.error("Freak out")
   * }
   * }}}
   */
  def unapply(t: Throwable): Option[Throwable] = if (apply(t)) Some(t) else None
}
