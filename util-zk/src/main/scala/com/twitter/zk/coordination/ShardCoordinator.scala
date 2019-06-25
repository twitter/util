package com.twitter.zk.coordination

import java.util.concurrent.RejectedExecutionException

import org.apache.zookeeper.{CreateMode, KeeperException}

import com.twitter.concurrent.Permit
import com.twitter.util.Future
import com.twitter.zk.coordination.ZkAsyncSemaphore.{
  LackOfConsensusException,
  PermitMismatchException,
  PermitNodeException
}
import com.twitter.zk.{ZNode, ZkClient}

object ShardCoordinator {
  case class SemaphoreError(err: Throwable)
      extends Exception("Exception from underlying semaphore.", err)
}

/**
 * A rudimentary shard/partition coordinator. Provides ShardPermits by lowest available
 * ID first (linear scan).
 *
 * {{{
 * val shardCoordinator = new ShardCoordinator(zkClient, "/testing/twitter/service/something/shards", numShards)
 * log.trace("Waiting for shard permit...")
 * shardCoordinator.acquire flatMap { shard =>
 *   log.trace("Working as shard %d", shard.id)
 *   { // inside some Future
 *     if (Hashing.consistentHash(item, numShards) == shard.id) action
 *   } ensure { shard.release }
 * }
 * }}}
 */
class ShardCoordinator(zk: ZkClient, path: String, numShards: Int) {
  import ShardCoordinator._
  require(numShards > 0)

  private[this] val separator = "/"
  private[this] val semaphorePath = Seq(path, "sem").mkString(separator)
  private[this] val shardPathPrefix = Seq(path, "shard-").mkString(separator)
  private[this] val semaphore = new ZkAsyncSemaphore(zk, semaphorePath, numShards)

  /**
   * Acquire a permit for a shard (ShardPermit) asynchronously. A ShardPermit contains
   * a zero-indexed shard ID. Be sure to call release() on the ShardPermit when your
   * client is finished performing work for the shard.
   *
   * @return A Future of ShardPermit that is satisfied when a shard slot becomes available.
   * @throws SemaphoreError when the underlying semaphore throws an exception.
   * @throws RejectedExecutionException when a shard cannot be acquired due to an unexpected
   *                                    state in the zookeeper tree. Assumed to only happen if some
   *                                    zookeeper client clobbers the tree location for this
   *                                    ShardCoordinator.
   */
  def acquire(): Future[ShardPermit] = {
    semaphore.acquire flatMap { permit =>
      shardNodes() map { nodes =>
        nodes map { node =>
          shardIdOf(node.path)
        }
      } map { ids =>
        (0 until numShards) filterNot { ids contains _ }
      } flatMap { availableIds =>
        // Iteratively (brute force) attempt to create a node for the next lowest available ID until
        // a Shard is successfully created (race resolution).
        availableIds.tail.foldLeft(createShardNode(availableIds.head, permit)) {
          (futureShardOption, id) =>
            futureShardOption flatMap { shardOption =>
              shardOption match {
                case Some(shard) => Future.value(shardOption)
                case None => createShardNode(id, permit)
              }
            }
        }
      } flatMap { shardOption =>
        shardOption map { Future.value(_) } getOrElse {
          Future.exception(
            new RejectedExecutionException("Could not get a shard, polluted zk tree?")
          )
        }
      } rescue {
        case err: LackOfConsensusException => Future.exception(SemaphoreError(err))
        case err: PermitMismatchException => Future.exception(SemaphoreError(err))
        case err: PermitNodeException => Future.exception(SemaphoreError(err))
      } onFailure { err =>
        permit.release()
      }
    }
  }

  private[this] def createShardNode(id: Int, permit: Permit): Future[Option[Shard]] = {
    zk(shardPath(id)).create(mode = CreateMode.EPHEMERAL) map { node =>
      Some(Shard(id, node, permit))
    } handle {
      case err: KeeperException.NodeExistsException => None
    }
  }

  private[this] def shardNodes(): Future[Seq[ZNode]] = {
    zk(path).getChildren() map { zop =>
      (zop.children filter { child =>
        child.path.startsWith(shardPathPrefix)
      } sortBy (child => shardIdOf(child.path))).toSeq
    }
  }

  private[this] def shardIdOf(path: String): Int = {
    path.substring(shardPathPrefix.length).toInt
  }

  private[this] def shardPath(id: Int) = Seq(path, "shard-" + id).mkString(separator)

}

sealed trait ShardPermit {
  val id: Int
  def release(): Unit
}

case class Shard(id: Int, private val node: ZNode, private val permit: Permit) extends ShardPermit {

  def release() = {
    node.delete() ensure { permit.release() }
  }
}
