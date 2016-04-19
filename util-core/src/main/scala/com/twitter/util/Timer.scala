package com.twitter.util

import com.twitter.concurrent.NamedPoolThreadFactory
import java.util.concurrent._
import scala.collection.mutable.ArrayBuffer

/**
 * TimerTasks represent pending tasks scheduled by a [[Timer]].
 */
trait TimerTask extends Closable {
  def cancel(): Unit
  def close(deadline: Time): Future[Unit] = Future(cancel())
}

/**
 * Timers are used to schedule tasks in the future.
 * They support both one-shot and recurring tasks.
 *
 * Timers propagate [[Local]] state, including the [[Monitor]], from when the task
 * is scheduled to when it is run.
 *
 * @note Scheduling tasks with [[Timer]]s should rarely
 * be done directly; for example, when programming
 * with [[Future]]s, prefer using [[Future.sleep]].
 */
trait Timer {
  /**
   * Run `f` at time `when`.
   */
  final def schedule(when: Time)(f: => Unit): TimerTask = {
    val locals = Local.save()
    scheduleOnce(when) {
      Local.let(locals)(Monitor.get(f))
    }
  }

  protected def scheduleOnce(when: Time)(f: => Unit): TimerTask

  /**
   * Run `f` at time `when`; subsequently run `f` at every
   * elapsed `period`.
   */
  final def schedule(when: Time, period: Duration)(f: => Unit): TimerTask = {
    val locals = Local.save()
    schedulePeriodically(when, period) {
      Local.let(locals)(Monitor.get(f))
    }
  }

  protected def schedulePeriodically(when: Time, period: Duration)(f: => Unit): TimerTask

  /**
   * Run `f` every elapsed `period`, starting `period` from now.
   */
  final def schedule(period: Duration)(f: => Unit): TimerTask =
    schedule(period.fromNow, period)(f)

  /**
   * Performs an operation after the specified delay.  Interrupting the Future
   * will cancel the scheduled timer task, if not too late.
   */
  def doLater[A](delay: Duration)(f: => A): Future[A] = {
    doAt(Time.now + delay)(f)
  }

  /**
   * Performs an operation at the specified time.  Interrupting the Future
   * will cancel the scheduled timer task, if not too late.
   */
  def doAt[A](time: Time)(f: => A): Future[A] = {
    var pending = true
    val p = new Promise[A]()

    def isFirst(): Boolean = p.synchronized {
      if (pending) {
        pending = false
        true
      } else {
        false
      }
    }

    val task = schedule(time) {
      if (isFirst())
        p.update(Try(f))
    }

    p.setInterruptHandler { case cause =>
      if (isFirst()) {
        task.cancel()

        val exc = new CancellationException
        exc.initCause(cause)
        p.setException(exc)
      }
    }
    p
  }

  /**
   * Stop the timer. Pending tasks are cancelled.
   * The timer is unusable after being stopped.
   */
  def stop(): Unit
}

object Timer {
  /**
   * A singleton instance of a [[NullTimer]] that invokes all tasks
   * immediately with the current thread.
   */
  val Nil: Timer = new NullTimer
}

/**
 * A NullTimer is not a timer at all: it invokes all tasks immediately
 * and inline.
 */
class NullTimer extends Timer {
  protected def scheduleOnce(when: Time)(f: => Unit): TimerTask = {
    f
    NullTimerTask
  }
  protected def schedulePeriodically(when: Time, period: Duration)(f: => Unit): TimerTask = {
    f
    NullTimerTask
  }

  def stop(): Unit = ()
}

object NullTimerTask extends TimerTask {
  def cancel(): Unit = ()
}

class ThreadStoppingTimer(underlying: Timer, executor: ExecutorService) extends Timer {
  protected def scheduleOnce(when: Time)(f: => Unit): TimerTask =
    underlying.schedule(when)(f)

  protected def schedulePeriodically(when: Time, period: Duration)(f: => Unit): TimerTask =
    underlying.schedule(when, period)(f)

