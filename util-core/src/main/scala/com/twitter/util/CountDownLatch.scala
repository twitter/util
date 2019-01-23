package com.twitter.util

import java.util.concurrent.TimeUnit

class CountDownLatch(val initialCount: Int) {
  val underlying: java.util.concurrent.CountDownLatch =
    new java.util.concurrent.CountDownLatch(initialCount)
  def count: Long = underlying.getCount
  def isZero: Boolean = count == 0
  def countDown(): Unit = underlying.countDown()
  def await(): Unit = underlying.await()
  def await(timeout: Duration): Boolean = underlying.await(timeout.inMillis, TimeUnit.MILLISECONDS)
  def within(timeout: Duration): Boolean = await(timeout) || {
    throw new TimeoutException(timeout.toString)
  }
}
