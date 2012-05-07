package com.twitter.zk

import com.twitter.conversions.time._
import com.twitter.util.{Duration, Future, Timer}
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

  /**
   *  Retries an operation indefinitely until success, with a delay that increases exponentially.
   *
   *  @param base   initial value that is multiplied by factor every time; must be > 0
   *  @param factor must be >= 1 so the retries do not become more aggressive
   */
  case class Exponential(
    base: Duration,
    factor: Double = 2.0,
    maximum: Duration = 30.seconds
  )(implicit timer: Timer) extends RetryPolicy {
    require(base > 0)
    require(factor >= 1)

    def apply[T](op: => Future[T]): Future[T] = {
      def retry(delay: Duration): Future[T] = {
        op rescue { case KeeperConnectionException(_) =>
          timer.doLater(delay) {
            retry((delay.inNanoseconds * factor).toLong.nanoseconds min maximum)
          }.flatten
        }
      }
      retry(base)
    }
  }

  /** A single try */
  object None extends RetryPolicy {
    def apply[T](op: => Future[T]) = op
  }
}
