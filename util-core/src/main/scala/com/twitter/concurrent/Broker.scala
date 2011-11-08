package com.twitter.concurrent

/*
 * Brokers coordinate offers to send and receive messages in FIFO
 * order.  They guarantee synchronous and exclusive delivery between
 * sender and receiver.
 *
 * Brokers are analogous to "Channels" in Concurrent ML or Go.
 * However, we name them differently here in order to avoid confusion
 * with {{com.twitter.concurrent.Channel}}.  Also, "Broker" more
 * accurately reflects its function, in our view.
 */

import scala.collection.mutable.Queue

import com.twitter.util.{Future, Promise, Return}

// todo: provide buffered brokers
class Broker[E] {
  /*
   * We rely on the fact that `sendq' and `recvq' aren't
   * simultaneously nonempty.  The correctness of the implementation
   * follows.
   */
  private[this] val sendq = new Queue[(E, Offer[Unit]#Setter)]
  private[this] val recvq = new Queue[Offer[E]#Setter]

   /**
   * Create an offer to broker the sending of the value {{e}}.  Upon
   * synchronization, the offer is realized exactly when there is a
   * receiver that is also synchronizing.
   */
  def send(e: E): Offer[Unit] = new Offer[Unit] {
    def poll(): Option[() => Unit] = {
      while (!recvq.isEmpty) {
        val setter = recvq.dequeue()
        setter() foreach { receiver =>
          return Some { () => receiver(e) }
        }
      }

      None
    }

    def enqueue(setter: Offer[Unit]#Setter) = {
      val item = (e, setter)
      sendq += item
      () => { sendq.dequeueFirst { _ eq item } }
    }

    def objects = Seq(Broker.this)
  }

  /**
   * Create an offer to receive a brokered value.  Upon
   * synchronization, the offer is realized exactly when there is a
   * sender.  Receives are in FIFO order of sends.
   */
  def recv: Offer[E] = new Offer[E] {
    def poll(): Option[() => E] = {
      while (!sendq.isEmpty) {
        val (e, setter) = sendq.dequeue()
        setter() foreach { sender =>
          return Some { () =>
            sender(())
            e
          }
        }
      }

      None
    }

    def enqueue(setter: Offer[E]#Setter) = {
      recvq += setter
      () => { recvq.dequeueFirst { _ eq setter } }
    }

    def objects = Seq(Broker.this)
  }

  /* Scala actor style syntax. */

  /**
   * Send an item on the broker, returning a {{Future}} indicating
   * completion.
   */
  def !(e: E): Future[Unit] = send(e)()

  /**
   * Like {!}, but block until the item has been sent.
   */
  def !!(e: E): Unit = this.!(e)()

  /**
   * Retrieve an item from the broker, asynchronously.
   */
  def ? : Future[E] = recv()

  /**
   * Retrieve an item from the broker, blocking.
   */
  def ?? : E = this.?()
}
