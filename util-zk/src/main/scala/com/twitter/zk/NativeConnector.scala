package com.twitter.zk

import com.twitter.concurrent.{Broker, Offer, Serialized}
import com.twitter.logging.Logger
import com.twitter.util.{Await, Duration, Future, Promise, Return, TimeoutException, Timer}
import org.apache.zookeeper.ZooKeeper

/**
 * An Asynchronous ZooKeeper Client.
 */
case class NativeConnector(
  connectString: String,
  connectTimeout: Option[Duration],
  sessionTimeout: Duration,
  timer: Timer,
  authenticate: Option[AuthInfo] = None)
    extends Connector
    with Serialized {
  override val name = "native-zk-connector"

  protected[this] def mkConnection = {
    new NativeConnector.Connection(
      connectString,
      connectTimeout,
      sessionTimeout,
      authenticate,
      timer,
      log
    )
  }

  onSessionEvent {
    // Invalidate the connection on expiration.
    case StateEvent.Expired => Await.ready(release())
  }

  /*
   * Access to connection must be serialized, i.e. enqueued to be processed asynchronously and
   * serially, in order to prevent race conditions between invocations of apply() and release()
   * since both of these operations may read and write to this variable.  This helps to ensure that
   * at most one connection is active.
   */
  @volatile private[this] var connection: Option[NativeConnector.Connection] = None

  /**
   * Get the connection if one already exists or obtain a new one.
   * If the connection is not received within connectTimeout, the connection is released.
   */
  def apply(): Future[ZooKeeper] =
    serialized {
      connection.getOrElse {
        val c = mkConnection
        c.sessionEvents foreach { event =>
          sessionBroker.send(event()).sync()
        }
        connection = Some(c)
        c
      }.apply
    }.flatten
      .rescue {
        case e: NativeConnector.ConnectTimeoutException =>
          release() flatMap { _ =>
            Future.exception(e)
          }
      }

  /**
   * If there is a connection, release it.
   */
  def release(): Future[Unit] =
    serialized {
      connection match {
        case None => Future.Unit
        case Some(c) =>
          connection = None
          c.release()
      }
    }.flatten
}

object NativeConnector {
  def apply(
    connectString: String,
    sessionTimeout: Duration
  )(
    implicit timer: Timer
  ): NativeConnector = {
    NativeConnector(connectString, None, sessionTimeout, timer)
  }

  def apply(
    connectString: String,
    connectTimeout: Duration,
    sessionTimeout: Duration
  )(
    implicit timer: Timer
  ): NativeConnector = {
    NativeConnector(connectString, Some(connectTimeout), sessionTimeout, timer)
  }

  def apply(
    connectString: String,
    connectTimeout: Duration,
    sessionTimeout: Duration,
    authenticate: Option[AuthInfo]
  )(
    implicit timer: Timer
  ): NativeConnector = {
    NativeConnector(connectString, Some(connectTimeout), sessionTimeout, timer, authenticate)
  }

  case class ConnectTimeoutException(connectString: String, timeout: Duration)
      extends TimeoutException("timeout connecting to %s after %s".format(connectString, timeout))

  /**
   * Maintains connection and ensures all session events are published.
   * Once a connection is released, it may not be re-opened.
   *
   * apply() and release() must not be called concurrently. This is enforced by NativeConnector.
   */
  protected class Connection(
    connectString: String,
    connectTimeout: Option[Duration],
    sessionTimeout: Duration,
    authenticate: Option[AuthInfo],
    timer: Timer,
    log: Logger) {

    @volatile protected[this] var zookeeper: Option[ZooKeeper] = None

    protected[this] val connectPromise = new Promise[ZooKeeper]

    /** A ZooKeeper handle that will error if connectTimeout is specified and exceeded. */
    lazy val connected: Future[ZooKeeper] = connectTimeout
      .map { timeout =>
        connectPromise.within(timer, timeout).rescue {
          case _: TimeoutException =>
            Future.exception(ConnectTimeoutException(connectString, timeout))
        }
      }
      .getOrElse(connectPromise)

    protected[this] val releasePromise = new Promise[Unit]
    val released: Future[Unit] = releasePromise

    protected[this] val sessionBroker = new EventBroker

    /**
     * Publish session events, but intercept Connected events and use them to satisfy the pending
     * connection promise if possible, and add the authentication handle.
     */
    val sessionEvents: Offer[StateEvent] = {
      val broker = new Broker[StateEvent]
      def loop(): Unit = {
        sessionBroker.recv
          .sync()
          .map { StateEvent(_) }
          .respond {
            case Return(StateEvent.Connected) =>
              zookeeper.foreach { zk =>
                connectPromise.updateIfEmpty(Return(zk))
                authenticate.foreach { auth =>
                  log.info("Authenticating to zk as %s".format(new String(auth.data, "UTF-8")))
                  zk.addAuthInfo(auth.mode, auth.data)
                }
              }
            case _ =>
          }
          .flatMap { broker.send(_).sync() }
          .ensure { loop() }
      }
      loop()
      broker.recv
    }

    /**
     * Obtain a ZooKeeper connection
     *
     * There must not be concurrent calls to this method and/or release();  this is enforced by
     * NativeConnector.
     */
    def apply(): Future[ZooKeeper] = {
      assert(!releasePromise.isDefined)
      zookeeper = zookeeper.orElse { Some { mkZooKeeper } }
      connected
    }

    protected[this] def mkZooKeeper = {
      new ZooKeeper(connectString, sessionTimeout.inMillis.toInt, sessionBroker)
    }

    def release(): Future[Unit] = Future {
      zookeeper foreach { zk =>
        log.debug("release")
        zk.close()
        zookeeper = None
        releasePromise.setValue(())
      }
    }
  }
}
