package com.twitter.app.lifecycle

import com.twitter.util.{Future, Return, Throw}

/**
 * The [[Notifier]] emits signals to [[Observer observers]] when lifecycle [[Event events]] are
 * executed.
 *
 * @param observers the observers who will be notified of lifecycle event activities
 * @note exposed for testing
 */
private[twitter] class Notifier private[app] (observers: Iterable[Observer]) {

  /**
   * Record and execute a lifecycle event and emit the result to any [[Observer observers]].
   *
   * @param event The [[Event lifecycle event]] to execute.
   * @param f The function to execute as part of this [[Event lifecycle event]].
   */
  final def apply(event: Event)(f: => Unit): Unit = execAndSignal(event, f)

  /**
   * Record and execute a lifecycle event [[Future]] and emit the result to
   * any [[Observer observers]] on completion of the [[Future]].
   *
   * @param event The [[Event lifecycle event]] to execute.
   * @param f The Future that will be responded to on completion for this
   *          [[Event lifecycle event]].
   */
  final def future(event: Event)(f: Future[Unit]): Future[Unit] = {
    onEntry(event)
    f.respond {
      case Return(_) =>
        onSuccess(event)
      case Throw(t) =>
        onFailure(event, t)
    }
  }

  private[this] def execAndSignal(event: Event, f: => Unit): Unit = {
    onEntry(event)
    try {
      f
      onSuccess(event)
    } catch {
      case t: Throwable =>
        onFailure(event, t)
        throw t
    }
  }

  // notify all observers we are entering the event
  private[this] def onEntry(stage: Event): Unit =
    observers.foreach(_.onEntry(stage))

  // notify all observers on success
  private[this] def onSuccess(stage: Event): Unit =
    observers.foreach(_.onSuccess(stage))

  // notify all observers on failures
  private[this] def onFailure(stage: Event, throwable: Throwable): Unit =
    observers.foreach(_.onFailure(stage, throwable))

}