  def stop(): Unit = {
    executor.submit(new Runnable { def run() = underlying.stop() })
  }
}

trait ReferenceCountedTimer extends Timer {
  def acquire(): Unit
}

class ReferenceCountingTimer(factory: () => Timer)
  extends ReferenceCountedTimer
{
  private[this] var refcount = 0
  private[this] var underlying: Timer = null

  def acquire(): Unit = synchronized {
    refcount += 1
    if (refcount == 1) {
      require(underlying == null)
      underlying = factory()
    }
  }

  def stop(): Unit = synchronized {
    refcount -= 1
    if (refcount == 0) {
      underlying.stop()
      underlying = null
    }
  }

  // Just dispatch to the underlying timer. It's the responsibility of
  // the API consumer to not call into the timer once it has been
  // stopped.
  protected def scheduleOnce(when: Time)(f: => Unit): TimerTask =
    underlying.schedule(when)(f)

  protected def schedulePeriodically(when: Time, period: Duration)(f: => Unit): TimerTask =
    underlying.schedule(when, period)(f)
}

/**
 * A [[Timer]] that is implemented via a [[java.util.Timer]].
 *
 * @param isDaemon whether or not the associated [[Thread]] should run
 *   as a daemon.
 * @param name used as the name of the associated [[Thread]] when specified.
 */
class JavaTimer(isDaemon: Boolean, name: Option[String]) extends Timer {
  def this(isDaemon: Boolean) = this(isDaemon, None)
  def this() = this(false)

  override def toString: String = name match {
    case Some(n) => s"com.twitter.util.JavaTimer($n)"
    case None => super.toString
  }

  private[this] val underlying = name match {
    case Some(n) => new java.util.Timer(n, isDaemon)
    case None => new java.util.Timer(isDaemon)
  }

  private[this] val catcher: PartialFunction[Throwable, Unit] = {
    case NonFatal(t) =>
      logError(t)
    case fatal: Throwable =>
      logError(fatal)
      throw fatal
  }

  protected def scheduleOnce(when: Time)(f: => Unit): TimerTask = {
    val task = toJavaTimerTask(try f catch catcher)
    underlying.schedule(task, safeTime(when).toDate)
    toTimerTask(task)
  }

  protected def schedulePeriodically(when: Time, period: Duration)(f: => Unit): TimerTask = {
    val task = toJavaTimerTask(try f catch catcher)
    underlying.schedule(task, safeTime(when).toDate, period.inMillis)
    toTimerTask(task)
  }

  def stop(): Unit = underlying.cancel()

  /**
   * log any Throwables caught by the internal TimerTask.
   *
   * By default we log to System.err but users may subclass and log elsewhere.
   *
   * This method MUST NOT throw or else your Timer will die.
   */
  def logError(t: Throwable): Unit = {
    System.err.println("WARNING: JavaTimer caught exception running task: %s".format(t))
    t.printStackTrace(System.err)
  }

  // Make sure Time is on or after the epoch.  j.u.Timer throws an
  // IllegalArgumentException if the value is negative.  To allow `when` to be
  // before the epoch (e.g. Time.Bottom), move any pre-epoch times to the epoch.
  private[this] def safeTime(time: Time): Time = {
    time.max(Time.epoch)
  }

  private[this] def toJavaTimerTask(f: => Unit) = new java.util.TimerTask {
    def run(): Unit = f
  }

  private[this] def toTimerTask(task: java.util.TimerTask) = new TimerTask {
    def cancel(): Unit = task.cancel()
  }
}

