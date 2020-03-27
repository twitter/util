package com.twitter.zk.coordination

import com.twitter.concurrent.Permit
import com.twitter.util.{Future, Promise, Return, Throw}
import com.twitter.zk.{StateEvent, ZkClient, ZNode}
import java.nio.charset.Charset
import java.util.concurrent.{ConcurrentLinkedQueue, RejectedExecutionException}
import org.apache.zookeeper.KeeperException.NoNodeException
import org.apache.zookeeper.{CreateMode, KeeperException}

/**
 * ZkAsyncSemaphore is a distributed semaphore with asynchronous execution.
 * Grabbing a permit constitutes a vote on the number of permits the semaphore
 * can permit and returns a Future[Permit]. If consensus on the number of permits
 * is lost, an exception is raised when acquiring a permit (so expect it).
 *
 * Care must be taken to handle zookeeper client session expiry. A ZkAsyncSemaphore cannot
 * be used after the zookeeper session has expired. Likewise, any permits acquired
 * via the session must be considered invalid. Additionally, it is the client's responsibility
 * to determine if a permit is still valid in the case that the zookeeper client becomes
 * disconnected.
 *
 * Attempts to clone AsyncSemaphore
 *
 * Ex.
 * {{{
 * implicit val timer = new JavaTimer(true)
 * val connector = NativeConnector("localhost:2181", 5.seconds, 10.minutes)
 * val zk = ZkClient(connector).withRetryPolicy(RetryPolicy.Basic(3))
 * val path = "/testing/twitter/service/charm/shards"
 * val sem = new ZkAsyncSemaphore(zk, path, 4)
 *
 * sem.acquire flatMap { permit =>
 *   Future { ... } ensure { permit.release }
 * } // handle { ... }
 * }}}
 */
