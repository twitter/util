package com.twitter.jvm

import scala.collection.mutable

import java.util.concurrent.{
  AbstractExecutorService, Callable, ScheduledExecutorService, ScheduledFuture,
  TimeUnit}

// A mostly empty implementation so that we can successfully
// mock it.
class MockScheduledExecutorService extends AbstractExecutorService with ScheduledExecutorService {
  val schedules = mutable.Buffer[(Runnable, Long, Long, TimeUnit)]()

  def schedule[V](c: Callable[V], delay: Long, unit: TimeUnit) = throw new Exception
  def schedule(command: Runnable, delay: Long, unit: TimeUnit) = throw new Exception
  def scheduleWithFixedDelay(command: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit) =
    throw new Exception
  def execute(command: Runnable) = throw new Exception
  def awaitTermination(timeout: Long, unit: TimeUnit) = throw new Exception
  def isTerminated() = throw new Exception
  def isShutdown() = throw new Exception
  def shutdownNow() = throw new Exception
  def shutdown() = throw new Exception

  def scheduleAtFixedRate(r: Runnable, initialDelay: Long, period: Long, unit: TimeUnit): ScheduledFuture[_] = {
    schedules += ((r, initialDelay, period, unit))
    null
  }
}
