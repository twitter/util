package com.twitter.zk

import com.twitter.logging.Logger
import com.twitter.util.Future
import java.util.concurrent.atomic.AtomicReference
import org.apache.zookeeper.ZooKeeper
import scala.annotation.tailrec

trait Connector {
  val name = "zk-connector"
  protected[this] lazy val log = Logger.get(name)

  private[this] val listeners = new AtomicReference[List[PartialFunction[StateEvent, Unit]]](Nil)

  protected[this] val sessionBroker: EventBroker = new EventBroker

  // a broker may only be used for 1:1 communication, so we fan-out event notifications
  sessionBroker.recv foreach { event =>
    val listening = listeners.get()
    log.debug("propagating event to %d listeners %s", listening.size, event)
    val stateEvent = StateEvent(event)
    listening.foreach { listener =>
      if (listener.isDefinedAt(stateEvent)) {
        try {
          listener(stateEvent)
        } catch {
          case e: Throwable => log.error(e, "Exception in connection event listener")
        }
      } else log.debug("listener does not handle %s", event)
    }
  }


  @tailrec
  final def onSessionEvent(f: PartialFunction[StateEvent, Unit]) {
    val list = listeners.get()
    if (!listeners.compareAndSet(list, f :: list)) onSessionEvent(f)
  }

  /** Connect to a ZooKeeper cluster and yield a handle once the connection is complete. */
  def apply(): Future[ZooKeeper]

  /** Disconnect from the ZooKeeper server. */
  def release(): Future[Unit]
}
