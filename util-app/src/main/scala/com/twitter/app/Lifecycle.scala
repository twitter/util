package com.twitter.app

import com.twitter.app.lifecycle.{Event, Notifier, Observer}
import com.twitter.util.Future
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._

/**
 * Mixin to an App that allows for observing lifecycle events.
 * To emit an event
 *   {{{ observe(PreMain) { // logic for premain } }}}
 */
private[app] trait Lifecycle { self: App =>
  private[this] val observers = new ConcurrentLinkedQueue[Observer]()
  private[this] val notifier = new Notifier(observers.asScala)

  /**
   * Notifies `Observer`s of `Event`s.
   */
  protected final def observe(event: Event)(f: => Unit): Unit =
    notifier(event)(f)

  /**
   * Notifies `Observer`s of [[com.twitter.util.Future]] `Event`s.
   */
  protected final def observeFuture(event: Event)(f: Future[Unit]): Future[Unit] =
    notifier.future(event)(f)

  /** Add a lifecycle [[com.twitter.app.lifecycle.Event]] [[com.twitter.app.lifecycle.Observer]] */
  private[twitter] final def withObserver(observer: Observer): Unit =
    observers.add(observer)
}
