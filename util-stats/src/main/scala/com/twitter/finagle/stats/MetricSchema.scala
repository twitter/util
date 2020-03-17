package com.twitter.finagle.stats

/**
 * This trait represents a metric with the full set of its metadata.
 */
sealed abstract class MetricSchema(val metricBuilder: MetricBuilder)

/**
 * Represents a counter with its assorted metadata.
 */
case class CounterSchema(override val metricBuilder: MetricBuilder)
    extends MetricSchema(metricBuilder)

/**
 * Represents a gauge with its assorted metadata.
 */
case class GaugeSchema private (override val metricBuilder: MetricBuilder)
    extends MetricSchema(metricBuilder)

/**
 * Represents a histogram with its assorted metadata.
 */
case class HistogramSchema private (override val metricBuilder: MetricBuilder)
    extends MetricSchema(metricBuilder)
