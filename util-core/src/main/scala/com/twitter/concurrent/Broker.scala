package com.twitter.concurrent

import com.twitter.util.{Future, Promise, Return, Await}
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
        case s@Receiving(rq) =>
          if (rq.isEmpty) throw new IllegalStateException()
          val (recvp, newq) = rq.dequeue
          val nextState = if (newq.isEmpty) Quiet else Receiving(newq)
          if (!state.compareAndSet(s, nextState)) prepare() else {
            val (sendTx, recvTx) = Tx.twoParty(msg)
            recvp.setValue(recvTx)
            Future.value(sendTx)
          }

        case s@(Quiet | Sending(_)) =>
          val p = new Promise[Tx[Unit]]
          val elem: (Promise[Tx[Unit]], T) = (p, msg)
          p.setInterruptHandler {
            case _ => rmElem(elem)
          }
          val nextState = s match {
            case Quiet => Sending(Queue(elem))
            case Sending(q) => Sending(q enqueue elem)
            case Receiving(_) => throw new IllegalStateException()
          }

          if (state.compareAndSet(s, nextState)) p else prepare()
      }
    }
  }

  val recv: Offer[T] = new Offer[T] {
    @tailrec
    def prepare() =
      state.get match {
        case s@Sending(sq) =>
          if (sq.isEmpty) throw new IllegalStateException()
          val ((sendp, msg), newq) = sq.dequeue
          val nextState = if (newq.isEmpty) Quiet else Sending(newq)
          if (!state.compareAndSet(s, nextState)) prepare() else {
            val (sendTx, recvTx) = Tx.twoParty(msg)
            sendp.setValue(sendTx)
            Future.value(recvTx)
          }

        case s@(Quiet | Receiving(_)) =>
          val p = new Promise[Tx[T]]
          p.setInterruptHandler { case _ => rmElem(p) }
          val nextState = s match {
            case Quiet => Receiving(Queue(p))
            case Receiving(q) => Receiving(q enqueue p)
            case Sending(_) => throw new IllegalStateException()
          }

          if (state.compareAndSet(s, nextState)) p else prepare()
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
  def !!(msg: T): Unit = Await.result(this ! msg)

  /**
   * Retrieve an item from the broker, asynchronously.
   */
  def ? : Future[T] = recv.sync()

  /**
   * Retrieve an item from the broker, blocking.
   */
  def ?? : T = Await.result(this?)
}
