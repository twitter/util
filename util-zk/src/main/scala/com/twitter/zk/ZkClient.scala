package com.twitter.zk

import scala.collection.JavaConverters._

import org.apache.zookeeper.ZooDefs.Ids.CREATOR_ALL_ACL
import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.{CreateMode, ZooKeeper}

import com.twitter.logging.Logger
import com.twitter.util.{Duration, Future, Timer}

/**
 * An Asynchronous ZooKeeper Client API.
 *
 * A ZkClient instance is configured with settings and defaults (like a retry policy) and is
 * attached to underlying Connector.  This allows new, immutable ZkClient instances to be created
 * with alternative settings on the same underlying Connector.
 */
trait ZkClient {
  val name = "zk.client"
  protected[zk] val log = Logger.get(name)

  /** Build a ZNode handle using this ZkClient. */
  def apply(path: String): ZNode = ZNode(this, path)

  /* Connection management */

  /** Maintains a connection to ZooKeeper. */
  protected[this] val connector: Connector

  /** Get a connected ZooKeeper handle */
  def apply(): Future[ZooKeeper] = connector()

  /** Attach a listener to receive session events */
  def onSessionEvent(f: PartialFunction[StateEvent, Unit]): Unit = {
    connector.onSessionEvent(f)
  }

  /** Release the connection */
  def release(): Future[Unit] = connector.release()

  /* Settings */

  /** Default: Creator access */
  val acl: Seq[ACL] = CREATOR_ALL_ACL.asScala

  /** Build a ZkClient with another default creation ACL. */
  def withAcl(a: Seq[ACL]): ZkClient = transform(_acl = a)

  /** Default: create persistent nodes */
  val mode: CreateMode = CreateMode.PERSISTENT

  /** Build a ZkClient with another default creation mode. */
  def withMode(m: CreateMode): ZkClient = transform(_mode = m)

  /** Default: fail without retrying */
  val retryPolicy: RetryPolicy = RetryPolicy.None

  /** Get a ZkClient with a basic retry policy. */
  def withRetries(n: Int): ZkClient = withRetryPolicy(RetryPolicy.Basic(n))

  /** Get a ZkClient with another retry policy. */
  def withRetryPolicy(r: RetryPolicy): ZkClient = transform(_retryPolicy = r)

  /** Use the current retry policy to perform an operation with a ZooKeeper handle. */
  def retrying[T](op: ZooKeeper => Future[T]): Future[T] = retryPolicy { apply() flatMap (op) }

  /** Create a new ZkClient, possibly overriding configuration. */
  protected[this] def transform(
    _connector: Connector = connector,
    _acl: Seq[ACL] = acl,
    _mode: CreateMode = mode,
    _retryPolicy: RetryPolicy = retryPolicy
  ) = new ZkClient {
    val connector = _connector
    override val acl = _acl
    override val mode = _mode
    override val retryPolicy = _retryPolicy
  }
}

object ZkClient {

  /** Build a ZkClient with a provided Connector */
  def apply(_connector: Connector) = new ZkClient {
    protected[this] val connector = _connector
  }

  /** Build a ZkClient with a NativeConnector */
  def apply(connectString: String, connectTimeout: Option[Duration], sessionTimeout: Duration)(
    implicit timer: Timer
  ): ZkClient = {
    apply(NativeConnector(connectString, connectTimeout, sessionTimeout, timer))
  }

  /** Build a ZkClient with a NativeConnector */
  def apply(connectString: String, connectTimeout: Duration, sessionTimeout: Duration)(
    implicit timer: Timer
  ): ZkClient = {
    apply(connectString, Some(connectTimeout), sessionTimeout)(timer)
  }

  /** Build a ZkClient with a NativeConnector */
  def apply(connectString: String, sessionTimeout: Duration)(implicit timer: Timer): ZkClient = {
    apply(connectString, None, sessionTimeout)(timer)
  }
}
