package com.twitter.zk

import com.twitter.concurrent.{Broker, Offer}
import com.twitter.util.{Future, Return, Throw, Try}
import org.apache.zookeeper.{CreateMode, KeeperException, WatchedEvent}
import org.apache.zookeeper.common.PathUtils
import org.apache.zookeeper.data.{ACL, Stat}
import scala.collection.JavaConverters._
import scala.collection.Seq

/**
 * A handle to a ZNode attached to a ZkClient
 */
trait ZNode {
  /** Absolute path of ZNode */
  val path: String

  protected[zk] val zkClient: ZkClient
  protected[this] lazy val log = zkClient.log

  override def hashCode = path.hashCode

  override def toString = "ZNode(%s)".format(path)

  /** ZNodes are equal if they share a path. */
  override def equals(other: Any) = other match {
    case ZNode(p) => p == path
    case _ => false
  }

  /*
   * Helpers
   */

  /** Get a child node. */
  def apply(child: String): ZNode = ZNode(zkClient, childPath(child))

  /** Build a ZNode with its metadata. */
  def apply(stat: Stat): ZNode.Exists = ZNode.Exists(this, stat)

  /** Build a ZNode with its metadata and children. */
  def apply(stat: Stat, children: Seq[String]): ZNode.Children = ZNode.Children(this, stat, children)

  /** Build a ZNode with its metadata and data. */
  def apply(stat: Stat, bytes: Array[Byte]): ZNode.Data = ZNode.Data(this, stat, bytes)

  /** The 'basename' of the ZNode path. */
  lazy val name: String = path.split('/') match {
    case Array() => path
    case nodes => nodes.last
  }

  /** The parent node.  The root node is its own parent. */
  lazy val parent: ZNode = ZNode(zkClient, parentPath)
  lazy val parentPath: String = path.split('/') match {
    case Array() => path
    case nodes => nodes.tail.init.mkString("/", "/", "")
  }

  /** The absolute path of a child */
  def childPath(child: String): String = path match {
    case path if (!path.endsWith("/")) => path + "/" + child
    case path => path + child
  }

  /** Create a copy of this ZNode with an alternate ZkClient. */
  def withZkClient(zk: ZkClient): ZNode = ZNode(zk, path)

  /*
   * Remote node operations
   */

  /** Returns a Future that is satisfied with this ZNode */
  def create(
      data: Array[Byte] = Array.empty[Byte],
      acls: Seq[ACL]    = zkClient.acl,
      mode: CreateMode  = zkClient.mode): Future[ZNode] = {
    zkClient.retrying { zk =>
      val result = new StringCallbackPromise
      zk.create(path, data, acls.asJava, mode, result, null)
      result map { newPath => ZNode(zkClient, newPath) }
    }
  }

  /** Returns a Future that is satisfied with this ZNode */
  def delete(version: Int = 0): Future[ZNode] = zkClient.retrying { zk =>
    val result = new UnitCallbackPromise
    zk.delete(path, version, result, null)
    result map { _ => this }
  }

  /** Returns a Future that is satisfied with this ZNode with its metadata and data */
  def setData(data: Array[Byte], version: Int): Future[ZNode.Data] = zkClient.retrying { zk =>
    val result = new ExistsCallbackPromise(this)
    zk.setData(path, data, version, result, null)
    result map { _.apply(data) }
  }

  /** Returns a Future that is satisfied with a reference to this ZNode */
  def sync(): Future[ZNode] = zkClient.retrying { zk =>
    val result = new UnitCallbackPromise
    zk.sync(path, result, null)
    result map { _ => this }
  }

  /** Provides access to this node's children. */
  val getChildren: ZOp[ZNode.Children] =  new ZOp[ZNode.Children] {
    import LiftableFuture._

    /** Get this ZNode with its metadata and children */
    def apply() = zkClient.retrying { zk =>
      val result = new ChildrenCallbackPromise(ZNode.this)
      zk.getChildren(path, false, result, null)
      result
    }

    /**
     * Get a ZNode with its metadata and children; and install a watch for changes.
     *
     * The returned ZNode.Watch encapsulates the return value from a ZNode operation and the
     * watch that will fire when a ZNode operation completes.  If the ZNode does not exist, the
     * result will be a Throw containing a KeeperException.NoNodeExists, though the watch will
     * fire when an event occurs.  If any other errors occur when fetching the ZNode, the returned
     * Future will error without returning a Watch.
     */
    def watch() = zkClient.retrying { zk =>
      val result = new ChildrenCallbackPromise(ZNode.this)
      val update = new EventPromise
      zk.getChildren(path, update, result, null)
      result.liftNoNode map { ZNode.Watch(_, update) }
    }
  }

