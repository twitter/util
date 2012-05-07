package com.twitter.zk

import com.twitter.concurrent.{Offer, Serialized}
import com.twitter.util.{Duration, Future, Promise, Timer}
import org.apache.zookeeper.ZooKeeper

/**
 * An Asynchronous ZooKeeper Client.
 */
trait NativeConnector extends Connector with Serialized {
  val connectString: String
  val connectTimeout: Option[Duration]
  val sessionTimeout: Duration
  implicit val timer: Timer

  @volatile protected[this] var connection: Option[NativeConnector.Connection] = None

  def apply() = serialized {
    connection getOrElse {
      val c = mkConnection
      connection = Some(c)
      c
    }
  } flatMap { _.apply() }

  protected[this] def mkConnection = {
    new NativeConnector.Connection(connectString, connectTimeout, sessionTimeout, sessionBroker, this)
  }

  def release() = serialized {
    connection map { c =>
      connection = None
      c
    }
  } flatMap {
    case Some(c) => c.release()
    case None => Future.Done
  }
}

object NativeConnector {
  /** Build a connector */
  def apply(
      _connectString: String,
      _connectTimeout: Option[Duration],
      _sessionTimeout: Duration)
      (implicit _timer: Timer): NativeConnector = new NativeConnector {
    val connectString  = _connectString
    val connectTimeout = _connectTimeout
    val sessionTimeout = _sessionTimeout
    implicit val timer = _timer
  }

  /**
   * Maintains connection and ensures all session events are published.
   * Once a connection is released, it may not be re-opened.
   */
  class Connection(
    connectString: String,
    connectTimeout: Option[Duration],
    sessionTimeout: Duration,
    sessionBroker: EventBroker,
    connector: Connector
  )(implicit timer: Timer) extends Serialized {
    @volatile protected[this] var zookeeper: ZooKeeper = null

    protected[this] val _connected = new Promise[ZooKeeper]

    /** A ZooKeeper handle that will error if connectTimeout is exceeded. */
    lazy val connected: Future[ZooKeeper] = {
      connectTimeout map(_connected.within) getOrElse(_connected)
    }

    protected[this] val _released = new Promise[Unit]
    val released: Future[Unit] = _released

    /** Watch session events for a connection event. */
    connector.onSessionEvent { case StateEvent.Connected() => _connected.setValue(zookeeper) }

    /** Obtain a ZooKeeper connection */
    def apply(): Future[ZooKeeper] = serialized {
      assert(!_released.isDefined)
      zookeeper = Option { zookeeper } getOrElse { mkZooKeeper }
    } flatMap { _ => connected }

    protected[this] def mkZooKeeper = {
      new ZooKeeper(connectString, sessionTimeout.inMillis.toInt, sessionBroker)
    }

    def release(): Future[Unit] = serialized {
      if (zookeeper != null) {
        zookeeper.close()
        zookeeper = null
        _released.setValue(Unit)
      }
    }
  }
}

