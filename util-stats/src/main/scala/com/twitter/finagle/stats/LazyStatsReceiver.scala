package com.twitter.finagle.stats

import com.twitter.finagle.stats.MetricBuilder.{CounterType, HistogramType}

/**
 * Wraps an underlying [[StatsReceiver]] to ensure that derived counters and
 * stats will not start exporting metrics until `incr` or `add` is first called
 * on them.
 *
 * This should be used when integrating with tools that create metrics eagerly,
 * but you don't know whether you're going to actually use those metrics or not.
 * One example might be if you're speaking to a remote peer that exposes many
 * endpoints, and you eagerly create metrics for all of those endpoints, but
 * aren't going to use all of the different methods.
 *
 * We don't apply this very widely automatically--it can mess with caching, and
 * adds an extra allocation when you construct a new counter or stat, so please
 * be judicious when using it.
 *
 * @note does not change the way gauges are used, since there isn't a way of
 *       modeling whether a gauge is "used" or not.
 */
final class LazyStatsReceiver(val self: StatsReceiver) extends StatsReceiverProxy {
  override def counter(metricBuilder: MetricBuilder): Counter = {
    validateMetricType(metricBuilder, CounterType)
    new Counter {
      private[this] lazy val underlying = self.counter(metricBuilder)
      def incr(delta: Long): Unit = underlying.incr(delta)
      def metadata: Metadata = metricBuilder
    }
  }

  override def stat(metricBuilder: MetricBuilder): Stat = {
    validateMetricType(metricBuilder, HistogramType)
    new Stat {
      private[this] lazy val underlying = self.stat(metricBuilder)
      def add(value: Float): Unit = underlying.add(value)
      def metadata: Metadata = metricBuilder
    }
  }
}
