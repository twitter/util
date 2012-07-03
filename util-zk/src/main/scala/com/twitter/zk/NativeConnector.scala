package com.twitter.zk

import com.twitter.concurrent.{Offer, Serialized}
import com.twitter.util.{Duration, Future, Promise, Return, Timer}
import org.apache.zookeeper.ZooKeeper

/**
 * An Asynchronous ZooKeeper Client.
 */
trait NativeConnector extends Connector with Serialized {
  override val name = "native-zk-connector"
  val connectString: String
  val connectTimeout: Option[Duration]
  val sessionTimeout: Duration
  implicit val timer: Timer

  @volatile protected[this] var connection: Option[NativeConnector.Connection] = None

  def apply() = serialized {
    connection getOrElse {
      val c = mkConnection
      c.sessionEvents.foreach { event =>
        sessionBroker.send(event()).sync()
      }
      connection = Some(c)
      c
    }
  } flatMap { _.apply() }

  protected[this] def mkConnection = {
    new NativeConnector.Connection(connectString, connectTimeout, sessionTimeout)
  }

  onSessionEvent {
    // Invalidate the connection on expiration.
    case StateEvent.Expired => release()()
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
    sessionTimeout: Duration
  )(implicit timer: Timer) extends Serialized {
    @volatile protected[this] var zookeeper: Option[ZooKeeper] = None

    protected[this] val _connected = new Promise[ZooKeeper]

    protected[this] val sessionBroker = new EventBroker

    /*
     * Publish session events, but intercept Connected events and use them to satisfy the pending
     * connection promise if possible.
     *
     * It is possible for events to be sent on this Offer before the connection promise is satisfied
     * and I think that is okay.
     */
    val sessionEvents: Offer[StateEvent] = sessionBroker.recv map { StateEvent(_) } map {
      case event @ StateEvent.Connected => {
        serialized {
          zookeeper.foreach { zk =>
            _connected.updateIfEmpty(Return(zk))
          }
        }
        event
      }
      case event => event
    }

    /** A ZooKeeper handle that will error if connectTimeout is exceeded. */
    lazy val connected: Future[ZooKeeper] = {
      connectTimeout map(_connected.within) getOrElse(_connected)
    }

    protected[this] val _released = new Promise[Unit]
    val released: Future[Unit] = _released

    /** Obtain a ZooKeeper connection */
    def apply(): Future[ZooKeeper] = serialized {
      assert(!_released.isDefined)
      zookeeper = zookeeper orElse Some { mkZooKeeper }
    } flatMap { _ => connected }

    protected[this] def mkZooKeeper = {
      new ZooKeeper(connectString, sessionTimeout.inMillis.toInt, sessionBroker)
    }

    def release(): Future[Unit] = serialized {
      zookeeper.foreach { zk =>
        zk.close()
        zookeeper = None
        _released.setValue(Unit)
      }
    }
  }
}

