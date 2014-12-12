package com.twitter.concurrent

import com.twitter.util.{Future, Promise}
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.collection.immutable.Queue

object AsyncQueue {
  private sealed trait State[+T]
  private case object Idle extends State[Nothing]
  private case class Offering[T](q: Queue[T]) extends State[T]
  private case class Polling[T](q: Queue[Promise[T]]) extends State[T]
  private case class Excepting[T](q: Queue[T], exc: Throwable) extends State[T]
}

/**
 * An asynchronous FIFO queue. In addition to providing {{offer()}}
 * and {{poll()}}, the queue can be "failed", flushing current
 * pollers.
 */
class AsyncQueue[T] {
  import AsyncQueue._

  private[this] val state = new AtomicReference[State[T]](Idle)

  def size: Int = state.get match {
    case Offering(q) => q.size
    case _ => 0
  }

  /**
   * Retrieves and removes the head of the queue, completing the
   * returned future when the element is available.
   */
  @tailrec
  final def poll(): Future[T] = state.get match {
    case s@Idle =>
      val p = new Promise[T]
      if (state.compareAndSet(s, Polling(Queue(p)))) p else poll()

    case s@Polling(q) =>
      val p = new Promise[T]
      if (state.compareAndSet(s, Polling(q.enqueue(p)))) p else poll()

    case s@Offering(q) =>
      val (elem, nextq) = q.dequeue
      val nextState = if (nextq.nonEmpty) Offering(nextq) else Idle
      if (state.compareAndSet(s, nextState)) Future.value(elem) else poll()

    case Excepting(q, exc) if q.isEmpty =>
      Future.exception(exc)

    case s@Excepting(q, exc) =>
      val (elem, nextq) = q.dequeue
      val nextState = Excepting(nextq, exc)
      if (state.compareAndSet(s, nextState)) Future.value(elem) else poll()
  }

  /**
   * Insert the given element at the tail of the queue.
   */
  @tailrec
  final def offer(elem: T): Unit = state.get match {
    case s@Idle =>
      if (!state.compareAndSet(s, Offering(Queue(elem))))
        offer(elem)

    case s@Offering(q) =>
      if (!state.compareAndSet(s, Offering(q.enqueue(elem))))
        offer(elem)

    case s@Polling(q) =>
      val (waiter, nextq) = q.dequeue
      val nextState = if (nextq.nonEmpty) Polling(nextq) else Idle
      if (state.compareAndSet(s, nextState))
        waiter.setValue(elem)
      else
        offer(elem)

    case Excepting(_, _) =>
      // Drop.
  }

  /**
   * Fail the queue: current and subsequent pollers will be completed
   * with the given exception; any outstanding messages are discarded.
   */
  final def fail(exc: Throwable): Unit = fail(exc, true)

  /**
   * Fail the queue. When `discard` is true, the queue contents is discarded
   * and all pollers are failed immediately. When this flag is false, subsequent
   * pollers are not failed until the queue becomes empty.
   *
   * No new elements are admitted to the queue after it has been failed.
   */
  @tailrec
  final def fail(exc: Throwable, discard: Boolean): Unit = state.get match {
    case s@Idle =>
      if (!state.compareAndSet(s, Excepting(Queue.empty, exc)))
        fail(exc, discard)

    case s@Polling(q) =>
      if (!state.compareAndSet(s, Excepting(Queue.empty, exc))) fail(exc, discard) else
        q foreach(_.setException(exc))

    case s@Offering(q) =>
      val nextq = if (discard) Queue.empty else q
      if (!state.compareAndSet(s, Excepting(nextq, exc))) fail(exc, discard)

    case Excepting(_, _) => // Just take the first one.
  }

  override def toString = "AsyncQueue<%s>".format(state.get)
}
