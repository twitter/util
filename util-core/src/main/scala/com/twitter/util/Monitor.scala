package com.twitter.util

import java.util.logging.{Level, Logger}
import scala.util.control

/**
 * Wraps an exception that happens when handling another exception in
 * a monitor.
 */
case class MonitorException(handlingExc: Throwable, monitorExc: Throwable)
    extends Exception(monitorExc) {
  override def getMessage: String =
    "threw exception \"" + monitorExc + "\" while handling " +
      "another exception \"" + handlingExc + "\""
}

/**
 * A Monitor is a composable exception handler.  It is independent of
 * position, divorced from the notion of a call stack.  Monitors do
 * not recover values from failed computations: It handles only true
 * exceptions that may require cleanup.
 *
 * @see [[AbstractMonitor]] for an API friendly to creating instances from Java.
 */
trait Monitor { self =>

  /**
   * Attempt to handle the exception `exc`.
   *
   * @return whether the exception was handled by this Monitor
   */
  def handle(exc: Throwable): Boolean

  private[this] val someSelf: Some[Monitor] = Some(self)

  /**
   * Run `f` inside of the monitor context. If `f` throws
   * an exception - directly or not - it is handled by this
   * monitor.
   */
  def apply(f: => Unit): Unit = {
    // Note: this inlines `Monitor.using` to avoid closure allocations.
    val saved = Monitor.getOption
    try {
      Monitor.setOption(someSelf)
      try f
      catch {
        case exc: Throwable =>
          if (!handle(exc)) throw exc
      }
    } finally Monitor.setOption(saved)
  }

  /**
   * A new monitor which, if `this` fails to handle the exception,
   * attempts to let `next` handle it.
   */
  def orElse(next: Monitor): Monitor = new Monitor {
    def handle(exc: Throwable): Boolean = {
      val tried = self.tryHandle(exc)
      if (tried.isReturn) true
      else next.tryHandle(tried.throwable).isReturn
    }
  }

  /**
   * A new monitor which first handles the exception with `this`,
   * then passes it onto `next` unconditionally. The new monitor
   * handles the exception if either `this` or `next` does.
   */
  def andThen(next: Monitor): Monitor = new Monitor {
    def handle(exc: Throwable): Boolean =
      self.tryHandle(exc) match {
        case Return(_) =>
          next.tryHandle(exc)
          true
        case Throw(exc1) =>
          next.tryHandle(exc1).isReturn
      }
  }

  /**
   * An implementation widget: attempts to handle `exc` returning a
   * `com.twitter.util.Try[Unit]`. If the exception is unhandled,
   * we return `Throw(exc)`, if the handler throws an exception, we
   * wrap it in a [[MonitorException]].
   */
  protected def tryHandle(exc: Throwable): Try[Unit] =
    try {
      if (self.handle(exc)) Return.Unit
      else Throw(exc)
    } catch {
      case monitorExc: Throwable => Throw(MonitorException(exc, monitorExc))
    }
}

/**
 * An API for creating [[Monitor]] instances in Java.
 */
abstract class AbstractMonitor extends Monitor

/**
 * Defines the (Future)-`Local` monitor as well as some monitor
 * utilities.
 *
 * Java users should use the `Monitors` class.
 */
object Monitor extends Monitor {
  private[this] val local = new Local[Monitor]

  /**
   * Get the current [[Local]] monitor or a [[NullMonitor]]
   * if none has been [[set]].
   *
   * @see [[getOption]]
   */
  def get: Monitor = local() match {
    case Some(m) => m
    case None => NullMonitor
  }

  /**
   * Get the current [[Local]] monitor or `None`
   * if none has been `set`.
   *
   * @see [[get]]
   */
  def getOption: Option[Monitor] = local()

  private[this] def validateSetMonitor(monitor: Monitor): Unit = {
    if (monitor eq this)
      throw new IllegalArgumentException("Cannot set the monitor to the global Monitor")
  }

