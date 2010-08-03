package com.twitter.util

import java.util.concurrent.TimeUnit

class CountDownLatch(val count: Int) {
  val underlying = new java.util.concurrent.CountDownLatch(count)
  def countDown() = underlying.countDown()
  def await() = underlying.await()
  def await(timeout: Duration) = underlying.await(timeout.inMillis, TimeUnit.MILLISECONDS)
  def within(timeout: Duration) = await(timeout) || {
    throw new TimeoutException(timeout.toString)
  }
}
