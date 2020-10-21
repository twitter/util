package com.twitter.concurrent

import com.twitter.util.{Future, Promise, Return, Throw, Try}
import java.util.ArrayDeque
import scala.collection.immutable.Queue

object AsyncQueue {

  private sealed trait State

  // In the `OfferingOrIdle` state we can have 0 or more chunks of data ready for
  // consumption. If we have 0 data chunks and the queue is `poll`ed we can transition
  // to the `Polling` state.
  private case object OfferingOrIdle extends State

  // If we're in a `Polling` state we must have at least one polling `Promise`.
  private case object Polling extends State

  // In the `Excepting` state the queue will only contain offers.
  private case class Excepting(exc: Throwable) extends State

  /** Indicates there is no max capacity */
  private val UnboundedCapacity = Int.MaxValue
}

/**
 * An asynchronous FIFO queue. In addition to providing [[offer]]
 * and [[poll]], the queue can be failed, flushing current pollers.
 *
 * @param maxPendingOffers optional limit on the number of pending `offers`.
 * The default is unbounded, but any other positive value can be used to limit
 * the max queue size. Note that `Int.MaxValue` is used to denote unbounded.
 *
 * @note thread safety is enforced via the intrinsic lock on `this` which must
 *       be acquired for any subclasses which want to serialize operations.
 */
class AsyncQueue[T](maxPendingOffers: Int) {
  import AsyncQueue._

  require(maxPendingOffers > 0)

  // synchronize all access to `state` and `queue`
  private[this] var state: State = OfferingOrIdle

  private[this] val queue = new ArrayDeque[Any]

  /**
   * An asynchronous, unbounded, FIFO queue. In addition to providing [[offer]]
   * and [[poll]], the queue can be failed, flushing current pollers.
   */
  def this() = this(AsyncQueue.UnboundedCapacity)

  /**
   * Returns the current number of pending elements.
   */
  final def size: Int = synchronized {
    state match {
      case OfferingOrIdle | Excepting(_) => queue.size
      case Polling => 0 // If we're `Polling` we're empty
    }
  }

  /**
   * Retrieves and removes the head of the queue, completing the
   * returned future when the element is available.
   */
  final def poll(): Future[T] = synchronized {
    state match {
      case OfferingOrIdle if queue.isEmpty =>
        val p = new Promise[T]
        state = Polling
        queue.offer(p)
        p

      case OfferingOrIdle =>
        val elem = queue.poll()
        Future.value(elem.asInstanceOf[T])

      case Polling =>
        val p = new Promise[T]
        queue.offer(p)
        p

      case Excepting(t) if queue.isEmpty =>
        Future.exception(t)

      case Excepting(_) =>
        Future.value(queue.poll().asInstanceOf[T])
    }
  }

  /**
   * Insert the given element at the tail of the queue.
   *
   * @return `true` if the item was successfully added, `false` otherwise.
   */
  def offer(elem: T): Boolean = {
    var waiter: Promise[T] = null
    val result = synchronized {
      state match {
        case OfferingOrIdle if queue.size >= maxPendingOffers =>
          false

        case OfferingOrIdle =>
          queue.offer(elem)
          true

        case Polling =>
          waiter = queue.poll().asInstanceOf[Promise[T]]
          if (queue.isEmpty)
            state = OfferingOrIdle
          true

        case Excepting(_) =>
          false // Drop.
      }
    }
    // we do this to avoid satisfaction while synchronized, which could lead to
    // deadlock if there are interleaved queue operations in the waiter closure.
    if (waiter != null)
      waiter.setValue(elem)
    result
  }

  /**
   * Drains any pending elements into a `Try[Queue]`.
   *
   * If the queue has been failed and is now empty,
   * a `Throw` of the exception used to fail will be returned.
   * Otherwise, return a `Return(Queue)` of the pending elements.
   */
  final def drain(): Try[Queue[T]] = synchronized {
    state match {
      case OfferingOrIdle =>
        var q = Queue.empty[T]
        while (!queue.isEmpty) {
          q :+= queue.poll().asInstanceOf[T]
        }
        Return(q)

      case Excepting(_) if !queue.isEmpty =>
        var q = Queue.empty[T]
        while (!queue.isEmpty) {
          q :+= queue.poll().asInstanceOf[T]
        }
        Return(q)

      case Excepting(e) =>
        Throw(e)

      case Polling =>
        Return(Queue.empty)
    }
  }

  /**
   * Fail the queue: current and subsequent pollers will be completed
   * with the given exception; any outstanding messages are discarded.
   */
  final def fail(exc: Throwable): Unit = fail(exc, discard = true)

  /**
   * Fail the queue. When `discard` is true, the queue contents is discarded
   * and all pollers are failed immediately. When this flag is false, subsequent
   * pollers are not failed until the queue becomes empty.
   *
   * No new elements are admitted to the queue after it has been failed.
   */
  def fail(exc: Throwable, discard: Boolean): Unit = {
    // CAUTION: `q` may be `null`
    val q: Array[AnyRef] = synchronized {
      state match {
        case Polling =>
          state = Excepting(exc)
          if (queue.isEmpty) null
          else {
            val data = queue.toArray
            queue.clear()
            data
          }

        case OfferingOrIdle =>
          if (discard)
            queue.clear()
          state = Excepting(exc)
          null

        case Excepting(_) => // Just take the first one.
          null
      }
    }
    // we do this to avoid satisfaction while synchronized, which could lead to
    // lock contention if closures on the promise are slow or there are a lot of
    // them
    if (q != null) {
      var i = 0
      while (i < q.length) {
        q(i).asInstanceOf[Promise[_]].setException(exc)
        i += 1
      }
    }
  }

  override def toString: String = s"AsyncQueue<${synchronized(state)}>"
}
