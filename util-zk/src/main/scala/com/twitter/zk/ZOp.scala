package com.twitter.zk

import com.twitter.concurrent.{Broker, Offer}
import com.twitter.util.{Future, Try}

/** A ZNode-read operation. */
trait ZOp[T <: ZNode.Exists] {

  /** Returns a Future that is satisfied when the operation is complete. */
  def apply(): Future[T]

  /**
   * Get a ZNode and watch for updates to it.
   * If the node does not exist the returned Future is satisfied with a ZNode.Watch containing
   * a Throw(KeeperException.NoNodeException).
   */
  def watch(): Future[ZNode.Watch[T]]

  /** Repeatedly performs a Watch operation and publishes results on an Offer. */
  def monitor(): Offer[Try[T]] = {
    val broker = new Broker[Try[T]]
    // Set the watch, send the result to the broker, and repeat this when an event occurs
    def setWatch(): Unit = {
      watch() onSuccess {
        case ZNode.Watch(result, update) =>
          broker ! result onSuccess { _ => update onSuccess { _ => setWatch() } }
      }
    }
    setWatch()
    broker.recv
  }
}
