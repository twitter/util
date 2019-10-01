package com.twitter.finagle.stats

/**
 * Represents the units this metric is measured in.
 *
 * Common units for metrics are:
 *   Bytes/Kilobytes/Megabytes (for payload size, data written to disk)
 *   Milliseconds (for latency, GC durations)
 *   Requests (for successes, failures, and requests)
 *   Percentage (for CPU Util, Memory Usage)
 */
sealed trait MetricUnit
case object Unspecified extends MetricUnit
case object Bytes extends MetricUnit
case object Kilobytes extends MetricUnit
case object Megabytes extends MetricUnit
case object Seconds extends MetricUnit
case object Milliseconds extends MetricUnit
case object Microseconds extends MetricUnit
case object Requests extends MetricUnit
case object Percentage extends MetricUnit
case class CustomUnit(name: String) extends MetricUnit
