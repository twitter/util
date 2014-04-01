package com.twitter.util

trait NoStacktrace extends Exception {
  override def fillInStackTrace = this
  // specs expects non-empty stacktrace array
  this.setStackTrace(NoStacktrace.NoStacktraceArray)
}

object NoStacktrace {
  val NoStacktraceArray = Array(new StackTraceElement("com.twitter.util", "NoStacktrace", null, -1))
}
