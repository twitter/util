package com.twitter.util

import java.util.concurrent.TimeUnit

class CountDownLatch(val initialCount: Int) {
  val underlying = new java.util.concurrent.CountDownLatch(initialCount)
  def count = underlying.getCount
  def isZero = count == 0
  def countDown() = underlying.countDown()
  def await() = underlying.await()
  def await(timeout: Duration) = underlying.await(timeout.inMillis, TimeUnit.MILLISECONDS)
  def within(timeout: Duration) = await(timeout) || {
    throw new TimeoutException(timeout.toString)
  }
}
