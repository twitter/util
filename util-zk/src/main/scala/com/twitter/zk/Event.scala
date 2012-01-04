package com.twitter.zk

import com.twitter.concurrent.Broker
import com.twitter.util.{Promise, Return}
import org.apache.zookeeper.{AsyncCallback, KeeperException, WatchedEvent, Watcher, ZooKeeper}
import org.apache.zookeeper.Watcher.Event.{EventType, KeeperState}

/*
 * WatchedEvent matchers
 */

object Event {
  def apply(t: EventType, s: KeeperState, p: Option[String]) = new WatchedEvent(t, s, p.orNull)

  def unapply(event: WatchedEvent): Option[(EventType, KeeperState, Option[String])] = {
    Some((event.getType, event.getState, Option { event.getPath }))
  }
}

trait StateEvent {
  val eventType = EventType.None
  val state: KeeperState
  def apply() = Event(eventType, state, None)
  def unapply(event: WatchedEvent) = event match {
    case Event(t, s, _) => (t == eventType && s == state)
    case _ => false
  }
}

object StateEvent {
  object Connected extends StateEvent {
    val state = KeeperState.SyncConnected
  }

  object Disconnected extends StateEvent {
    val state = KeeperState.Disconnected
  }

  object Expired extends StateEvent {
    val state = KeeperState.Expired
  }
}

trait NodeEvent {
  val state = KeeperState.SyncConnected
  val eventType: EventType
  def apply(path: String) = Event(eventType, state, Some(path))
  def unapply(event: WatchedEvent) = event match {
    case Event(t, _, somePath) if (t == eventType) => somePath
    case _ => None
  }
}

object NodeEvent {
  object Created extends NodeEvent {
    val eventType = EventType.NodeCreated
  }

  object ChildrenChanged extends NodeEvent {
    val eventType = EventType.NodeChildrenChanged
  }

  object DataChanged extends NodeEvent {
    val eventType = EventType.NodeDataChanged
  }

  object Deleted extends NodeEvent {
    val eventType = EventType.NodeDeleted
  }
}

class EventPromise extends Promise[WatchedEvent] with Watcher {
  def process(event: WatchedEvent) { updateIfEmpty(Return(event)) }
}

class EventBroker extends Broker[WatchedEvent] with Watcher {
  def process(event: WatchedEvent) { send(event).sync() }
}
