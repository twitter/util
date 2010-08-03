package com.twitter.util

import java.util.concurrent.TimeUnit

object CountDownLatch {
  class TimeoutException(message: String) extends Exception(message)
}

class CountDownLatch(val count: Int) {
  val underlying = new java.util.concurrent.CountDownLatch(count)
  def countDown() = underlying.countDown()
  def await() = underlying.await()
  def await(timeout: Duration) = {
    underlying.await(timeout.inMillis, TimeUnit.MILLISECONDS) || {
      throw new CountDownLatch.TimeoutException("Timeout exceeded awaiting countDown")
    }
  }
}