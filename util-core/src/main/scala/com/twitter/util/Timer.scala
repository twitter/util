package com.twitter.util

import java.util.concurrent.{
  RejectedExecutionHandler, ScheduledThreadPoolExecutor,
  ThreadFactory, TimeUnit, ExecutorService}
import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.conversions.time._

trait TimerTask {
  def cancel()
}

trait Timer {
  def schedule(when: Time)(f: => Unit): TimerTask
  def schedule(when: Time, period: Duration)(f: => Unit): TimerTask

  def schedule(period: Duration)(f: => Unit): TimerTask = {
    schedule(period.fromNow, period)(f)
  }

  def stop()
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

class ReferenceCountedTimer(factory: () => Timer) extends Timer {
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

  private[this] def toJavaTimerTask(f: => Unit) = new java.util.TimerTask {
    def run { f }
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
  def this(poolSize: Int = 2, name: String = "timer") =
    this(poolSize, new NamedPoolThreadFactory(name), None)
  
  private[this] val underlying = rejectedExecutionHandler match {
    case None =>
      new ScheduledThreadPoolExecutor(poolSize, threadFactory)
    case Some(h: RejectedExecutionHandler) =>
      new ScheduledThreadPoolExecutor(poolSize, threadFactory, h)
  }

  def schedule(when: Time)(f: => Unit): TimerTask = {
    val task = toJavaTimerTask(f)
    underlying.schedule(task, when.sinceNow.inMillis, TimeUnit.MILLISECONDS)
    toTimerTask(task)
  }

  def schedule(when: Time, period: Duration)(f: => Unit): TimerTask =
    schedule(when.sinceNow, period)(f)

  def schedule(wait: Duration, period: Duration)(f: => Unit): TimerTask = {
    val task = toJavaTimerTask(f)
    underlying.scheduleAtFixedRate(task,
      wait.inMillis, period.inMillis, TimeUnit.MILLISECONDS)
    toTimerTask(task)
  }

  def stop() = underlying.shutdown()

  private[this] def toJavaTimerTask(f: => Unit) = new java.util.TimerTask {
    def run { f }
  }

  private[this] def toTimerTask(task: java.util.TimerTask) = new TimerTask {
    def cancel() { task.cancel() }
  }
}
