package com.twitter.concurrent

import com.twitter.util._
import scala.collection.mutable.ArrayBuffer
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConversions._

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
trait Channel[+A] extends Serialized {
  /**
   * Subscribe to messages on this channel. If the channel is closed,
   * this method still returns an Observer. This is a trade-off to
   * avoid excessive lock-contention. Listen for close events if this
   * affects your use case.
   *
   * ''Note'': all subclasses of Channel *must* ensure ordered,
   * single-threaded delivery of messages. This means that the callback
   * param k should NEVER be invoked by two threads at once.
   *
   * Furthermore, respond() must publish all operations to shared variables
   * to any Threads that will later invoke param k. Thus, any shared
   * variables accessed only from within k needn't be synchronized or
   * annotated volatile.
   *
   * ''Note'': hard references may be kept to callbacks added to a channel
   * It is the callers responsibility to dispose() of the Observer to
   * prevent memory leaks. Consider adding finalizers to classes that
   * create Observers, and dispose() all observers.
   *
   * @param k A function returning Future[Unit] indicating that the message
   * has been fully processed.
   * @return an observer object representing the subscription.
   */
  def respond(k: A => Future[Unit]): Observer

  /**
   * Combine two Channels together to produce a new Channel with
   * messages interleaved.
   */
  def merge[B >: A](that: Channel[B]): Channel[B] = {
    val source = new ChannelSource[B]
    this.serialized {
      that.serialized {
        this.respond { a =>
          Future.join(source.send(a))
        }
        that.respond { b =>
          Future.join(source.send(b))
        }
        (this.closes join that.closes) ensure source.close()
      }
    }
    source
  }

  /**
   * Pipe the output of this channel to another Channel. If either Channel
   * closes midway through, stop operations.
   */
  def pipe[B >: A](to: ChannelSource[B]) = {
    val from = this
    var observer: Observer = null
    from.serialized {
      to.serialized {
        observer = from.respond { a =>
          Future.join(to.send(a))
        }
        to.closes.respond { _ =>
          observer.dispose()
        }
      }
    }

    observer
  }

  /**
   * The typical Scala collect method: a combination of map and
   * filter.
   */
  def collect[B](f: PartialFunction[A, B]): Channel[B] = {
    val source = new ChannelSource[B]
    serialized {
      respond { a =>
        if (f.isDefinedAt(a)) Future.join(source.send(f(a)))
        else Future.Unit
      }
      closes.foreach { _ =>
        source.close()
      }
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
   * Produce a new channel, keeping elements only where the predicate
   * obtains.
   */
  def filter(p: A => Boolean): Channel[A] = collect {
    case a if p(a) => a
  }

  /**
   * Get a future for the first message to arrive on this Channel,
   * from this point on.
   */
  def first: Future[A] = {
    val promise = new Promise[A]
    serialized {
      val observer = respond { a =>
        Future { promise.setValue(a) }
      }
      promise foreach { _ =>
        observer.dispose()
      }
      closes.foreach { _ =>
        promise.updateIfEmpty(Throw(new Exception("No element arrived")))
      }
    }
    promise
  }

  /**
   * A Future[Unit] indicating when the Channel closed. New observers can no
   * longer be added (calls to respond become no-ops), and no more messages
   * will be delivered.
   */
  val closes: Future[Unit]

  /**
   * Indicates whether the Channel is open.
   */
  def isOpen: Boolean

  /**
   * Ensures that events happen in order, one thread at a time. This prevents
   * interleaving of message delivery (respond) and lifecycle (close) events.
   *
   * Furthermore, it can be used to atomically perform several operations to a
   * Channel at once, without the side-effects of those operations being
   * triggered until the whole "batch" of operations complete. For example,
   * this can be used to attach several observers at once without the possibility
   * of one of the observers being invoked before the others were attached. If
   * two or more observers share mutable state, this would be used to eliminate
   * race-conditions leading to a sad data-integrity pickle, poor lad.
   */
  override def serialized[A](f: => A) = super.serialized(f)
}

/**
 * A concrete Channel implementation that is both readable and writable.
 * Typically a producer constructs a ChannelSource and upcasts it to
 * a Channel before giving to a consumer
 */
class ChannelSource[A] extends Channel[A] {
  @volatile private[this] var open = true

  // Behaving as a concurrent set with O(1) deletion.
  private[this] val _observers =
    new JConcurrentMapWrapper(new ConcurrentHashMap[ObserverSource[A], ObserverSource[A]])

  // private as read-write.
  // note that lazy-vals are @volatile and thus publish the XisDefined booleans.
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
   * This is often used to start/stop producing as Observers are added and removed.
   *
   *     channel.numObservers.respond {
   *       case 0 => stop()
   *       case 1 => start()
   *       case _ =>
   *     }
   */
  def numObservers: Channel[Int] = _numObservers

  /**
   * A Future[Unit] that indicates when the channel is closed.
   */
  val closes:   Future[Unit]      = _closes

  def isOpen = open

  /**
   * Send a message to all observers. Returns a Seq[Future[Observer]]
   * that indicates completion of delivery for each observer. The return
   * value is used to exhibit backpressure from consumers to producers.
   *
   * ''Note'': Delivery is serialized, meaning messages are always delivered
   * by one thread at a time. This ensures that messages arrive in-order,
   * and context-switching cannot interleave deliveries.
   */
  def send(a: A): Seq[Future[Observer]] = {
    /**
     * Create a snapshot of the observers in case it is modified during
     * delivery.
     */
    val observersCopy = new ArrayBuffer[ObserverSource[A]]
    _observers.keys.copyToBuffer(observersCopy)

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

  /**
   * Close the channel. Removes references to any observers and triggers
   * the closes events. New observers can no longer be added, and further
   * sends become no-ops.
   *
   * This method is serialized to ensure that close and send events do not
   * interleave.
   */
  def close() {
    serialized {
      if (open) {
        open = false
        _closes.setValue(())
        _observers.clear()
        if (respondsIsDefined)     _responds.close()
        if (disposesIsDefined)     _disposes.close()
        if (numObserversIsDefined) _numObservers.close()
      }
    }
  }

  def respond(listener: A => Future[Unit]): Observer = {
    val observer = new ConcreteObserver(listener)
    serialized {
      if (open) {
        _observers += observer -> observer
        _numObservers.send(_observers.size)
        _responds.send(observer)
      }
    }
    observer
  }

  private[this] class ConcreteObserver(listener: A => Future[Unit]) extends ObserverSource[A] {
    def apply(a: A) = { listener(a) }

    def dispose() {
      ChannelSource.this.serialized {
        _observers.remove(this)
        _numObservers.send(_observers.size)
        _disposes.send(this)
      }
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