  /**
   * Set the [[Local]] monitor.
   *
   * Note that the monitor may not be the `Monitor` companion object.
   *
   * @see [[getOption]]
   */
  def set(m: Monitor): Unit = {
    validateSetMonitor(m)
    local() = m
  }

  /**
   * Set the [[Local]] monitor.
   *
   * Note that the monitor may not be the `Monitor` companion object.
   *
   * If `None` is given, then the local is `clear`-ed.
   *
   * @see [[setOption]]
   */
  def setOption(monitor: Option[Monitor]): Unit =
    monitor match {
      case Some(m) =>
        validateSetMonitor(m)
        local.set(monitor)
      case None =>
        local.clear()
    }

  /** Compute `f` with the [[Local]] monitor set to `m` */
  @inline
  def using[T](m: Monitor)(f: => T): T = restoring {
    set(m)
    f
  }

  /** Restore the  [[Local]] monitor after running computation `f` */
  @inline
  def restoring[T](f: => T): T = {
    val saved = local()
    try f
    finally local.set(saved)
  }

  /**
   * An exception catcher that attempts to handle exceptions with
   * the current monitor.
   */
  val catcher: PartialFunction[Throwable, Unit] = {
    case exc =>
      if (!handle(exc))
        throw exc
  }

  /**
   * Run the computation `f` in the context of the current [[Local]]
   * monitor.
   */
  override def apply(f: => Unit): Unit =
    try f
    catch catcher

  /**
   * Handle `exc` with the current [[Local]] monitor. If the
   *  [[Local]] monitor fails to handle the exception, it is handled by
   * the [[RootMonitor]].
   */
  def handle(exc: Throwable): Boolean =
    get.orElse(RootMonitor).handle(exc)

  private[this] val AlwaysFalse = scala.Function.const(false) _

  /**
   * Create a new monitor from a partial function.
   */
  def mk(f: PartialFunction[Throwable, Boolean]): Monitor = new Monitor {
    def handle(exc: Throwable): Boolean = f.applyOrElse(exc, AlwaysFalse)
  }

  /**
   * Checks whether or not monitoring is activated, meaning that the
   * currently-set Monitor is non-null.
   *
   * @return true if currently-set Monitor is the [[NullMonitor]]. False otherwise.
   */
  def isActive: Boolean = get != NullMonitor
}

/**
 * A monitor that always fails to handle an exception. Combining this
 * with any other Monitor will simply return the other Monitor effectively
 * removing NullMonitor from the chain.
 */
object NullMonitor extends Monitor {
  def handle(exc: Throwable): Boolean = false
  override def orElse(next: Monitor): Monitor = next
  override def andThen(next: Monitor): Monitor = next

  def getInstance: Monitor = this

  override def toString: String = "NullMonitor"
}

object RootMonitor extends Monitor {
  private[this] val log = Logger.getLogger("monitor")
  private[this] val root = Monitor.mk {
    case control.NonFatal(e) =>
      log.log(Level.SEVERE, "Exception propagated to the root monitor!", e)
      true /* Never propagate non fatal exception */

    case e: VirtualMachineError =>
      log.log(Level.SEVERE, "VM error", e)
      System.err.println("VM error: %s".format(e.getMessage))
      e.printStackTrace(System.err)
      System.exit(1)
      true /*NOTREACHED*/

    case e: Throwable =>
      log.log(Level.SEVERE, "Fatal exception propagated to the root monitor!", e)
      false
  }

  @volatile private[this] var monitor: Monitor = root

  def handle(exc: Throwable): Boolean = monitor.handle(exc)

  /**
   * Set a custom monitor `m` as the root monitor. If `m` doesn't handle an exception,
   * it'll fallback to the original root monitor.
   */
  def set(m: Monitor): Unit = monitor = m.andThen(root)

  /**
   * Java-friendly API to get this instance
   */
  def getInstance: Monitor = this
}
