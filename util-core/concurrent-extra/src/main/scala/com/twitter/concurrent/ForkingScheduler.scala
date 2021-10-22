package com.twitter.concurrent

import com.twitter.util.Future
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

/**
 * A scheduler that provides methods to fork the execution of
 * `Future` computations and behaves similarly to a thread pool
 */
trait ForkingScheduler extends Scheduler {

  /**
   * Forks the execution of a `Future` computation even if
   * the scheduler is overloaded.
   *
   * @param f the Future computation to be forked
   * @tparam T the type of the `Future` computation
   * @return a `Future` to be satisfied when the forked `Future`
   *         is completed
   */
  def fork[T](f: => Future[T]): Future[T]

  /**
   * Tries to fork the execution of a `Future` computation and
   * returns empty in case the scheduler is overloaded.
   *
   * @param f the Future computation to be forked
   * @tparam T the type of the `Future` computation
   * @return A `Future` with `None` if the scheduler is overloaded
   *         or `Some[T]` if the task is successfully forked and
   *         executed.
   */
  def tryFork[T](f: => Future[T]): Future[Option[T]]

  /**
   * Forks the execution of a `Future` computation using the
   * provided executor.
   *
   * @param executor the executor to be used to run the `Future`
   *                 computation
   * @param f the `Future` computation to be forked
   * @tparam T the type of the `Future` computation
   * @return a `Future` to be satisfied when the forked `Future`
   *         is completed
   */
  def fork[T](executor: Executor)(f: => Future[T]): Future[T]

  /**
   * Creates an `ExecutorService` wrapper for this forking
   * scheduler. The original forking scheduler remains
   * unchanged and is used as the underlying implementation
   * of the returned executor. If the executor service is
   * shut down, only the wrapper is shut down and the original
   * forking scheduler continues operating.
   *
   * @return the executor service wrapper
   */
  def asExecutorService(): ExecutorService

  /**
   * Indicates if `FuturePool`s should have their tasks
   * redirected to the forking scheduler.
   *
   * @return if enabled
   */
  def redirectFuturePools(): Boolean
}

object ForkingScheduler {

  /**
   * Returns empty if the current scheduler isn't a forking
   * scheduler. Returns the forking scheduler otherwise.
   * @return an optional of the forking scheduler
   */
  def apply(): Option[ForkingScheduler] =
    Scheduler() match {
      case f: ForkingScheduler =>
        Some(f)
      case _ =>
        None
    }
}
