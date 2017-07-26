package com.twitter.finagle.stats

import com.twitter.util.{Future, Stopwatch}
import java.util.concurrent.{Callable, TimeUnit}
import scala.util.control.NonFatal

/**
 * An append-only collection of time-series data. Example Stats are
 * "queue depth" or "query width in a stream of requests".
 *
 * Utilities for timing synchronous execution and asynchronous
 * execution are on the companion object ([[Stat.time(Stat)]] and
 * [[Stat.timeFuture(Stat)]].
 */
trait Stat {
  def add(value: Float): Unit
}

/**
 * Helpers for working with histograms.
 *
 * Java-friendly versions can be found in [[com.twitter.finagle.stats.JStats]].
 */
object Stat {

  /**
   * Time a given `f` using the given `unit`.
   */
  def time[A](stat: Stat, unit: TimeUnit)(f: => A): A = {
    val elapsed = Stopwatch.start()
    try {
      f
    } finally {
      stat.add(elapsed().inUnit(unit))
    }
  }

  /**
   * Time a given `f` using milliseconds.
   */
  def time[A](stat: Stat)(f: => A): A = time(stat, TimeUnit.MILLISECONDS)(f)

  /**
   * Time a given asynchronous `f` using the given `unit`.
   */
  def timeFuture[A](stat: Stat, unit: TimeUnit)(f: => Future[A]): Future[A] = {
    val start = Stopwatch.timeNanos()
    try {
      f.respond { _ =>
        stat.add(unit.convert(Stopwatch.timeNanos() - start, TimeUnit.NANOSECONDS))
      }
    } catch {
      case NonFatal(e) =>
        stat.add(unit.convert(Stopwatch.timeNanos() - start, TimeUnit.NANOSECONDS))
        Future.exception(e)
    }
  }

  /**
   * Time a given asynchronous `f` using milliseconds.
   */
  def timeFuture[A](stat: Stat)(f: => Future[A]): Future[A] =
    timeFuture(stat, TimeUnit.MILLISECONDS)(f)
}

/**
 * Stat utility methods for ease of use from java.
 */
object JStats {

  /**
   * Time a given `fn` using the given `unit`.
   */
  def time[A](stat: Stat, fn: Callable[A], unit: TimeUnit): A = Stat.time(stat, unit)(fn.call())

  /**
   * Time a given `fn` using milliseconds.
   */
  def time[A](stat: Stat, fn: Callable[A]): A = Stat.time(stat)(fn.call())

  /**
   * Time a given asynchronous `fn` using the given `unit`.
   */
  def timeFuture[A](stat: Stat, fn: Callable[Future[A]], unit: TimeUnit): Future[A] =
    Stat.timeFuture(stat, unit)(fn.call())

  /**
   * Time a given asynchronous `fn` using milliseconds.
   */
  def timeFuture[A](stat: Stat, fn: Callable[Future[A]]): Future[A] =
    Stat.timeFuture(stat)(fn.call())
}