class ZkAsyncSemaphore(
  zk: ZkClient,
  path: String,
  numPermits: Int,
  maxWaiters: Option[Int] = None) {
  import ZkAsyncSemaphore._
  require(numPermits > 0)
  require(maxWaiters.getOrElse(0) >= 0)

  private[this] val pathSeparator = "/"
  private[this] val permitPrefix = "permit-"
  private[this] val permitNodePathPrefix = Seq(path, permitPrefix).mkString(pathSeparator)
  private[this] val futureSemaphoreNode = createSemaphoreNode()
  private[this] val waitq = new ConcurrentLinkedQueue[(Promise[ZkSemaphorePermit], ZNode)]

  private[this] class ZkSemaphorePermit(node: ZNode) extends Permit {
    val zkPath: String = node.path
    val sequenceNumber: Int = sequenceNumberOf(zkPath)
    override def release(): Unit = {
      node.delete()
    }
  }

  @volatile
  var numWaiters: Int = 0
  @volatile
  var numPermitsAvailable: Int = numPermits

  def acquire(): Future[Permit] = synchronized {
    val futurePermit = futureSemaphoreNode flatMap { semaphoreNode =>
      zk(permitNodePathPrefix).create(
        data = numPermits.toString.getBytes(Charset.forName("UTF8")),
        mode = CreateMode.EPHEMERAL_SEQUENTIAL
      )
    }
    futurePermit flatMap { permitNode =>
      val mySequenceNumber = sequenceNumberOf(permitNode.path)

      permitNodes() flatMap { permits =>
        val sequenceNumbers = permits map { child => sequenceNumberOf(child.path) }
        getConsensusNumPermits(permits) flatMap { consensusNumPermits =>
          if (consensusNumPermits != numPermits) {
            throw ZkAsyncSemaphore.PermitMismatchException(
              "Attempted to create semaphore of %d permits when consensus is %d"
                .format(numPermits, consensusNumPermits)
            )
          }
          if (permits.size < numPermits) {
            Future.value(new ZkSemaphorePermit(permitNode))
          } else if (mySequenceNumber <= sequenceNumbers(numPermits - 1)) {
            Future.value(new ZkSemaphorePermit(permitNode))
          } else {
            maxWaiters match {
              case Some(max) if (waitq.size >= max) => {
                MaxWaitersExceededException
              }
              case _ => {
                val promise = new Promise[ZkSemaphorePermit]
                waitq.add((promise, permitNode))
                promise
              }
            }
          }
        } onFailure {
          case err => {
            permitNode.delete()
            Future.exception(err)
          }
        }
      }
    }
  }

  /**
   * Create the zookeeper path for this semaphore and set up handlers for all client events:
   * - Monitor the tree for changes (for handling waiters, updating accounting)
   * - Reject all current waiters if our session expires.
   * - Check waiters if the client has reconnected and our session is still valid.
   */
  private[this] def createSemaphoreNode(): Future[ZNode] = {
    safeCreate(path) map { semaphoreNode =>
      zk() map { client =>
        // client is always connected here.
        monitorSemaphore(semaphoreNode)
        zk onSessionEvent {
          case StateEvent.Expired => rejectWaitQueue()
          case StateEvent.Connected => {
            permitNodes() map { nodes => checkWaiters(nodes) }
            monitorSemaphore(semaphoreNode)
          }
        }
      }
      semaphoreNode
    }
  }

  /**
   * Create intermediate zookeeper nodes as required so that the specified path exists.
   *
   * @param path The zookeeper node path to create.
   * @return A Future ZNode that is satisfied when the full path exists.
   */
  private[this] def safeCreate(path: String): Future[ZNode] = {
    val nodes = path.split(pathSeparator) filter { !_.isEmpty }
    val head = Future.value(zk(pathSeparator + nodes.head))
    nodes.tail.foldLeft(head) { (futureParent, child) =>
      futureParent flatMap { parent =>
        val newParent = parent(child)
        newParent.create() rescue {
          case err: KeeperException.NodeExistsException => Future.value(newParent)
        }
      }
    }
  }

  /**
   * Return ''all'' permit requests for this semaphore.
   *
   * @return A Future sequence of ZNodes that exist for this semaphore (each a request for a Permit)
   *         The sequence includes both nodes that have entered the semaphore as well as waiters.
   */
  private[this] def permitNodes(): Future[Seq[ZNode]] = {
    futureSemaphoreNode.flatMap { semaphoreNode =>
      semaphoreNode.getChildren() map { zop =>
        zop.children.toSeq filter { child =>
          child.path.startsWith(permitNodePathPrefix)
        } sortBy (child => sequenceNumberOf(child.path))
      }
    }
  }

  /**
   * Continuously monitor the semaphore node for changes. If there are permit promises in the waitq,
   * check if the earliest can be fulfilled. Assumes the monitor cycles once per change (does not
   * coalesce) and therefore needs only check head of queue.
   *
   * @param node The semaphore node parent of the permit children to monitor.
   */
  private[this] def monitorSemaphore(node: ZNode) = {
    val monitor = node.getChildren.monitor()
    monitor foreach { tryChildren => tryChildren map { zop => checkWaiters(zop.children.toSeq) } }
  }

  /**
   * Check the wait queue for waiters the can be satisfied with a Permit.
   *
   * @param nodes A sequence of ZNodes to check the wait queue against.
   */
  private[this] def checkWaiters(nodes: Seq[ZNode]) = {
    nodes.size match {
      case length if length <= numPermits => {
        numPermitsAvailable = numPermits - length
        numWaiters = 0
      }
      case length => {
        numPermitsAvailable = 0
        numWaiters = length - numPermits
      }
    }
    val permits = nodes filter { child =>
      child.path.startsWith(permitNodePathPrefix)
    } sortBy (child => sequenceNumberOf(child.path))
    val ids = permits map { child => sequenceNumberOf(child.path) }
    val waitqIterator = waitq.iterator()
    while (waitqIterator.hasNext) {
      val (promise, permitNode) = waitqIterator.next()
      val id = sequenceNumberOf(permitNode.path)
      if (!permits.contains(permitNode)) {
        promise.setException(
          PermitNodeException(
            "Node for this permit has been deleted (client released, session expired, or tree was clobbered)."
          )
        )
      } else if (permits.size < numPermits) {
        promise.setValue(new ZkSemaphorePermit(permitNode))
        waitqIterator.remove()
      } else if (id <= ids(numPermits - 1)) {
        promise.setValue(new ZkSemaphorePermit(permitNode))
        waitqIterator.remove()
      }
    }
  }

  /**
   * Reject all waiting requests for permits. This must be done when the zookeeper client session
   * has expired.
   */
  private[this] def rejectWaitQueue() = {
    val waitqIterator = waitq.iterator()
    while (waitqIterator.hasNext) {
      val (promise, _) = waitqIterator.next()
      waitqIterator.remove()
      promise.setException(PermitNodeException("ZooKeeper client session expired."))
    }
  }

  /**
   * Determine what the consensus of clients believe numPermits should be. The '''data''' section for
   * each node in {{{permits}}} must contain a UTF-8 string representation of an integer, specifying
   * the value of numPermits the client's semaphore instance was created with. These are considered
   * votes for the consensus on numPermits. An exception is thrown if there is no consensus (two leading
   * groups with the same cardinality).
   *
   * @param permits A sequence of ZNodes used to vote on the number of Permits that this semaphore
   *                will provide.
   * @return A Future Int representing the number of Permits that may be provided by this semaphore.
   * @throws LackOfConsensusException When there is no consensus.
   */
  private[this] def getConsensusNumPermits(permits: Seq[ZNode]): Future[Int] = {
    Future.collect(permits map numPermitsOf) map { purportedNumPermits =>
      val groupedByNumPermits = purportedNumPermits filter { i =>
        0 < i
      } groupBy { i => i }
      val permitsToBelievers = groupedByNumPermits map {
        case (permits, believers) => (permits, believers.size)
      }
      val (numPermitsInMax, numBelieversOfMax) = permitsToBelievers.maxBy {
        case (_, believers) => believers
      }
      val cardinalityOfMax = permitsToBelievers.values.count(_ == numBelieversOfMax)

      if (cardinalityOfMax == 1) {
        // Consensus
        numPermitsInMax
      } else {
        // No consensus or this vote breaks consensus (two votes in discord)
        throw LackOfConsensusException(
          "Cannot create semaphore with %d permits. Loss of consensus on %d permits."
            .format(numPermits, numPermitsInMax)
        )
      }
    }
  }

  private[this] def sequenceNumberOf(path: String): Int = {
    if (!path.startsWith(permitNodePathPrefix))
      throw new Exception("Path does not match the permit node prefix")
    path.substring(permitNodePathPrefix.length).toInt
  }

  private[coordination] def numPermitsOf(node: ZNode): Future[Int] = {
    node.getData().transform {
      case Return(data: ZNode.Data) =>
        try {
          Future.value(new String(data.bytes, Charset.forName("UTF8")).toInt)
        } catch {
          case err: NumberFormatException => Future.value(-1)
        }
      case Throw(t: NoNodeException) =>
        // This permit was released (i.e. after we got the list of permits).
        Future.value(-1)
      case Throw(t) => Future.exception(t)
    }
  }

}

object ZkAsyncSemaphore {
  case class LackOfConsensusException(msg: String) extends Exception(msg)
  case class PermitMismatchException(msg: String) extends Exception(msg)
  case class PermitNodeException(msg: String) extends Exception(msg)
  private val MaxWaitersExceededException =
    Future.exception(new RejectedExecutionException("Max waiters exceeded"))
}
