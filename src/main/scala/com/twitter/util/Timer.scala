package com.twitter.util

trait Timer {
  def schedule(when: Time)(f: => Unit)
  def schedule(when: Time, period: Duration)(f: => Unit)

  def schedule(period: Duration)(f: => Unit) {
    schedule(period.fromNow, period)(f)
  }

  def stop()
}

class JavaTimer(isDaemon: Boolean) extends Timer {
  def this() = this(false)

  val javaTimer = new java.util.Timer(isDaemon)

  def schedule(when: Time)(f: => Unit) {
    javaTimer.schedule(toTask(f), when.toDate)
  }

  def schedule(when: Time, period: Duration)(f: => Unit) {
    javaTimer.schedule(toTask(f), when.toDate, period.inMillis)
  }

  def stop() = javaTimer.cancel()

  private[this] def toTask(f: => Unit) = new java.util.TimerTask {
    def run {
      f
    }
  }
}