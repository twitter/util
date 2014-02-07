package com.twitter.concurrent

import java.util.ArrayDeque
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger
import management.ManagementFactory
import scala.util.Random

trait Scheduler {
  /**
   * Schedule `r` to be run at some time in the future.
   */
  def submit(r: Runnable)

  /**
   * Flush the schedule. Returns when there is no more
   * work to do.
   */
  def flush()

  // A note on Hotspot's ThreadMXBean's CPU time. On Linux, this
  // uses clock_gettime[1] which should both be fast and accurate.
  //
  // On OSX, the Mach thread_info call is used.
  //
  // [1] http://linux.die.net/man/3/clock_gettime

  /** The amount of User time that's been scheduled as per ThreadMXBean. */
  def usrTime: Long

  /** The amount of CPU time that's been scheduled as per ThreadMXBean */
  def cpuTime: Long

  /** Number of dispatches performed by this scheduler. */
  def numDispatches: Long
}

/**
 * A global scheduler.
 */
object Scheduler extends Scheduler {
  @volatile private var self: Scheduler = new LocalScheduler

  def apply(): Scheduler = self

  // Note: This can be unsafe since some schedulers may be active,
  // and flush() can be invoked on the wrong scheduler.
  //
  // This can happen, for example, if a LocalScheduler is used while
  // a future is resolved via Await.
  def setUnsafe(sched: Scheduler) {
    self = sched
  }

  def submit(r: Runnable) = self.submit(r)
  def flush() = self.flush()
  def usrTime = self.usrTime
  def cpuTime = self.cpuTime
  def numDispatches = self.numDispatches
}


/**
 * An efficient thread-local, direct-dispatch scheduler.
 */
private class LocalScheduler extends Scheduler {
  private[this] val SampleScale = 1000
  private[this] val bean = ManagementFactory.getThreadMXBean()
  @volatile private[this] var activations = Set[Activation]()
  private[this] val local = new ThreadLocal[Activation] {
    override def initialValue = null
  }

  private class Activation extends Scheduler {
    private[this] var r0, r1, r2: Runnable = null
    private[this] val rs = new ArrayDeque[Runnable]
    private[this] var running = false
    private[this] val rng = new Random

    // This is safe: there's only one updater.
    @volatile var usrTime = 0L
    @volatile var cpuTime = 0L
    @volatile var numDispatches = 0L

    def submit(r: Runnable) {
      assert(r != null)
      if (r0 == null) r0 = r
      else if (r1 == null) r1 = r
      else if (r2 == null) r2 = r
      else rs.addLast(r)
      if (!running) {
        if (rng.nextInt(SampleScale) == 0) {
          numDispatches += SampleScale
          val cpu0 = bean.getCurrentThreadCpuTime()
          val usr0 = bean.getCurrentThreadUserTime()
          run()
          cpuTime += (bean.getCurrentThreadCpuTime() - cpu0)*SampleScale
          usrTime += (bean.getCurrentThreadUserTime() - usr0)*SampleScale
        } else {
          run()
        }
      }
    }

    def flush() {
      if (running) run()
    }

    private[this] def run() {
      val save = running
      running = true
      // via moderately silly benchmarking, the
      // queue unrolling gives us a ~50% speedup
      // over pure Queue usage for common
      // situations.
      try {
        while (r0 != null) {
          val r = r0
          r0 = r1
          r1 = r2
          r2 = if (rs.isEmpty) null else rs.removeFirst()
          r.run()
        }
      } finally {
        running = save
      }
    }
  }

  private[this] def get(): Activation = {
    val a = local.get()
    if (a != null)
      return a

    local.set(new Activation)
    synchronized { activations += local.get() }
    local.get()
  }

  // Scheduler implementation:
  def submit(r: Runnable) = get().submit(r)
  def flush() = get().flush()

  def usrTime = (activations.iterator map (_.usrTime)).sum
  def cpuTime = (activations.iterator map (_.cpuTime)).sum
  def numDispatches = (activations.iterator map (_.numDispatches)).sum
}

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

  protected def threads() = {
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

  def numDispatches = -1L  // Unsupported

  def getExecutor = executor
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
 * A scheduler that will bridge tasks from outside into the executor threads,
 * while keeping all local tasks on their local threads.
 * (Note: This scheduler is expecting an executor with unbounded capacity, not
 * expecting any RejectedExecutionException's other than the ones caused by
 * shutting down)
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
}