  /** Provides access to this node's data. */
  val getData: ZOp[ZNode.Data] = new ZOp[ZNode.Data] {
    import LiftableFuture._

    /** Get this node's data */
    def apply() = zkClient.retrying { zk =>
      val result = new DataCallbackPromise(ZNode.this)
      zk.getData(path, false, result, null)
      result
    }

    /**
     * Get this node's metadata and data; and install a watch for changes.
     *
     * The returned ZNode.Watch encapsulates the return value from a ZNode operation and the
     * watch that will fire when a ZNode operation completes.  If the ZNode does not exist, the
     * result will be a Throw containing a KeeperException.NoNodeExists, though the watch will
     * fire when an event occurs.  If any other errors occur when fetching the ZNode, the returned
     * Future will error without returning a Watch.
     */
    def watch() = zkClient.retrying { zk =>
      val result = new DataCallbackPromise(ZNode.this)
      val update = new EventPromise
      zk.getData(path, update, result, null)
      result.liftNoNode map { ZNode.Watch(_, update) }
    }
  }

  /** Provides access to this node's metadata. */
  val exists: ZOp[ZNode.Exists] = new ZOp[ZNode.Exists] {
    import LiftableFuture._

    /** Get this node's metadata. */
    def apply() = zkClient.retrying { zk =>
      val result = new ExistsCallbackPromise(ZNode.this)
      zk.exists(path, false, result, null)
      result
    }

    /** Get this node's metadata and watch for updates */
    def watch() = zkClient.retrying { zk =>
      val result = new ExistsCallbackPromise(ZNode.this)
      val update = new EventPromise
      zk.exists(path, update, result, null)
      result.liftNoNode.map { ZNode.Watch(_, update) }
    }
  }

  /**
   * Continuously watch all nodes in this subtree for child updates.
   *
   * A ZNode.TreeUpdate is offered for each node in the tree.
   *
   * If this node is deleted and it had children, an offer is sent indicating that this
   * node no longer has children.  A watch is maintained on deleted nodes so that if the
   * parent node is not monitored, the monitor continues to work when the node is restored.
   */
  def monitorTree(): Offer[ZNode.TreeUpdate] = {
    val broker = new Broker[ZNode.TreeUpdate]
    /** Pipe events from a subtree's monitor to this broker. */
    def pipeSubTreeUpdates(next: Offer[ZNode.TreeUpdate]) {
      next() flatMap(broker!) onSuccess { _ => pipeSubTreeUpdates(next) }
    }
    /** Monitor a watch on this node. */
    def monitorWatch(watch: Future[ZNode.Watch[ZNode.Children]], knownChildren: Set[ZNode]) {
      watch onSuccess {
        // When a node is fetched with a watch, send a ZNode.TreeUpdate on the broker, and start
        // monitoring
        case ZNode.Watch(Return(parent), eventUpdate) => {
          val children = parent.children.toSet
          val treeUpdate = ZNode.TreeUpdate(parent,
              added = children -- knownChildren,
              removed = knownChildren -- children)
          broker!(treeUpdate) onSuccess { _ =>
            treeUpdate.added foreach { z =>
              pipeSubTreeUpdates(z.monitorTree())
            }
            eventUpdate onSuccess {
              case MonitorableEvent() => monitorWatch(parent.getChildren.watch(), children)
              case event => log.error("Unmonitorable event: %s: %s", path, event)
            }
          }
        }
        case ZNode.Watch(Throw(ZNode.Error(_path)), eventUpdate) => {
          // Tell the broker about the children we lost; otherwise, if there were no children,
          // this deletion should be reflected in a watch on the parent node, if one exists.
          if (knownChildren.size > 0) {
            broker! ZNode.TreeUpdate(this, removed = knownChildren)
          } else { Future.Done } onSuccess { _ =>
            eventUpdate onSuccess {
              case MonitorableEvent() => monitorWatch(parent.getChildren.watch(), Set.empty[ZNode])
              case event => log.error("Unmonitorable event: %s: %s", path, event)
            }
          }
        }
      } handle { case e =>
        // An error occurred and there's not really anything we can do about it.
        log.error(e, "%s: watch could not be established", path)
      }
    }
    // Initially, we don't know about any children for the node.
    monitorWatch(getChildren.watch(), Set.empty[ZNode])
    broker.recv
  }

