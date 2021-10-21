package com.twitter.util

import com.twitter.concurrent.ForkingScheduler
import com.twitter.concurrent.NamedPoolThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{Future => _, _}
import scala.runtime.NonLocalReturnControl

/**
 * A `FuturePool` executes tasks asynchronously, typically using a pool
 * of worker threads.
 */
trait FuturePool {

  /**
   * Execute `f`, returning a [[Future]] that represents the outcome.
   */
  def apply[T](f: => T): Future[T]

  /**
   * The approximate size of the pool.
   *
   * While this is implementation specific, this often represents
   * the number of threads in the pool.
   *
   * @return -1 if this [[FuturePool]] implementation doesn't support this statistic.
   */
  def poolSize: Int = -1

  /**
   * The approximate number of tasks actively executing.
   *
   * @return -1 if this [[FuturePool]] implementation doesn't support this statistic.
   */
  def numActiveTasks: Int = -1

  /**
   * The approximate number of tasks that have completed execution.
   *
   * @return -1 if this [[FuturePool]] implementation doesn't support this statistic.
   */
  def numCompletedTasks: Long = -1L

  /**
   * The approximate number of tasks that are pending (typically waiting in the queue).
   *
   * @return -1 if this [[FuturePool]] implementation doesn't support this statistic.
   */
  def numPendingTasks: Long = -1
}

/**
 * Note: There is a Java-friendly API for this object: [[com.twitter.util.FuturePools]].
 */
object FuturePool {

  /**
   * Creates a [[FuturePool]] backed by an `java.util.concurrent.ExecutorService`.
   */
  def apply(executor: ExecutorService): ExecutorServiceFuturePool =
    new ExecutorServiceFuturePool(executor)

  /**
   * Creates a [[FuturePool]] backed by an `java.util.concurrent.ExecutorService`
   * which propagates cancellation.
   */
  def interruptible(executor: ExecutorService): ExecutorServiceFuturePool =
    new InterruptibleExecutorServiceFuturePool(executor)

  /**
   * A [[FuturePool]] that really isn't; it executes tasks immediately
   * without waiting.  This can be useful in unit tests.
   */
  val immediatePool: FuturePool = new FuturePool {
    def apply[T](f: => T): Future[T] = Future(f)

    override def toString: String = "FuturePool.immediatePool"
  }

  private[twitter] lazy val defaultExecutor = Executors.newCachedThreadPool(
    new NamedPoolThreadFactory("UnboundedFuturePool", makeDaemons = true)
  )

  /**
   * The default future pool, using a cached threadpool, provided by
   * `java.util.concurrent.Executors.newCachedThreadPool`. Note
   * that this is intended for IO concurrency; computational
   * parallelism typically requires special treatment. If an interrupt
   * is raised on a returned Future and the work has started, the worker
   * thread will not be interrupted.
   */
  lazy val unboundedPool: FuturePool =
    new ExecutorServiceFuturePool(defaultExecutor) {
      override def toString: String =
        s"FuturePool.unboundedPool($defaultExecutor)"
    }

  /**
   * The default future pool, using a cached threadpool, provided by
   * `java.util.concurrent.Executors.newCachedThreadPool`. Note
   * that this is intended for IO concurrency; computational
   * parallelism typically requires special treatment.  If an interrupt
   * is raised on a returned Future and the work has started, an attempt
   * will be made to interrupt the worker thread.
   */
  lazy val interruptibleUnboundedPool: FuturePool =
    new InterruptibleExecutorServiceFuturePool(defaultExecutor) {
      override def toString: String =
        s"FuturePool.interruptibleUnboundedPool($defaultExecutor)"
    }
}

/**
 * A [[FuturePool]] backed by a `java.util.concurrent.ExecutorService`
 * that supports cancellation.
 */
class InterruptibleExecutorServiceFuturePool(executor: ExecutorService)
    extends ExecutorServiceFuturePool(executor, true)

/**
 * A [[FuturePool]] implementation backed by an `java.util.concurrent.ExecutorService`.
 *
 * If a piece of work has started, it cannot be cancelled and will not propagate
 * cancellation unless `interruptible` is true. If you want to propagate cancellation,
 * use an [[InterruptibleExecutorServiceFuturePool]].
 */
class ExecutorServiceFuturePool protected[this] (
  val executor: ExecutorService,
  val interruptible: Boolean)
    extends FuturePool {
  def this(executor: ExecutorService) = this(executor, false)

  /**
   * Not null if the current scheduler supports forking and indicates that
   * `FuturePool`s should be redirected to it. If the provided executor is a
   * `ThreadPoolExecutor`, the max concurrency of the forking scheduler is
   * set to the size of the thread pool, maintaining a similar concurrency behavior
   */
  private[this] lazy val forkingScheduler: ForkingScheduler = {
    ForkingScheduler()
      .filter(_.redirectFuturePools())
      .getOrElse(null)
  }

  def apply[T](f: => T): Future[T] = {
    if (forkingScheduler != null) {
      forkingScheduler.fork(Future.value(f))
    } else {
      val runOk = new AtomicBoolean(true)
      val p = new Promise[T]
      val task = new Runnable {
        private[this] val saved = Local.save()

        def run(): Unit = {
          // Make an effort to skip work in the case the promise
          // has been cancelled or already defined.
          if (!runOk.compareAndSet(true, false))
            return

          val current = Local.save()
          Local.restore(saved)

          try p.updateIfEmpty(Try(f))
          catch {
            case nlrc: NonLocalReturnControl[_] =>
              val fnlrc = new FutureNonLocalReturnControl(nlrc)
              p.updateIfEmpty(Throw(fnlrc))
              throw fnlrc
            case e: Throwable =>
              p.updateIfEmpty(Throw(new ExecutionException(e)))
              throw e
          } finally Local.restore(current)
        }
      }

      // This is safe: the only thing that can call task.run() is
      // executor, the only thing that can raise an interrupt is the
      // receiver of this value, which will then be fully initialized.
      val javaFuture =
        try executor.submit(task)
        catch {
          case e: RejectedExecutionException =>
            runOk.set(false)
            p.setException(e)
            null
        }

      p.setInterruptHandler {
        case cause =>
          if (interruptible || runOk.compareAndSet(true, false)) {
            if (p.updateIfEmpty(Throw(cause)))
              javaFuture.cancel(true)
          }
      }

      p
    }
  }

  override def toString: String =
    s"ExecutorServiceFuturePool(interruptible=$interruptible, executor=$executor, forkingScheduler=$forkingScheduler)"

  override def poolSize: Int = executor match {
    case tpe: ThreadPoolExecutor => tpe.getPoolSize
    case _ => super.poolSize
  }
  override def numActiveTasks: Int = executor match {
    case tpe: ThreadPoolExecutor => tpe.getActiveCount
    case _ => super.numActiveTasks
  }
  override def numCompletedTasks: Long = executor match {
    case tpe: ThreadPoolExecutor => tpe.getCompletedTaskCount
    case _ => super.numCompletedTasks
  }

  override def numPendingTasks: Long = executor match {
    case tpe: ThreadPoolExecutor => tpe.getQueue.size()
    case _ => super.numPendingTasks
  }
}
