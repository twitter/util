package com.twitter.util

import com.twitter.concurrent.Scheduler
import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.util.control.{NonFatal => NF}


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

  private[this] val TrackElapsedBlocking = new ThreadLocal[Object]()

  /**
   * Enables tracking time spent in blocking operations for the current thread.
   *
   * This should only be enabled for threads where blocking is discouraged.
   *
   * @see [[Scheduler.blockingTimeNanos]] and [[Scheduler.blocking]]
   */
  def enableBlockingTimeTracking(): Unit =
    TrackElapsedBlocking.set(java.lang.Boolean.TRUE)

  /**
   * Disables tracking time spent in blocking operations for the current thread.
   */
  def disableBlockingTimeTracking(): Unit =
    TrackElapsedBlocking.remove()

  /**
   * Whether or not this thread should track time spent in blocking operations.
   *
   * @see [[Scheduler.blockingTimeNanos]] and [[Scheduler.blocking]]
   */
  def getBlockingTimeTracking: Boolean = {
    TrackElapsedBlocking.get() != null
  }

  sealed trait CanAwait {

    /**
     * Should time spent in blocking operations be tracked.
     */
    def trackElapsedBlocking: Boolean = getBlockingTimeTracking
  }
}

/**
 * Synchronously await the result of some action by blocking the current
 * thread.
 *
 * The two main uses of `Await` are (a) you have synchronous code that needs to
 * wait on some action performed by asynchronous code, or (b) you have synchronous code
 * that needs to wait on some action performed on a thread pool or otherwise a different
 * thread.
 *
 * A common type of `Awaitable` is the [[com.twitter.util.Future]].
 *
 * In the context of an event loop (such as when you are on a Finagle thread),
 * never synchronously wait for completion - favoring asynchronous methods such as
 * combinators or callbacks registered on a Future.
 *
 * @define ready
 *
 * Returns the awaitable object itself when the action has completed.
 * Completion of this method merely indicates action completion, regardless
 * of whether it was successful or not. In order to determine whether the action was
 * successful, the awaitable must be queried separately. Prefer using
 * `result()` when you wish failures to be thrown as exceptions.
 *
 * @define result
 *
 * Waits until the action has completed. If the action was successful,
 * returns the result of the action. If the action failed, the corresponding
 * exception representing the failure is thrown.
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

  /**
   * Is this Awaitable ready? In other words: would calling
   * [[com.twitter.util.Awaitable.ready Awaitable.ready]] block?
   */
  def isReady(awaitable: Awaitable[_]): Boolean = awaitable.isReady

  /** $all */
  @throws(classOf[TimeoutException])
  @throws(classOf[InterruptedException])
  @varargs
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

// See https://stackoverflow.com/questions/26643045/java-interoperability-woes-with-scala-generics-and-boxing
private[util] trait CloseAwaitably0[U <: Unit] extends Awaitable[U] {
  private[this] val onClose: Promise[U] = new Promise[U]
  private[this] val closed: AtomicBoolean = new AtomicBoolean(false)

  /**
   * closeAwaitably is intended to be used as a wrapper for
   * `close`. The underlying `f` will be called at most once.
   */
  protected def closeAwaitably(f: => Future[U]): Future[U] = {
    if (closed.compareAndSet(false, true))
      try {
        onClose.become(f)
      } catch {
        case NF(e) =>
          onClose.setException(e)
        case t: Throwable =>
          onClose.setException(t)
          throw t
      }
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
 * Note: There is a Java-friendly API for this trait: `com.twitter.util.AbstractCloseAwaitably`.
 */
trait CloseAwaitably extends CloseAwaitably0[Unit]