  protected[this] object MonitorableEvent {
    def unapply(event: WatchedEvent) = event match {
      case NodeEvent.ChildrenChanged(p) => true
      case NodeEvent.Created(p) => true
      case NodeEvent.Deleted(p) => true
      case event => false
    }
  }
}

/**
 * ZNode utilities and return types.
 */
object ZNode {
  /** Build a ZNode */
  def apply(zk: ZkClient, _path: String) = new ZNode {
    PathUtils.validatePath(_path)
    protected[zk] val zkClient = zk
    val path = _path
  }

  /** matcher */
  def unapply(znode: ZNode) = Some(znode.path)

  /** A matcher for KeeperExceptions that have a non-null path. */
  object Error {
    def unapply(ke: KeeperException) = Option(ke.getPath)
  }

  /** A ZNode with its Stat metadata. */
  trait Exists extends ZNode {
    val stat: Stat

    override def equals(other: Any) = other match {
      case Exists(p, s) => (p == path && s == stat)
      case o => super.equals(o)
    }

    def apply(children: Seq[String]): ZNode.Children = apply(stat, children)
    def apply(bytes: Array[Byte]): ZNode.Data = apply(stat, bytes)
  }

  object Exists {
    def apply(znode: ZNode, _stat: Stat) = new Exists {
      val path = znode.path
      protected[zk] val zkClient = znode.zkClient
      val stat = _stat
    }
    def apply(znode: Exists): Exists = apply(znode, znode.stat)
    def unapply(znode: Exists) = Some((znode.path, znode.stat))
  }

  /** A ZNode with its Stat metadata and children znodes. */
  trait Children extends Exists {
    val stat: Stat
    val children: Seq[ZNode]

    override def equals(other: Any) = other match {
      case Children(p, s, c) => (p == path && s == stat && c == children)
      case o => super.equals(o)
    }

  }

  object Children {
    def apply(znode: Exists, _children: Seq[ZNode]): Children = new Children {
      val path = znode.path
      protected[zk] val zkClient = znode.zkClient
      val stat = znode.stat
      val children = _children
    }
    def apply(znode: ZNode, stat: Stat, children: Seq[String]): Children = {
      apply(Exists(znode, stat), children.map(znode.apply))
    }
    def unapply(z: Children) = Some((z.path, z.stat, z.children))
  }

  /** A ZNode with its Stat metadata and data. */
  trait Data extends Exists {
    val stat: Stat
    val bytes: Array[Byte]

    override def equals(other: Any) = other match {
      case Data(p, s, b) => (p == path && s == stat && b == bytes)
      case o => super.equals(o)
    }
  }

  object Data {
    def apply(znode: ZNode, _stat: Stat, _bytes: Array[Byte]) = new Data {
      val path = znode.path
      protected[zk] val zkClient = znode.zkClient
      val stat = _stat
      val bytes = _bytes
    }
    def apply(znode: Exists, bytes: Array[Byte]): Data = apply(znode, znode.stat, bytes)
    def unapply(znode: Data) = Some((znode.path, znode.stat, znode.bytes))
  }

  case class Watch[T <: Exists](result: Try[T], update: Future[WatchedEvent]) {
    /** Map this Watch to one of another type. */
    def map[V <: Exists](toV: T => V): Watch[V] = new Watch(result.map(toV), update)
  }

  /** Describes an update to a node's children. */
  case class TreeUpdate(
      parent: ZNode,
      added: Set[ZNode] = Set.empty[ZNode],
      removed: Set[ZNode] = Set.empty[ZNode])
}
