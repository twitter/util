package com.twitter.concurrent

import com.twitter.util._
import collection.mutable.ArrayBuffer

/**
 * A Channel is a unidirectional, read-only communication medium. It
 * represents a stream of messages that can be subscribed to. There
 * are any number of subscribers and thus it is also pub-sub hub.
 *
 * This is a trait. The most common concrete implementation is a
 * ChannelSource. A ChannelSource is both readable and writeable.
 * A typical use case is for a producer to construct a ChannelSource
 * and give it to a consumer, upcasted to a Channel.
 */
trait Channel[+A] {
  /**
   * Subscribe to messages on this channel. If the channel is closed,
   * this method still returns an Observer. This is a trade-off to
   * avoid excessive lock-contention. Listen for close events if this
   * affects your use case.
   *
   * @param reference because publish-subscribe mechanism can easily
   * cause memory leaks, the reference object is used in a Map with
   * weak keys.
   * @return an observer object representing the subscription.
   */
  def respond(reference: AnyRef)(k: A => Unit): Observer

  /**
   * Combine two Channels together to produce a new Channel with
   * messages interleaved.
   */
  def merge[B >: A](that: Channel[B]): Channel[B] = {
    val source = new ChannelSource[B]
    this.respond(source) { a =>
      source.send(a)
    }
    that.respond(source) { a =>
      source.send(a)
    }
    for {
      _ <- this.closes
      _ <- that.closes
    } {
      source.close()
    }
    source
  }

  /**
   * Pipe the output of this channel to another Channel.
   */
  def pipe[B >: A](source: ChannelSource[B]) {
    respond(source) { a =>
      source.send(a)
    }
  }

  /**
   * The typical Scala collect method: a combination of map and
   * filter.
   */
  def collect[B](f: PartialFunction[A, B]): Channel[B] = {
    val source = new ChannelSource[B]
    respond(source) { a =>
      if (f.isDefinedAt(a)) source.send(f(a))
    }
    this.closes.foreach { _ =>
      source.close()
    }
    source
  }

  /**
   * Produce a new channel with the function applied to all messages
   * in this channel.
   */
  def map[B](f: (A => B)): Channel[B] = collect {
    case a => f(a)
  }

  /**
   * Produce a new channel, eliminating elements where the predicate
   * obtains.
   */
  def filter(p: A => Boolean): Channel[A] = collect {
    case a if !p(a) => a
  }

  /**
   * Get a future for the first message to arrive on this Channel,
   * from this point on.
   */
  def first: Future[A] = {
    val promise = new Promise[A]
    val observer = respond(promise) { a =>
      // avoid race condition with idempotent update and
      // reference the observer outside.
      promise.updateIfEmpty(Return(a))
    }
    promise foreach { _ =>
      observer.dispose()
    }
    closes.foreach { _ =>
      promise.updateIfEmpty(Throw(new Exception("No element arrived")))
    }
    promise
  }

  /**
   * Close the channel.
   */
  def close()

  /**
   * A Future[Unit] indicating when the Channel closed
   */
  val closes: Future[Unit]

  /**
   * Indicates whether the Channel is open.
   */
  def isOpen: Boolean
}

/**
 * A concrete Channel implementation that is both readable and writable.
 * Typically a producer constructs a ChannelSource and upcasts it to
 * a Channel before giving to a consumer
 */
class ChannelSource[A] extends Channel[A] with Serialized {
  private[this] var open = true
  private[this] val subscribers = MapMaker[Any, ObserverSource[A]](_.weakKeys)

  // private as read-write
  private[this] lazy val _responds = new ChannelSource[Observer]
  private[this] lazy val _pauses   = new ChannelSource[Observer]
  private[this] lazy val _resumes  = new ChannelSource[Observer]
  private[this] lazy val _disposes = new ChannelSource[Observer]
  private[this]      val _closes   = new Promise[Unit]

  // public as read-only
  /**
   * A Channel of receive events. When a receiver is added to the Channel,
   * a message is sent.
   */
  lazy val responds: Channel[Observer] = _responds

  /**
   * A Channel of subscriber pause-events. When a subscriber pauses, a
   * message is sent. This is useful for backpressure policies.
   */
  lazy val pauses:   Channel[Observer] = _pauses

  /**
   * A Channel of subscriber resume-events. When a subscriber pauses, a
   * message is sent.
   */
  lazy val resumes:   Channel[Observer] = _resumes

  /**
   * A Channel of subscriber dispose-events. When a subscriber unsubscribes,
   * a message is sent.
   */
  lazy val disposes: Channel[Observer] = _disposes

  val closes:   Future[Unit]      = _closes

  def isOpen = open

  def send(a: A) {
    assertOpen()

    /**
     * create a snapshot of the observers in case it is modified during
     * delivery.
     */
    val values = new ArrayBuffer[ObserverSource[A]]
    subscribers.values.copyToBuffer(values)

    values.foreach { observer =>
      deliver(a, observer)
    }
  }

  /**
   * Override this method to implement backpressure strategies. The default
   * implementation ignores backpressure indications.
   */
  protected def deliver(a: A, f: ObserverSource[A]) {
    f(a)
  }

  def close() {
    serialized {
      if (open) {
        open = false
        subscribers.clear()
        _closes.setValue(())
        _responds.close()
        _pauses.close()
        _disposes.close()
      }
    }
  }

  def respond(reference: AnyRef)(listener: (A) => Unit): Observer = {
    val observer = new ConcreteObserver(reference, listener)
    serialized {
      if (open) {
        subscribers += reference -> observer
        _responds.send(observer)
      }
    }
    observer
  }

  private[this] def assertOpen() {
    if (!open) throw new IllegalStateException("Channel is closed")
  }

  class ConcreteObserver[A](reference: AnyRef, listener: A => Unit) extends ObserverSource[A] {
    def apply(a: A) { listener(a) }

    @volatile private[this] var _isPaused = false

    def isPaused = _isPaused

    def dispose() {
      subscribers.remove(reference)
    }

    def pause() {
      _isPaused = true
      _pauses.send(this)
    }

    def resume() {
      _isPaused = false
      _resumes.send(this)
    }
  }
}


/**
 * An object representing the lifecycle of subscribing to a Channel.
 * This object can be used to unsubscribe or exhibit backpressure.
 */
trait Observer {
  /**
   * Indicates that the Observer is no longer interested in receiving
   * messages.
   */
  def dispose()

  /**
   * Indicate that the Observer is overwhelmed. Exhibits backpressure.
   */
  def pause()

  /**
   * Indicate that the Observer is no longer overwhelmed. Moar.
   */
  def resume()
}

trait ObserverSource[A] extends Observer {
  /**
   * Indicates that the Observer is paused
   */
  def isPaused: Boolean

  def apply(a: A)
}