package com.twitter.concurrent

import com.twitter.util.Awaitable.CanAwait
import java.util.ArrayDeque
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.{Level, Logger}
import scala.collection.mutable

/**
 * An interface for scheduling [[java.lang.Runnable]] tasks.
 */
trait Scheduler {

  /**
   * Schedule `r` to be run at some time in the future.
   */
  def submit(r: Runnable): Unit

  /**
   * Flush the schedule. Returns when there is no more
   * work to do.
   */
  def flush(): Unit

  // A note on Hotspot's ThreadMXBean's CPU time. On Linux, this
  // uses clock_gettime[1] which should both be fast and accurate.
  //
  // On OSX, the Mach thread_info call is used.
  //
  // [1] http://linux.die.net/man/3/clock_gettime
  //
  // Note, these clock stats were decommissioned as they only make
  // sense relative to `wallTime` and the tracking error we have
  // experienced `wallTime` and `*Time` make it impossible to
  // use these reliably. It is not worth the performance and
  // code complexity to support them.

  /**
   * The amount of User time that's been scheduled as per ThreadMXBean.
   */
  @deprecated("schedulers no longer export this", "2015-01-10")
  def usrTime: Long = -1L

  /**
   * The amount of CPU time that's been scheduled as per ThreadMXBean.
   */
  @deprecated("schedulers no longer export this", "2015-01-10")
  def cpuTime: Long = -1L

  /**
   * Total walltime spent in the scheduler.
   */
  @deprecated("schedulers no longer export this", "2015-01-10")
  def wallTime: Long = -1L

  /**
   * The number of dispatches performed by this scheduler.
   */
  def numDispatches: Long

  /**
   * Total time spent doing [[blocking]] operations, in nanoseconds.
   *
   * This should only include time spent on threads where
   * [[CanAwait.trackElapsedBlocking]] returns `true`.
   *
   * @return -1 if the [[Scheduler]] does not support tracking this.
   *
   * @note this does not include time spent doing blocking code
   *       outside of [[Scheduler.blocking]]. For example,
   *       `Future(someSlowSynchronousIO)` would not be accounted
   *       for here.
   */
  def blockingTimeNanos: Long = -1L

  /**
   * Executes a function `f` in a blocking fashion.
   *
   * Note: The permit may be removed in the future.
   */
  def blocking[T](f: => T)(implicit perm: CanAwait): T
}

/**
 * A global scheduler.
 */
object Scheduler extends Scheduler {
  @volatile private var self: Scheduler = new LocalScheduler

  def apply(): Scheduler = self

  /**
   * Swap out the current globally-set scheduler with another.
   *
   * Note: This can be unsafe since some schedulers may be active,
   * and flush() can be invoked on the wrong scheduler.
   *
   * This can happen, for example, if a LocalScheduler is used while
   * a future is resolved via Await.
   *
   * @param sched the other Scheduler to swap in for the one that is
   * currently set
   */
  def setUnsafe(sched: Scheduler): Unit = {
    self = sched
  }

  def submit(r: Runnable): Unit = self.submit(r)
  def flush(): Unit = self.flush()

  def numDispatches: Long = self.numDispatches

  override def blockingTimeNanos: Long = self.blockingTimeNanos

  def blocking[T](f: => T)(implicit perm: CanAwait): T = self.blocking(f)
}

private[concurrent] object LocalScheduler {

  val log: Logger = Logger.getLogger(getClass.getSimpleName)

  /** Used to produce a stacktrace to help with finding blocking */
  private[this] class BlockingHere extends Exception

  /**
   * A task-queueing, direct-dispatch scheduler.
   *
   * There is at most one `Activation` instance per Thread,
   * and thus at most only one Thread updating local state, but
   * there can be multiple Threads reading state.
   */
  class Activation(lifo: Boolean, sampleBlockingFraction: Double)
      extends Scheduler
      with Iterator[Runnable] {

    private[this] var r0, r1, r2: Runnable = null
    private[this] val rs = new ArrayDeque[Runnable]
    private[this] var running = false

    @volatile var numDispatches = 0L
    @volatile var blockingNanos = 0L

    override def toString: String = s"Activation(${System.identityHashCode(this)})"

    def submit(r: Runnable): Unit = {
      assert(r != null)

      if (lifo) reorderLifo(r)
      else if (r0 == null) r0 = r
      else if (r1 == null) r1 = r
      else if (r2 == null) r2 = r
      else rs.addLast(r)

      if (!running) {
        numDispatches += 1
        run()
      }
    }

    private[this] final def reorderLifo(r: Runnable): Unit = {
      if (r2 != null) {
        rs.addFirst(r2)
        r2 = r1
        r1 = r0
      } else if (r1 != null) {
        r2 = r1
        r1 = r0
      } else if (r0 != null) {
        r1 = r0
      }
      r0 = r
    }

    def flush(): Unit = {
      if (running) run()
    }

    @inline def hasNext: Boolean = running && r0 != null

    @inline def next(): Runnable = {
      // via moderately silly benchmarking, the
      // queue unrolling gives us a ~50% speedup
      // over pure Queue usage for common
      // situations.

      val r = r0
      r0 = r1
      r1 = r2
      r2 = if (rs.isEmpty) null else rs.removeFirst()
      r
    }

    private[this] def run(): Unit = {
      val save = running
      running = true
      try {
        while (hasNext) next().run()
      } finally {
        running = save
      }
    }

    def blocking[T](f: => T)(implicit perm: CanAwait): T = {
      if (perm.trackElapsedBlocking) {
        val start = System.nanoTime()
        val value = f
        val elapsedNs = System.nanoTime() - start
        blockingNanos += elapsedNs
        if (ThreadLocalRandom.current().nextDouble() < sampleBlockingFraction) {
          val micros = TimeUnit.NANOSECONDS.toMicros(elapsedNs)
          log.log(
            Level.INFO,
            s"Scheduler blocked for $micros micros via the following stacktrace",
            new BlockingHere()
          )
        }
        value
      } else {
        f
      }
    }
  }
}

