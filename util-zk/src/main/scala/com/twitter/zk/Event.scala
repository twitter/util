package com.twitter.zk

import org.apache.zookeeper.Watcher.Event.{EventType, KeeperState}
import org.apache.zookeeper.{WatchedEvent, Watcher}

import com.twitter.concurrent.Broker
import com.twitter.util.{Promise, Return}

/*
 * WatchedEvent matchers
 */

object Event {
  def apply(t: EventType, s: KeeperState, p: Option[String]): WatchedEvent =
    new WatchedEvent(t, s, p.orNull)

  def unapply(event: WatchedEvent): Option[(EventType, KeeperState, Option[String])] = {
    Some((event.getType, event.getState, Option { event.getPath }))
  }
}

sealed trait StateEvent {
  val eventType: EventType = EventType.None
  val state: KeeperState
  def apply(): WatchedEvent = Event(eventType, state, None)
  def unapply(event: WatchedEvent): Boolean = event match {
    case Event(t, s, _) => (t == eventType && s == state)
    case _ => false
  }
}

object StateEvent {
  object AuthFailed extends StateEvent {
    val state: KeeperState = KeeperState.AuthFailed
  }

  object Connected extends StateEvent {
    val state: KeeperState = KeeperState.SyncConnected
  }

  object Disconnected extends StateEvent {
    val state: KeeperState = KeeperState.Disconnected
  }

  object Expired extends StateEvent {
    val state: KeeperState = KeeperState.Expired
  }

  object ConnectedReadOnly extends StateEvent {
    val state: KeeperState = KeeperState.ConnectedReadOnly
  }

  object SaslAuthenticated extends StateEvent {
    val state: KeeperState = KeeperState.SaslAuthenticated
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
  val state: KeeperState = KeeperState.SyncConnected
  val eventType: EventType
  def apply(path: String): WatchedEvent = Event(eventType, state, Some(path))
  def unapply(event: WatchedEvent): Option[String] = event match {
    case Event(t, _, somePath) if (t == eventType) => somePath
    case _ => None
  }
}

object NodeEvent {
  object Created extends NodeEvent {
    val eventType: EventType = EventType.NodeCreated
  }

  object ChildrenChanged extends NodeEvent {
    val eventType: EventType = EventType.NodeChildrenChanged
  }

  object DataChanged extends NodeEvent {
    val eventType: EventType = EventType.NodeDataChanged
  }

  object Deleted extends NodeEvent {
    val eventType: EventType = EventType.NodeDeleted
  }
}

class EventPromise extends Promise[WatchedEvent] with Watcher {
  def process(event: WatchedEvent): Unit = { updateIfEmpty(Return(event)) }
}

class EventBroker extends Broker[WatchedEvent] with Watcher {
  def process(event: WatchedEvent): Unit = { send(event).sync() }
}
