package com.twitter.util

class Timer(isDaemon: Boolean) {
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