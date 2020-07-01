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
   * Notifies [[Observer observers]] of [[com.twitter.app.lifecycle.Event events]]
   */
  protected final def observe(event: Event)(f: => Unit): Unit =
    notifier(event)(f)

  /**
   * Notifies [[Observer observers]] of [[Future]] [[com.twitter.app.lifecycle.Event events]]
   */
  protected final def observeFuture(event: Event)(f: Future[Unit]): Future[Unit] =
    notifier.future(event)(f)

  /** Add a lifecycle [[com.twitter.app.lifecycle.Event]] [[Observer]] */
  private[twitter] final def withObserver(observer: Observer): Unit =
    observers.add(observer)
}
