package com.twitter.util

import java.util.logging.{Level, Logger}

/**
 * Wraps an exception that happens when handling another exception in
 * a monitor.
 */
case class MonitorException(
  handlingExc: Throwable,
  monitorExc: Throwable
) extends Exception(monitorExc) {
  override def getMessage =
    "threw exception \""+monitorExc+"\" while handling "+
    "another exception \""+handlingExc+"\""
}

/**
 * A Monitor is a composable exception handler.  It is independent of
 * position, divorced from the notion of a call stack.  Monitors do
 * not recover values from a failed computations: It handles only true
 * exceptions that may require cleanup.
 */
trait Monitor { self =>
  /**
   * Attempt to handle the exception {{exc}}.
   *
   * @return whether the exception was handled by this Monitor
   */
  def handle(exc: Throwable): Boolean

  /**
   * Run {{f}} inside of the monitor context. If {{f}} throws
   * an exception - directly or not - it is handled by this
   * monitor.
   */
  def apply(f: => Unit) = Monitor.using(this) {
    try f catch { case exc: Throwable => if (!handle(exc)) throw exc }
  }

  /**
   * A new monitor which, if {{this}} fails to handle the exception,
   * attempts to let {{next}} handle it.
   */
  def orElse(next: Monitor) = new Monitor {
    def handle(exc: Throwable) = {
      self.tryHandle(exc) rescue { case exc1 =>
        next.tryHandle(exc1)
      } isReturn
    }
  }

  /**
   * A new monitor which first handles the exception with {{this}},
   * then passes it onto {{next}} unconditionally. The new monitor
   * handles the exception if either {{this}} or {{next}} does.
   */
  def andThen(next: Monitor) = new Monitor {
    def handle(exc: Throwable) =
      self.tryHandle(exc) match {
        case Return.Unit =>
          next.tryHandle(exc)
          true
        case Throw(exc1) =>
          next.tryHandle(exc1).isReturn
      }
  }

  /**
   * An implementation widget: attempts to handle {{exc}} returning a
   * {{com.twitter.util.Try[Unit]}}.  If the exception is unhandled,
   * we return {{Throw(exc)}}, if the handler throws an exception, we
   * wrap it in a {{MonitorException}}.
   */
  protected def tryHandle(exc: Throwable): Try[Unit] =
    Try { self.handle(exc) } rescue {
      case monitorExc => Throw(MonitorException(exc, monitorExc))
    } flatMap { ok =>
      if (ok) Return.Unit
      else Throw(exc): Try[Unit]
    }
}

/**
 * Defines the (Future)-{{Local}} monitor as well as some monitor
 * utilities.
 */
object Monitor extends Monitor {
  private[this] val local = new Local[Monitor]

  /** Get the current {{Local}} monitor */
  def get = local() getOrElse NullMonitor
  /** Set the {{Locall}} monitor */
  def set(m: Monitor) {
    require(m ne this, "Cannot set the monitor to the global Monitor")
    local() = m
  }

  /** Compute {{f}} with the {{Local}} monitor set to {{m}} */
  @inline
  def using[T](m: Monitor)(f: => T): T = restoring {
    set(m)
    f
  }

  /** Restore the {{Local}} monitor after running computation {{f}} */
  @inline
  def restoring[T](f: => T): T = {
    val saved = local()
    try f finally local.set(saved)
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
   * Run the computation {{f}} in the context of the current {{Local}}
   * monitor.
   */
  override def apply(f: => Unit) =
    try f catch catcher

  /**
   * Handle {{exc}} with the current {{Local}} monitor.  If the
   * {{Local}} monitor fails to handle the exception, it is handled by
   * the {{RootMonitor}}.
   */
  def handle(exc: Throwable): Boolean =
    (get orElse RootMonitor).handle(exc)

  /**
   * Create a new monitor from a partial function.
   */
  def mk(f: PartialFunction[Throwable, Boolean]) = new Monitor {
    def handle(exc: Throwable) =
      if (f.isDefinedAt(exc)) f(exc)
      else false
  }
}

/**
 * A monitor that always fails to handle an exception. Combining this
 * with any other Monitor will simply return the other Monitor effectively
 * removing NullMonitor from the chain.
 */
object NullMonitor extends Monitor {
  def handle(exc: Throwable) = false
  override def orElse(next: Monitor) = next
  override def andThen(next: Monitor) = next
}

object RootMonitor extends Monitor {
  private[this] val log = Logger.getLogger("monitor")

  def handle(exc: Throwable) = exc match {
    case NonFatal(e) =>
      log.log(Level.SEVERE, "Exception propagated to the root monitor!", e)
      true /* Never propagate non fatal exception */

    case e: VirtualMachineError =>
      log.log(Level.SEVERE, "VM error", e)
      System.err.println("VM error: %s".format(e.getMessage))
      e.printStackTrace(System.err)
      System.exit(1)
      true  /*NOTREACHED*/

    case e: Throwable =>
      log.log(Level.SEVERE, "Fatal exception propagated to the root monitor!", e)
      false
  }
}
