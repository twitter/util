package com.twitter.util

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
