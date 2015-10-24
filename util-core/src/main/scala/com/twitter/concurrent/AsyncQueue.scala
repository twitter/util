package com.twitter.concurrent

import com.twitter.util._
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.collection.immutable.Queue

object AsyncQueue {
  private sealed trait State[+T]
  private case object Idle extends State[Nothing]
  private case class Offering[T](q: Queue[T]) extends State[T]
  private case class Polling[T](q: Queue[Promise[T]]) extends State[T]
  private case class Excepting[T](q: Queue[T], exc: Throwable) extends State[T]

  /** Indicates there is no max capacity */
  private val UnboundedCapacity = Int.MaxValue
}

/**
 * An asynchronous FIFO queue. In addition to providing [[offer]]
 * and [[poll]], the queue can be [[fail "failed"]], flushing current
 * pollers.
 *
 * @param maxPendingOffers optional limit on the number of pending `offers`.
 * The default is unbounded, but any other positive value can be used to limit
 * the max queue size. Note that `Int.MaxValue` is used to denote unbounded.
 */
class AsyncQueue[T](maxPendingOffers: Int) {
  import AsyncQueue._

  /**
   * An asynchronous, unbounded, FIFO queue. In addition to providing [[offer]]
   * and [[poll]], the queue can be [[fail "failed"]], flushing current
   * pollers.
   */
  def this() = this(AsyncQueue.UnboundedCapacity)

  require(maxPendingOffers > 0)

  private[this] val state = new AtomicReference[State[T]](Idle)

  /**
   * Returns the current number of pending elements.
   */
  def size: Int = state.get match {
    case Offering(q) => q.size
    case _ => 0
  }

  private[this] def queueOf[E](e: E): Queue[E] =
    Queue.empty.enqueue(e)

  private[this] def pollExcepting(s: Excepting[T]): Future[T] = {
    val q = s.q
    if (q.isEmpty) {
      Future.exception(s.exc)
    } else {
      val (elem, nextq) = q.dequeue
      val nextState = Excepting(nextq, s.exc)
      if (state.compareAndSet(s, nextState)) Future.value(elem) else poll()
    }
  }

  /**
   * Retrieves and removes the head of the queue, completing the
   * returned future when the element is available.
   */
  @tailrec
  final def poll(): Future[T] = state.get match {
    case Idle =>
      val p = new Promise[T]
      if (state.compareAndSet(Idle, Polling(queueOf(p)))) p else poll()

    case s@Polling(q) =>
      val p = new Promise[T]
      if (state.compareAndSet(s, Polling(q.enqueue(p)))) p else poll()

    case s@Offering(q) =>
      val (elem, nextq) = q.dequeue
      val nextState = if (nextq.nonEmpty) Offering(nextq) else Idle
      if (state.compareAndSet(s, nextState)) Future.value(elem) else poll()

    case s: Excepting[T] =>
      pollExcepting(s)
  }

  /**
   * Insert the given element at the tail of the queue.
   *
   * @return `true` if the item was successfully added, `false` otherwise.
   */
  @tailrec
  final def offer(elem: T): Boolean = state.get match {
    case Idle =>
      if (!state.compareAndSet(Idle, Offering(queueOf(elem))))
        offer(elem)
      else true

    case Offering(q) if q.size >= maxPendingOffers =>
      false

    case s@Offering(q) =>
      if (!state.compareAndSet(s, Offering(q.enqueue(elem))))
        offer(elem)
      else true

    case s@Polling(q) =>
      val (waiter, nextq) = q.dequeue
      val nextState = if (nextq.nonEmpty) Polling(nextq) else Idle
      if (state.compareAndSet(s, nextState)) {
        waiter.setValue(elem)
        true
      } else {
        offer(elem)
      }

    case Excepting(_, _) =>
      false // Drop.
  }

  /**
   * Drains any pending elements into a `Try[Queue]`.
   *
   * If the queue has been [[fail failed]] and is now empty,
   * a `Throw` of the exception used to fail will be returned.
   * Otherwise, return a `Return(Queue)` of the pending elements.
   */
  @tailrec
  final def drain(): Try[Queue[T]] = state.get match {
    case s@Offering(q) =>
      if (state.compareAndSet(s, Idle)) Return(q)
      else drain()
    case s@Excepting(q, e) if q.nonEmpty =>
      if (state.compareAndSet(s, Excepting(Queue.empty, e))) Return(q)
      else drain()
    case s@Excepting(q, e) =>
      Throw(e)
    case _ =>
      Return(Queue.empty)
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
  @tailrec
  final def fail(exc: Throwable, discard: Boolean): Unit = state.get match {
    case Idle =>
      if (!state.compareAndSet(Idle, Excepting(Queue.empty, exc)))
        fail(exc, discard)

    case s@Polling(q) =>
      if (!state.compareAndSet(s, Excepting(Queue.empty, exc))) fail(exc, discard) else
        q.foreach(_.setException(exc))

    case s@Offering(q) =>
      val nextq = if (discard) Queue.empty else q
      if (!state.compareAndSet(s, Excepting(nextq, exc))) fail(exc, discard)

    case Excepting(_, _) => // Just take the first one.
  }

  override def toString = "AsyncQueue<%s>".format(state.get)
}
