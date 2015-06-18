package com.twitter.util

import com.twitter.concurrent.Scheduler
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.JavaConverters._


/**
 * Wait for the result of some action. Awaitable is not used
 * directly, but through the `Await` object.
 */
trait Awaitable[+T] {
  import Awaitable._

  /**
   * Support for `Await.ready`. The use of the implicit permit is an
   * access control mechanism: only `Await.ready` may call this
   * method.
   */
  @throws(classOf[TimeoutException])
  @throws(classOf[InterruptedException])
  def ready(timeout: Duration)(implicit permit: CanAwait): this.type

  /**
   * Support for `Await.result`. The use of the implicit permit is an
   * access control mechanism: only `Await.result` may call this
   * method.
   */
  @throws(classOf[Exception])
  def result(timeout: Duration)(implicit permit: CanAwait): T

  /**
   * Is this Awaitable ready? In other words: would calling
   * [[com.twitter.util.Awaitable.ready Awaitable.ready]] block?
   */
  def isReady(implicit permit: CanAwait): Boolean
}

object Awaitable {
  sealed trait CanAwait
}

/**
 * Await the result of some action.
 *
 * @define ready
 *
 * Returns the object when the action has completed.
 *
 * @define result
 *
 * Returns the result of the action when it has completed.
 *
 * If you want the results as a [[com.twitter.util.Try]],
 * use `Await.result(future.liftToTry)`.
 *
 * @define all
 *
 * Returns after all actions have completed. The timeout given is
 * passed to each awaitable in turn, meaning max await time will be
 * awaitables.size * timeout.
 */
object Await {
  import Awaitable._
  private implicit object AwaitPermit extends CanAwait

  /** $ready */
  @throws(classOf[TimeoutException])
  @throws(classOf[InterruptedException])
  def ready[T <: Awaitable[_]](awaitable: T): T =
    ready(awaitable, Duration.Top)

  /**
   * $ready
   *
   * If the `Awaitable` is not ready within `timeout`, a
   * [[com.twitter.util.TimeoutException]] will be thrown.
   */
  @throws(classOf[TimeoutException])
  @throws(classOf[InterruptedException])
  def ready[T <: Awaitable[_]](awaitable: T, timeout: Duration): T = {
    if (awaitable.isReady) awaitable.ready(timeout)
    else Scheduler.blocking { awaitable.ready(timeout) }
  }

  /** $result */
  @throws(classOf[Exception])
  def result[T](awaitable: Awaitable[T]): T =
    result(awaitable, Duration.Top)

  /**
   * $result
   *
   * If the `Awaitable` is not ready within `timeout`, a
   * [[com.twitter.util.TimeoutException]] will be thrown.
   */
  @throws(classOf[Exception])
  def result[T](awaitable: Awaitable[T], timeout: Duration): T =
    if (awaitable.isReady) awaitable.result(timeout)
    else Scheduler.blocking { awaitable.result(timeout) }

  /** $all */
  @throws(classOf[TimeoutException])
  @throws(classOf[InterruptedException])
  @scala.annotation.varargs
  def all(awaitables: Awaitable[_]*): Unit =
    all(awaitables, Duration.Top)

  /**
   * $all
   *
   * If any of the `Awaitable`s are not ready within `timeout`, a
   * [[com.twitter.util.TimeoutException]] will be thrown.
   */
  @throws(classOf[TimeoutException])
  @throws(classOf[InterruptedException])
  def all(awaitables: Seq[Awaitable[_]], timeout: Duration): Unit =
    awaitables foreach { _.ready(timeout) }

  /**
   * $all
   *
   * @see Await.all(Seq, Duration)
   */
  @throws(classOf[TimeoutException])
  @throws(classOf[InterruptedException])
  def all(awaitables: java.util.Collection[Awaitable[_]], timeout: Duration): Unit =
    all(awaitables.asScala.toSeq, timeout)
}

// See http://stackoverflow.com/questions/26643045/java-interoperability-woes-with-scala-generics-and-boxing
private[util] trait CloseAwaitably0[U <: Unit] extends Awaitable[U] {
  private[this] val onClose = new Promise[U]
  private[this] val closed = new AtomicBoolean(false)

  /**
   * closeAwaitably is intended to be used as a wrapper for
   * `close`. The underlying `f` will be called at most once.
   */
  protected def closeAwaitably(f: => Future[U]): Future[U] = {
    if (closed.compareAndSet(false, true))
      onClose.become(f)
    onClose
  }

  def ready(timeout: Duration)(implicit permit: Awaitable.CanAwait): this.type = {
    onClose.ready(timeout)
    this
  }

  def result(timeout: Duration)(implicit permit: Awaitable.CanAwait): U =
    onClose.result(timeout)

  def isReady(implicit permit: Awaitable.CanAwait): Boolean =
    onClose.isReady
}

/**
 * A mixin to make an [[com.twitter.util.Awaitable]] out
 * of a [[com.twitter.util.Closable]].
 *
 * Use `closeAwaitably` in the definition of `close`:
 *
 * {{{
 * class MyClosable extends Closable with CloseAwaitably {
 *   def close(deadline: Time) = closeAwaitably {
 *     // close the resource
 *   }
 * }
 * }}}
 *
 * Note: There is a Java-friendly API for this trait: [[com.twitter.util.AbstractCloseAwaitably]].
 */
trait CloseAwaitably extends CloseAwaitably0[Unit]