package com.twitter.concurrent

import com.twitter.util._
import collection.mutable.ArrayBuffer

/**
 * A Channel is a unidirectional, read-only communication object. It
 * represents a stream of messages that can be subscribed to. There
 * are any number of subscribers and thus it is also pub-sub hub.
 *
 * Channel[+A] is a trait. The most common concrete implementation is a
 * ChannelSource. A ChannelSource is both readable and writeable.
 * A typical use case is for a producer to construct a ChannelSource
 * and give it to a consumer, upcasted to a Channel. This is analogous
 * to the Future/Promise relationship.
 */
trait Channel[+A] {
  /**
   * Subscribe to messages on this channel. If the channel is closed,
   * this method still returns an Observer. This is a trade-off to
   * avoid excessive lock-contention. Listen for close events if this
   * affects your use case.
   *
   * ''Note'': all subclasses of Channel *should* ensure ordered,
   * single-threaded delivery of messages. This means that the callback
   * param k should NEVER be invoked by two threads at once. This also
   * means that k can lead to lock contention or queue delays depending
   * on implementation, so consider k to be a critical section and perform
   * any long-running computation in another thread. See also `send`
   * in ChannelSource[_] for a concrete implementation of this concurency
   * requirement.
   *
   * @param reference because publish-subscribe mechanism can easily
   * cause memory leaks, the reference object is used in a Map with
   * weak keys.
   * @param k the function invoked when a message is delivered. Returns
   * A Future[Unit] indicating that the message has been fully processed.
   * This allows observers to process messages asynchronously and exhibit
   * backpressure to the producer.
   * @return an observer object representing the subscription.
   */
  def respond(reference: AnyRef)(k: A => Future[Unit]): Observer

  /**
   * Combine two Channels together to produce a new Channel with
   * messages interleaved.
   */
  def merge[B >: A](that: Channel[B]): Channel[B] = {
    val source = new ChannelSource[B]
    this.respond(source) { a =>
      Future.join(source.send(a))
    }
    that.respond(source) { a =>
      Future.join(source.send(a))
    }
    (this.closes join that.closes) ensure source.close()
    source
  }

  /**
   * Pipe the output of this channel to another Channel.
   */
  def pipe[B >: A](source: ChannelSource[B]) = {
    val observer = respond(source) { a =>
      Future.join(source.send(a))
    }
    observer
  }

  /**
   * The typical Scala collect method: a combination of map and
   * filter.
   */
  def collect[B](f: PartialFunction[A, B]): Channel[B] = {
    val source = new ChannelSource[B]
    respond(source) { a =>
      if (f.isDefinedAt(a)) Future.join(source.send(f(a)))
      else Future.Unit
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
      // Idempotent updateIfEmpty avoids a race where more than one message
      // is sent before we register the dispose() callback on
      // the promise (1)
      Future { promise.updateIfEmpty(Return(a)) }
    }
    // (1) register dispose() as soon as the promise is satisfied
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
  private[this] val observers = MapMaker[ObserverSource[A], Any](_.weakValues)

  // private as read-write.
  // note that lazy-vals are volatile and thus publish the XisDefined booleans.
  private[this] var respondsIsDefined = false
  private[this] lazy val _responds = {
    respondsIsDefined = true
    new ChannelSource[Observer]
  }

  private[this] var disposesIsDefined = false
  private[this] lazy val _disposes = {
    disposesIsDefined = true
    new ChannelSource[Observer]
  }

  private[this] var numObserversIsDefined = false
  private[this] lazy val _numObservers = {
    numObserversIsDefined = true
    new ChannelSource[Int]
  }

  private[this] val _closes   = new Promise[Unit]

  // public as read-only
  /**
   * A Channel of receive events. When a receiver is added to the Channel,
   * a message is sent.
   */
  def responds: Channel[Observer] = _responds

  /**
   * A Channel of subscriber dispose-events. When a subscriber unsubscribes,
   * a message is sent.
   */
  def disposes: Channel[Observer] = _disposes

  /**
   * A Channel that emits an Int representing the current number of subscribers.
   */
  def numObservers: Channel[Int] = _numObservers

  val closes:   Future[Unit]      = _closes

  def isOpen = open

  /**
   * Send a message to all observers. Returns a Seq[Future[Observer]]
   * that indicates completion of delivery for each observer.
   *
   * ''Note'': Delivery is serialized, meaning messages are always delivered
   * by one thread at a time. This ensures that messages arrive in-order,
   * and context-switching cannot interleave deliveries.
   */
  def send(a: A): Seq[Future[Observer]] = {
    assertOpen()

    /**
     * Create a snapshot of the observers in case it is modified during
     * delivery.
     */
    val observersCopy = new ArrayBuffer[ObserverSource[A]]
    observers.keys.copyToBuffer(observersCopy)

    val result = observersCopy map { _ =>
      new Promise[Observer]
    }

    serialized {
      observersCopy.zip(result) map { case (observer, promise) =>
        observer(a) map { _ => observer} proxyTo promise
      }
    }

    result
  }

  def close() {
    serialized {
      if (open) {
        open = false
        _closes.setValue(())
        observers.clear()
        if (respondsIsDefined)     _responds.close()
        if (disposesIsDefined)     _disposes.close()
        if (numObserversIsDefined) _numObservers.close()
      }
    }
  }

  /**
   * Listen for messages on the channel.
   */
  def respond(reference: AnyRef)(listener: A => Future[Unit]): Observer = {
    val observer = new ConcreteObserver(listener)
    serialized {
      if (open) {
        observers += observer -> reference
        _numObservers.send(observers.size)
        _responds.send(observer)
      }
    }
    observer
  }

  private[this] def assertOpen() {
    if (!open) throw new IllegalStateException("Channel is closed")
  }

  private class ConcreteObserver(listener: A => Future[Unit]) extends ObserverSource[A] with Serialized {
    def apply(a: A) = { listener(a) }

    def dispose() {
      observers.remove(this)
      _numObservers.send(observers.size)
      _disposes.send(this)
    }
  }
}

/**
 * An object representing the lifecycle of subscribing to a Channel.
 * This object can be used to unsubscribe.
 */
trait Observer {
  /**
   * Indicates that the Observer is no longer interested in receiving
   * messages.
   */
  def dispose()
}

trait ObserverSource[A] extends Observer {
  def apply(a: A): Future[Unit]
}