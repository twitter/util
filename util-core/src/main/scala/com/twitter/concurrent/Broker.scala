package com.twitter.concurrent

import com.twitter.util.{Future, Promise, Return}
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.collection.immutable.Queue

/**
 * An unbuffered FIFO queue, brokered by `Offer`s. Note that the queue is
 * ordered by successful operations, not initiations, so `one` and `two`
 * may not be received in that order with this code:
 *
 * {{{
 * val b: Broker[Int]
 * b ! 1
 * b ! 2
 * }}}
 *
 * But rather we need to explicitly sequence them:
 *
 * {{{
 * val b: Broker[Int]
 * for {
 *   () <- b ! 1
 *   () <- b ! 2
 * } ()
 * }}}
 *
 * BUGS: the implementation would be much simpler in the absence of
 * cancellation.
 */

class Broker[T] {
  private[this] sealed trait State
  private[this] case object Quiet extends State
  private[this] case class Sending(q: Queue[(Promise[Tx[Unit]], T)]) extends State
  private[this] case class Receiving(q: Queue[Promise[Tx[T]]]) extends State

  private[this] val state = new AtomicReference[State](Quiet)

  @tailrec
  private[this] def rmElem(elem: AnyRef) {
    state.get match {
      case s@Sending(q) =>
        val nextq = q filter { _ ne elem }
        val nextState = if (nextq.isEmpty) Quiet else Sending(nextq)
        if (!state.compareAndSet(s, nextState))
          rmElem(elem)

      case s@Receiving(q) =>
        val nextq = q filter { _ ne elem }
        val nextState = if (nextq.isEmpty) Quiet else Receiving(nextq)
        if (!state.compareAndSet(s, nextState))
          rmElem(elem)

      case Quiet => ()
    }
  }

  def send(msg: T): Offer[Unit] = new Offer[Unit] {
    @tailrec
    def prepare() = {
      state.get match {
        case s@Receiving(Queue(recvp, newq@_*)) =>
          val nextState = if (newq.isEmpty) Quiet else Receiving(Queue(newq:_*))
          if (!state.compareAndSet(s, nextState)) prepare() else {
            val (sendTx, recvTx) = Tx.twoParty(msg)
            recvp.setValue(recvTx)
            Future.value(sendTx)
          }

        case s@(Quiet | Sending(_)) =>
          val p = new Promise[Tx[Unit]]
          val elem = (p, msg)
          val nextState = s match {
            case Quiet => Sending(Queue(elem))
            case Sending(q) => Sending(q enqueue elem)
            case Receiving(_) => throw new IllegalStateException()
          }

          if (!state.compareAndSet(s, nextState)) prepare() else {
            p.onCancellation { rmElem(elem) }
            p
          }

        case Receiving(Queue()) =>
          throw new IllegalStateException()
      }
    }
  }

  val recv: Offer[T] = new Offer[T] {
    @tailrec
    def prepare() =
      state.get match {
        case s@Sending(Queue((sendp, msg), newq@_*)) =>
          val nextState = if (newq.isEmpty) Quiet else Sending(Queue(newq:_*))
          if (!state.compareAndSet(s, nextState)) prepare() else {
            val (sendTx, recvTx) = Tx.twoParty(msg)
            sendp.setValue(sendTx)
            Future.value(recvTx)
          }

        case s@(Quiet | Receiving(_)) =>
          val p = new Promise[Tx[T]]
          val nextState = s match {
            case Quiet => Receiving(Queue(p))
            case Receiving(q) => Receiving(q enqueue p)
            case Sending(_) => throw new IllegalStateException()
          }

          if (!state.compareAndSet(s, nextState)) prepare() else {
            p.onCancellation { rmElem(p) }
            p
          }

        case Sending(Queue()) =>
          throw new IllegalStateException()
      }
  }

  /* Scala actor style / CSP syntax. */

  /**
   * Send an item on the broker, returning a {{Future}} indicating
   * completion.
   */
  def !(msg: T): Future[Unit] = send(msg).sync()

  /**
   * Like {!}, but block until the item has been sent.
   */
  def !!(msg: T): Unit = (this ! msg)()

  /**
   * Retrieve an item from the broker, asynchronously.
   */
  def ? : Future[T] = recv.sync()

  /**
   * Retrieve an item from the broker, blocking.
   */
  def ?? : T = (this?)()
}