class ScheduledThreadPoolTimer(
    poolSize: Int,
    threadFactory: ThreadFactory,
    rejectedExecutionHandler: Option[RejectedExecutionHandler])
  extends Timer {
  def this(poolSize: Int, threadFactory: ThreadFactory) =
    this(poolSize, threadFactory, None)

  def this(poolSize: Int, threadFactory: ThreadFactory, handler: RejectedExecutionHandler) =
    this(poolSize, threadFactory, Some(handler))

  /** Construct a ScheduledThreadPoolTimer with a NamedPoolThreadFactory. */
  def this(poolSize: Int = 2, name: String = "timer", makeDaemons: Boolean = false) =
    this(poolSize, new NamedPoolThreadFactory(name, makeDaemons), None)

  private[this] val underlying = rejectedExecutionHandler match {
    case None =>
      new ScheduledThreadPoolExecutor(poolSize, threadFactory)
    case Some(h: RejectedExecutionHandler) =>
      new ScheduledThreadPoolExecutor(poolSize, threadFactory, h)
  }

  private[this] def toRunnable(f: => Unit): Runnable = new Runnable {
    def run(): Unit = f
  }

  protected def scheduleOnce(when: Time)(f: => Unit): TimerTask = {
    val runnable = toRunnable(f)
    val javaFuture = underlying.schedule(runnable, when.sinceNow.inMillis, TimeUnit.MILLISECONDS)
    new TimerTask {
      def cancel(): Unit = {
        javaFuture.cancel(true)
        underlying.remove(runnable)
      }
    }
  }

  protected def schedulePeriodically(when: Time, period: Duration)(f: => Unit): TimerTask =
    schedule(when.sinceNow, period)(f)

  def schedule(wait: Duration, period: Duration)(f: => Unit): TimerTask = {
    val runnable = toRunnable(f)
    val javaFuture = underlying.scheduleAtFixedRate(runnable,
      wait.inMillis, period.inMillis, TimeUnit.MILLISECONDS)
    new TimerTask {
      def cancel(): Unit = {
        javaFuture.cancel(true)
        underlying.remove(runnable)
      }
    }
  }

  def stop(): Unit =
    underlying.shutdown()

  /** exposed for testing, stops and cancels any pending tasks */
  private[util] def stopWithPending(): Unit =
    underlying.shutdownNow()

}

/**
 * Exceedingly useful for writing well-behaved tests that need control
 * over a [[Timer]]. This is due to it playing well with the [[Time]]
 * manipulation methods such as [[Time.withTimeAt]], [[Time.withCurrentTimeFrozen]],
 * and so on.
 */
class MockTimer extends Timer {
  // These are weird semantics admittedly, but there may
  // be a bunch of tests that rely on them already.
  case class Task(var when: Time, runner: () => Unit)
    extends TimerTask
  {
    var isCancelled = false

    def cancel(): Unit = MockTimer.this.synchronized {
      isCancelled = true
      nCancelled += 1
      when = Time.now
      tick()
    }
  }

  var isStopped = false
  var tasks = ArrayBuffer[Task]()
  var nCancelled = 0

  def tick(): Unit = synchronized {
    if (isStopped)
      throw new IllegalStateException("timer is stopped already")

    val now = Time.now
    val (toRun, toQueue) = tasks.partition { task => task.when <= now }
    tasks = toQueue
    toRun.filterNot(_.isCancelled).foreach(_.runner())
  }

  protected def scheduleOnce(when: Time)(f: => Unit): TimerTask = synchronized {
    val task = Task(when, () => f)
    tasks += task
    task
  }

  /**
   * Pay attention that ticking frozen time forward more than 1x duration will
   * result in only one invocation of your task.
   */
  protected def schedulePeriodically(when: Time, period: Duration)(f: => Unit): TimerTask = {
    var isCancelled = false

    val task = new TimerTask {
      def cancel(): Unit = MockTimer.this.synchronized {
        isCancelled = true
      }
    }

    def runAndReschedule(): Unit = MockTimer.this.synchronized {
      if (!isCancelled) {
        schedule(Time.now + period) { runAndReschedule() }
        f
      }
    }

    schedule(when) { runAndReschedule() } // discard
    task
  }

  def stop(): Unit = synchronized {
    isStopped = true
  }
}