/**
 * An efficient thread-local, direct-dispatch scheduler.
 */
class LocalScheduler(lifo: Boolean) extends Scheduler {
  def this() = this(false)

  import LocalScheduler.Activation

  // use weak refs to prevent Activations from causing a memory leak
  // thread-safety provided by synchronizing on `activations`
  private[this] val activations = new mutable.WeakHashMap[Activation, Boolean]()

  private[this] val local = new ThreadLocal[Activation]()

  private[this] val sampleBlockingFraction: Double =
    try {
      sys.props.getOrElse("com.twitter.concurrent.schedulerSampleBlockingFraction", "0.0").toDouble
    } catch {
      case _: NumberFormatException => 0.0
    }

  assert(
    sampleBlockingFraction >= 0.0 && sampleBlockingFraction <= 1.0,
    s"sampleBlockingFraction must be between 0 and 1, inclusive: $sampleBlockingFraction"
  )

  override def toString: String = s"LocalScheduler(${System.identityHashCode(this)})"

  private[this] def get(): Activation = {
    val a = local.get()
    if (a != null)
      return a

    val activation = new Activation(lifo, sampleBlockingFraction)
    local.set(activation)
    activations.synchronized {
      activations.put(activation, java.lang.Boolean.TRUE)
    }
    activation
  }

  /** An implementation of Iterator over runnable tasks */
  @inline def hasNext: Boolean = get().hasNext

  /** An implementation of Iterator over runnable tasks */
  @inline def next(): Runnable = get().next()

  // Scheduler implementation:
  def submit(r: Runnable): Unit = get().submit(r)
  def flush(): Unit = get().flush()

  private[this] def activationsSum(f: Activation => Long): Long =
    activations.synchronized {
      activations.keysIterator.map(f).sum
    }

  def numDispatches: Long = activationsSum(_.numDispatches)

  override def blockingTimeNanos: Long = activationsSum(_.blockingNanos)

  def blocking[T](f: => T)(implicit perm: CanAwait): T =
    get().blocking(f)
}

/**
 * A named Scheduler mix-in that causes submitted tasks to be dispatched according to
 * an [[java.util.concurrent.ExecutorService]] created by an abstract factory
 * function.
 */
trait ExecutorScheduler { self: Scheduler =>
  val name: String
  val executorFactory: ThreadFactory => ExecutorService

  protected val threadGroup: ThreadGroup = new ThreadGroup(name)

  protected val threadFactory: ThreadFactory = new ThreadFactory {
    private val n = new AtomicInteger(1)

    def newThread(r: Runnable): Thread = {
      val thread = new Thread(threadGroup, r, name + "-" + n.getAndIncrement())
      thread.setDaemon(true)
      thread
    }
  }

  protected def threads(): Array[Thread] = {
    // We add 2x slop here because it's inherently racy to enumerate
    // threads. Since this is used only for monitoring purposes, we
    // don't try too hard.
    val threads = new Array[Thread](threadGroup.activeCount * 2)
    val n = threadGroup.enumerate(threads)
    threads.take(n)
  }

  protected[this] val executor: ExecutorService = executorFactory(threadFactory)

  def shutdown(): Unit = executor.shutdown()
  def submit(r: Runnable): Unit = executor.execute(r)
  def flush(): Unit = ()

  // Unsupported
  def numDispatches: Long = -1L

  def getExecutor: ExecutorService = executor

  def blocking[T](f: => T)(implicit perm: CanAwait): T = f
}

/**
 * A scheduler that dispatches directly to an underlying Java
 * cached threadpool executor.
 */
class ThreadPoolScheduler(
  val name: String,
  val executorFactory: ThreadFactory => ExecutorService
) extends Scheduler
    with ExecutorScheduler {
  def this(name: String) = this(name, Executors.newCachedThreadPool(_))
}

/**
 * A scheduler that bridges tasks submitted by external threads into local
 * executor threads. All tasks submitted locally are executed on local threads.
 *
 * Note: This scheduler expects to create executors with unbounded capacity.
 * Thus it does not expect and has undefined behavior for any
 * `RejectedExecutionException`s other than those encountered after executor
 * shutdown.
 */
class BridgedThreadPoolScheduler(
  val name: String,
  val executorFactory: ThreadFactory => ExecutorService
) extends Scheduler
    with ExecutorScheduler {
  private[this] val local = new LocalScheduler

  def this(name: String) = this(name, Executors.newCachedThreadPool(_))

  override def submit(r: Runnable): Unit = {
    if (Thread.currentThread.getThreadGroup == threadGroup)
      local.submit(r)
    else
      try executor.execute(new Runnable {
        def run(): Unit = {
          BridgedThreadPoolScheduler.this.submit(r)
        }
      })
      catch {
        case _: RejectedExecutionException => local.submit(r)
      }
  }

  override def flush(): Unit =
    if (Thread.currentThread.getThreadGroup == threadGroup)
      local.flush()
}
