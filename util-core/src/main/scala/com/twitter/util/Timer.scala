package com.twitter.util

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{CancellationException, ExecutorService, RejectedExecutionHandler, ScheduledThreadPoolExecutor, ThreadFactory, TimeUnit}

import scala.collection.mutable.ArrayBuffer

import com.twitter.concurrent.NamedPoolThreadFactory

trait TimerTask extends Closable {
  def cancel()
  def close(deadline: Time) = Future(cancel())
}

trait Timer {
  def schedule(when: Time)(f: => Unit): TimerTask
  def schedule(when: Time, period: Duration)(f: => Unit): TimerTask

  def schedule(period: Duration)(f: => Unit): TimerTask = {
    schedule(period.fromNow, period)(f)
  }

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
    val pending = new AtomicBoolean(true)
    val p = new Promise[A]

    val task = schedule(time) {
      if (pending.compareAndSet(true, false))
        p.update(Try(f))
    }

    p.setInterruptHandler {
      case cause =>
        if (pending.compareAndSet(true, false)) {
          task.cancel()

          val exc = new CancellationException
          exc.initCause(cause)
          p.setException(exc)
        }
    }

    p
  }

  def stop()
}

object Timer {
  val Nil: Timer = new NullTimer
}

/**
 * A NullTimer is not a timer at all: it invokes all tasks immediately
 * and inline.
 */
class NullTimer extends Timer {
  def schedule(when: Time)(f: => Unit): TimerTask = {
    f
    NullTimerTask
  }
  def schedule(when: Time, period: Duration)(f: => Unit): TimerTask = {
    f
    NullTimerTask
  }

  def stop() {}
}

object NullTimerTask extends TimerTask {
  def cancel() {}
}

class ThreadStoppingTimer(underlying: Timer, executor: ExecutorService) extends Timer {
  def schedule(when: Time)(f: => Unit): TimerTask =
    underlying.schedule(when)(f)
  def schedule(when: Time, period: Duration)(f: => Unit): TimerTask =
    underlying.schedule(when, period)(f)

  def stop() {
    executor.submit(new Runnable { def run() = underlying.stop() })
  }
}

trait ReferenceCountedTimer extends Timer {
  def acquire()
}

class ReferenceCountingTimer(factory: () => Timer)
  extends ReferenceCountedTimer
{
  private[this] var refcount = 0
  private[this] var underlying: Timer = null

  def acquire() = synchronized {
    refcount += 1
    if (refcount == 1) {
      require(underlying == null)
      underlying = factory()
    }
  }

  def stop() = synchronized {
    refcount -= 1
    if (refcount == 0) {
      underlying.stop()
      underlying = null
    }
  }

  // Just dispatch to the underlying timer. It's the responsibility of
  // the API consumer to not call into the timer once it has been
  // stopped.
  def schedule(when: Time)(f: => Unit) = underlying.schedule(when)(f)
  def schedule(when: Time, period: Duration)(f: => Unit) = underlying.schedule(when, period)(f)
}

class JavaTimer(isDaemon: Boolean) extends Timer {
  def this() = this(false)

  private[this] val underlying = new java.util.Timer(isDaemon)

  def schedule(when: Time)(f: => Unit) = {
    val task = toJavaTimerTask(f)
    underlying.schedule(task, when.toDate)
    toTimerTask(task)
  }

  def schedule(when: Time, period: Duration)(f: => Unit) = {
    val task = toJavaTimerTask(f)
    underlying.schedule(task, when.toDate, period.inMillis)
    toTimerTask(task)
  }

  def stop() = underlying.cancel()

  /**
   * log any Throwables caught by the internal TimerTask.
   *
   * By default we log to System.err but users may subclass and log elsewhere.
   *
   * This method MUST NOT throw or else your Timer will die.
   */
  def logError(t: Throwable) {
    System.err.println("WARNING: JavaTimer caught exception running task: %s".format(t))
    t.printStackTrace(System.err)
  }

  private[this] def toJavaTimerTask(f: => Unit) = new java.util.TimerTask {
    def run {
      try {
        f
      } catch {
        case NonFatal(t) => logError(t)
        case fatal: Throwable =>
          logError(fatal)
          throw fatal
      }
    }
  }

  private[this] def toTimerTask(task: java.util.TimerTask) = new TimerTask {
    def cancel() { task.cancel() }
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

  def schedule(when: Time)(f: => Unit): TimerTask = {
    val runnable = new Runnable { def run = f }
    val javaFuture = underlying.schedule(runnable, when.sinceNow.inMillis, TimeUnit.MILLISECONDS)
    new TimerTask {
      def cancel() {
        javaFuture.cancel(true)
        underlying.remove(runnable)
      }
    }
  }

  def schedule(when: Time, period: Duration)(f: => Unit): TimerTask =
    schedule(when.sinceNow, period)(f)

  def schedule(wait: Duration, period: Duration)(f: => Unit): TimerTask = {
    val runnable = new Runnable { def run = f }
    val javaFuture = underlying.scheduleAtFixedRate(runnable,
      wait.inMillis, period.inMillis, TimeUnit.MILLISECONDS)
    new TimerTask {
      def cancel() {
        javaFuture.cancel(true)
        underlying.remove(runnable)
      }
    }
  }

  def stop() = underlying.shutdown()
}

// Exceedingly useful for writing well-behaved tests.
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
    toRun filter { !_.isCancelled } foreach { _.runner() }
  }

  def schedule(when: Time)(f: => Unit): TimerTask = synchronized {
    val task = Task(when, () => f)
    tasks += task
    task
  }

  /**
   * Pay attention that ticking frozen time forward more than 1x duration will
   * result in only one invocation of your task.
   */
  def schedule(when: Time, period: Duration)(f: => Unit): TimerTask = {
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
