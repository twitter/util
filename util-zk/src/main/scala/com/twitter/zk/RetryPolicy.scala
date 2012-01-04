package com.twitter.zk

import com.twitter.util.Future
import org.apache.zookeeper.KeeperException

/** Pluggable retry strategy. */
trait RetryPolicy {
  def apply[T](op: => Future[T]): Future[T]
}

/** Matcher for connection-related KeeperExceptions. */
object KeeperConnectionException {
  def unapply(e: KeeperException) = e match {
    case e: KeeperException.ConnectionLossException => Some(e)
    case e: KeeperException.SessionExpiredException => Some(e)
    case e: KeeperException.SessionMovedException => Some(e)
    case e: KeeperException.OperationTimeoutException => Some(e)
    case e => None
  }
}

object RetryPolicy {
  /** Retries an operation a fixed number of times without back-off. */
  case class Basic(retries: Int) extends RetryPolicy {
    def apply[T](op: => Future[T]): Future[T] = {
      def retry(tries: Int): Future[T] = {
        op rescue { case KeeperConnectionException(_) if (tries > 0) =>
          retry(tries - 1)
        }
      }
      retry(retries)
    }
  }

  /** A single try */
  object None extends RetryPolicy {
    def apply[T](op: => Future[T]) = op
  }
}
