package com.twitter.concurrent

import com.twitter.util.Awaitable.CanAwait
import java.lang.management.ManagementFactory
import java.util.ArrayDeque
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger
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

  /**
   * The amount of User time that's been scheduled as per ThreadMXBean.
   */
  def usrTime: Long

  /**
   * The amount of CPU time that's been scheduled as per ThreadMXBean.
   */
  def cpuTime: Long

  /**
   * Total walltime spent in the scheduler.
   */
  def wallTime: Long

  /**
   * The number of dispatches performed by this scheduler.
   */
  def numDispatches: Long

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

  def submit(r: Runnable) = self.submit(r)
  def flush() = self.flush()
  def usrTime = self.usrTime
  def cpuTime = self.cpuTime
  def wallTime = self.wallTime
  def numDispatches = self.numDispatches

  def blocking[T](f: => T)(implicit perm: CanAwait) = self.blocking(f)
}

/**
 * An efficient thread-local, direct-dispatch scheduler.
 */
class LocalScheduler(lifo: Boolean) extends Scheduler {
  def this() = this(false)

  private[this] val SampleScale = 1000
  private[this] val bean = ManagementFactory.getThreadMXBean()
  private[this] val cpuTimeSupported = bean.isCurrentThreadCpuTimeSupported()

  // use weak refs to prevent Activations from causing a memory leak
  // thread-safety provided by synchronizing on `activations`
  private[this] val activations = new mutable.WeakHashMap[Activation, Boolean]()

  private[this] val local = new ThreadLocal[Activation] {
    override def initialValue = null
  }

  /**
   * A task-queueing, direct-dispatch scheduler
   */
  private class Activation extends Scheduler with Iterator[Runnable] {
    private[this] var r0, r1, r2: Runnable = null
    private[this] val rs = new ArrayDeque[Runnable]
    private[this] var running = false
    private[this] val rng = ThreadLocalRandom.current()

    // This is safe: there's only one updater.
    @volatile var usrTime = 0L
    @volatile var cpuTime = 0L
    @volatile var wallTime = 0L
    @volatile var numDispatches = 0L

    def submit(r: Runnable): Unit = {
      assert(r != null)

      if (lifo) reorderLifo(r)
      else if (r0 == null) r0 = r
      else if (r1 == null) r1 = r
      else if (r2 == null) r2 = r
      else rs.addLast(r)

      if (!running) {
        if (cpuTimeSupported && rng.nextInt(SampleScale) == 0)
          instrumentAndRun()
        else
          run()
      }
    }

    private[this] final def instrumentAndRun(): Unit = {
      numDispatches += SampleScale
      val cpu0 = bean.getCurrentThreadCpuTime
      val usr0 = bean.getCurrentThreadUserTime
      val wall0 = System.nanoTime()
      run()
      cpuTime += (bean.getCurrentThreadCpuTime - cpu0) * SampleScale
      usrTime += (bean.getCurrentThreadUserTime - usr0) * SampleScale
      wallTime += (System.nanoTime() - wall0) * SampleScale
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
        while (hasNext)
          next().run()
      } finally {
        running = save
      }
    }

    def blocking[T](f: => T)(implicit perm: CanAwait): T = f
  }

  private[this] def get(): Activation = {
    val a = local.get()
    if (a != null)
      return a

    val activation = new Activation()
    local.set(activation)
    activations.synchronized {
      activations.put(activation, java.lang.Boolean.TRUE)
    }
    activation
  }

  /** An implementaiton of Iterator over runnable tasks */
  @inline def hasNext: Boolean = get().hasNext

  /** An implementaiton of Iterator over runnable tasks */
  @inline def next(): Runnable = get().next()

  // Scheduler implementation:
  def submit(r: Runnable): Unit = get().submit(r)
  def flush(): Unit = get().flush()

  private[this] def activationsSum(f: Activation => Long): Long =
    activations.synchronized {
      activations.keysIterator.map(f).sum
    }

  def usrTime: Long = activationsSum(_.usrTime)
  def cpuTime: Long = activationsSum(_.cpuTime)
  def wallTime: Long = activationsSum(_.wallTime)
  def numDispatches: Long = activationsSum(_.numDispatches)

  def blocking[T](f: => T)(implicit perm: CanAwait): T = f
}

/**
 * A named Scheduler mix-in that causes submitted tasks to be dispatched according to
 * an [[java.util.concurrent.ExecutorService]] created by an abstract factory
 * function.
 */
trait ExecutorScheduler { self: Scheduler =>
  val name: String
  val executorFactory: ThreadFactory => ExecutorService

  protected[this] val bean = ManagementFactory.getThreadMXBean()
  protected val threadGroup: ThreadGroup = new ThreadGroup(name)
  @volatile private[this] var threads = Set[Thread]()

  protected val threadFactory = new ThreadFactory {
    private val n = new AtomicInteger(1)

    def newThread(r: Runnable) = {
      val thread = new Thread(threadGroup, r, name + "-" + n.getAndIncrement())
      thread.setDaemon(true)
      thread
    }
  }

  protected def threads(): Array[Thread] = {
    // We add 2x slop here because it's inherently racy to enumerate
    // threads. Since this is used only for monitoring purposes, we
    // don't try too hard.
    val threads = new Array[Thread](threadGroup.activeCount*2)
    val n = threadGroup.enumerate(threads)
    threads take n
  }

  protected[this] val executor = executorFactory(threadFactory)

  def shutdown() { executor.shutdown() }
  def submit(r: Runnable) { executor.execute(r) }
  def flush() = ()
  def usrTime = {
    var sum = 0L
    for (t <- threads()) {
      val time = bean.getThreadUserTime(t.getId())
      if (time > 0) sum += time
    }
    sum
  }

  def cpuTime = {
    var sum = 0L
    for (t <- threads()) {
      val time = bean.getThreadCpuTime(t.getId())
      if (time > 0) sum += time
    }
    sum
  }

  def wallTime = -1L

  def numDispatches = -1L  // Unsupported

  def getExecutor = executor

  def blocking[T](f: => T)(implicit perm: CanAwait) = f
}

/**
 * A scheduler that dispatches directly to an underlying Java
 * cached threadpool executor.
 */
class ThreadPoolScheduler(
  val name: String,
  val executorFactory: ThreadFactory => ExecutorService
) extends Scheduler with ExecutorScheduler {
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
) extends Scheduler with ExecutorScheduler {
  private[this] val local = new LocalScheduler

  def this(name: String) = this(name, Executors.newCachedThreadPool(_))

  override def submit(r: Runnable) {
    if (Thread.currentThread.getThreadGroup == threadGroup)
      local.submit(r)
    else
      try
        executor.execute(new Runnable {
          def run() {
            BridgedThreadPoolScheduler.this.submit(r)
          }
        })
      catch {
        case _: RejectedExecutionException => local.submit(r)
      }
  }

  override def flush() =
    if (Thread.currentThread.getThreadGroup == threadGroup)
      local.flush()
}
