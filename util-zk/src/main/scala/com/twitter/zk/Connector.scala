package com.twitter.zk

import com.twitter.concurrent.Offer
import com.twitter.logging.Logger
import com.twitter.util.Future
import org.apache.zookeeper.{ZooKeeper, WatchedEvent}

trait Connector {
  val name = "zk.connector"
  protected[this] lazy val log = Logger.get(name)

  protected[this] val sessionBroker = new EventBroker
  protected[zk] def events: Offer[WatchedEvent] = sessionBroker.recv

  /** Connect to a ZooKeeper cluster and yield a handle once the connection is complete. */
  def apply(): Future[ZooKeeper]

  /** Disconnect from the ZooKeeper server. */
  def release(): Future[Unit]
}
