package com.twitter.zk

import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec

import org.apache.zookeeper.ZooKeeper

import com.twitter.logging.Logger
import com.twitter.util.Future

trait Connector {

  import Connector.EventHandler

  val name = "zk-connector"
  protected lazy val log = Logger.get(name)

  private[this] val listeners = new AtomicReference[List[EventHandler]](Nil)

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
  final def onSessionEvent(f: EventHandler) {
    val list = listeners.get()
    if (!listeners.compareAndSet(list, f :: list)) onSessionEvent(f)
  }

  /** Connect to a ZooKeeper cluster and yield a handle once the connection is complete. */
  def apply(): Future[ZooKeeper]

  /** Disconnect from the ZooKeeper server. */
  def release(): Future[Unit]
}

object Connector {
  type EventHandler = PartialFunction[StateEvent, Unit]

  /**
   * Dispatches requests across several connectors.
   *
   * Session events from all Connnectors are published on the session broker; however consumers
   * of these events cannot know which specific connection the event was fired on.
   */
  case class RoundRobin(connectors: Connector*) extends Connector {
    require(connectors.length > 0)
    override val name = "round-robin-zk-connector:%d".format(connectors.length)

    private[this] var index = 0
    protected[this] def nextConnector() = {
      val i = synchronized {
        if (index == Int.MaxValue) {
          index = 0
        }
        index = index + 1
        index % connectors.length
      }
      log.trace("connector %d of %d", i + 1, connectors.length)
      connectors(i)
    }

    connectors foreach {
      _ onSessionEvent { case event => sessionBroker.send(event()).sync() }
    }

    def apply(): Future[ZooKeeper] = nextConnector().apply()

    /** Disconnect from all ZooKeeper servers. */
    def release(): Future[Unit] = Future.join {
      log.trace("release")
      connectors map { _.release() }
    }
  }
}
