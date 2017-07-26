package com.twitter.zk

import com.twitter.util.{Future, Return, Throw}
import org.apache.zookeeper.KeeperException
import scala.language.implicitConversions

protected[zk] object LiftableFuture {
  implicit def liftableFuture[T](f: Future[T]): LiftableFuture[T] = new LiftableFuture(f)
}

/**
 * Allows Future[T] to be mapped to Future[Try[T]].  This is particularly useful in lifting
 * KeepereException.NoNodeExceptions for ZOp.watch().
 */
protected[zk] class LiftableFuture[T](f: Future[T]) {

  /** Lift a value to a Return. */
  def liftSuccess = f map { Return(_) }

  /** Lift all errors to a Throw */
  def liftFailure = liftSuccess handle { case e => Throw(e) }

  /** Lift all KeeperExceptions to a Throw */
  def liftKeeperException = liftSuccess handle { case e: KeeperException => Throw(e) }

  /** Lift failures when a watch would have been successfully installed */
  def liftNoNode = liftSuccess handle { case e: KeeperException.NoNodeException => Throw(e) }
}
