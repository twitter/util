package com.twitter.zk

import org.apache.zookeeper.Watcher.Event.{EventType, KeeperState}
import org.apache.zookeeper.{WatchedEvent, Watcher}

import com.twitter.concurrent.Broker
import com.twitter.util.{Promise, Return}

/*
 * WatchedEvent matchers
 */

object Event {
  def apply(t: EventType, s: KeeperState, p: Option[String]) = new WatchedEvent(t, s, p.orNull)

  def unapply(event: WatchedEvent): Option[(EventType, KeeperState, Option[String])] = {
    Some((event.getType, event.getState, Option { event.getPath }))
  }
}

sealed trait StateEvent {
  val eventType = EventType.None
  val state: KeeperState
  def apply() = Event(eventType, state, None)
  def unapply(event: WatchedEvent) = event match {
    case Event(t, s, _) => (t == eventType && s == state)
    case _ => false
  }
}

object StateEvent {
  object AuthFailed extends StateEvent {
    val state = KeeperState.AuthFailed
  }

  object Connected extends StateEvent {
    val state = KeeperState.SyncConnected
  }

  object Disconnected extends StateEvent {
    val state = KeeperState.Disconnected
  }

  object Expired extends StateEvent {
    val state = KeeperState.Expired
  }

  object ConnectedReadOnly extends StateEvent {
    val state = KeeperState.ConnectedReadOnly
  }

  object SaslAuthenticated extends StateEvent {
    val state = KeeperState.SaslAuthenticated
  }

  def apply(w: WatchedEvent): StateEvent = {
    val state = w.getState
    state match {
      case KeeperState.AuthFailed => AuthFailed
      case KeeperState.SyncConnected => Connected
      case KeeperState.Disconnected => Disconnected
      case KeeperState.Expired => Expired
      case KeeperState.ConnectedReadOnly => ConnectedReadOnly
      case KeeperState.SaslAuthenticated => SaslAuthenticated
      case _ =>
        throw new IllegalArgumentException(s"Can't convert deprecated state to StateEvent: $state")
      //NoSyncConnected and Unknown are depricated in zk 3.x, and should be
      //expected to be removed in zk 4.x
    }
  }
}

sealed trait NodeEvent {
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
  def process(event: WatchedEvent): Unit = { updateIfEmpty(Return(event)) }
}

class EventBroker extends Broker[WatchedEvent] with Watcher {
  def process(event: WatchedEvent): Unit = { send(event).sync() }
}
