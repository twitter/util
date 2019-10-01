package com.twitter.finagle.stats

/**
 * This trait represents a metric with the full set of its metadata.
 */
sealed abstract class MetricSchema(metricBuilder: MetricBuilder)

/**
 * Represents a counter with its assorted metadata.
 */
case class CounterSchema private (metricBuilder: MetricBuilder) extends MetricSchema(metricBuilder)

/**
 * Represents a gauge with its assorted metadata.
 */
case class GaugeSchema private (metricBuilder: MetricBuilder) extends MetricSchema(metricBuilder)

/**
 * Represents a histogram with its assorted metadata.
 */
case class HistogramSchema private (metricBuilder: MetricBuilder)
    extends MetricSchema(metricBuilder)
