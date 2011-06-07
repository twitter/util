package com.twitter.concurrent

/*
 * Brokers coordinate offers to send and receive messages in FIFO
 * order.  They guarantee synchronous and exclusive delivery between
 * sender and receiver.
 *
 * Brokers are analogous to "Channels" in Concurrent ML or Go.
 * However, we name them differently here in order to avoid confusion
 * with {{com.twitter.concurrent.Channel}}.
 */

import scala.collection.mutable.Queue

import com.twitter.util.{Future, Promise, Return}

object Broker {
  private[Broker] class WaitQ[T] {
    type Waiter = () => Option[T]
    private[this] val q = new Queue[Waiter]
  
    def enqueue(waiter: => Option[T]): Waiter = synchronized {
      val w = { () => waiter }
      q += w
      w
    }
    
    def remove(waiter: Waiter) = synchronized {
      // this could get expensive (linear)
      q.dequeueFirst { _ eq waiter }
    }
    
    def dequeue(): Option[T] = synchronized {
      while (!q.isEmpty) {
        val waiter = q.dequeue()
        waiter() foreach { item =>
          return Some(item)
        }
      }
      
      None
    }
  
    def size: Int = synchronized { q.size }
  }
}

// todo: provide buffered brokers
// todo: close() semantics -- should we freak out on further
// sends/receives rather than just dropping them, as now?
class Broker[E] {
  /* 
   * We rely on the fact that `putq' and `getq' aren't simultaneously
   * nonempty.  The rest of the implementation follows easily from
   * this invariant.
   */

  import Broker._

  private[this] type Getter = E => Unit
  private[this] type Putter = Getter => Unit
  private[this] val putq = new WaitQ[Putter]
  private[this] val getq = new WaitQ[Getter]
  private[this] val closed = new Promise[Unit]

  /**
   * Create an offer to broker the sending of the value {{e}}.  Upon
   * synchronization, the offer is realized exactly when there is a
   * receiver that is also synchronizing.
   */
  def send(e: => E) = new Offer[Unit] {
    def poll(): Option[() => Unit] = {
      if (closed.isDefined) return None
      getq.dequeue() flatMap { getter =>
        getter(e)
        Some(() => ())
      }
    }

    def enqueue(setter: Offer[Unit]#Setter) = {
      val waiter = putq.enqueue {
        // todo: shoudl we do get the get inside of the offer set block?
        setter() map { set => { getter => getter(e); set(() => ()) } }
      }
      () => putq.remove(waiter)
    }

    def objects = Seq(this)
  }

  /**
   * Create an offer to receive a brokered value.  Upon
   * synchronization, the offer is realized exactly when there is a
   * sender.  Receives are in FIFO order of sends.
   */
  def recv = new Offer[E] {
    def poll(): Option[() => E] = {
      if (closed.isDefined) return None
      putq.dequeue() match {
        case Some(putter) =>
          var res: Option[() => E] = None
          putter { e => res = Some(() => e); () }
          res
        case None =>
          None
      }
    }
    
    def enqueue(setter: Offer[E]#Setter) = {
      val waiter = getq.enqueue {
        setter() map { set => { e => set(() => e) } }
      }      
      () => getq.remove(waiter)
    }  
    
    def objects = Seq(this)
  }

  /**
   * Close this broker. This activates the offer given by {{onClose}}.
   */  
  def close() {
    closed.updateIfEmpty(Return(()))
  }

  /**
   * An offer that is activated when this broker has been closed.
   */
  val onClose: Offer[Unit] = closed.toOffer const ()
}
