package com.twitter.finagle.stats

/**
 * Allow [[StatsReceivers]] to provide snapshots of histogram counts.
 * Implementations must expose a map where keys are the name
 * of the stat and values are the contents of the histogram.
 */
trait WithHistogramDetails {
  def histogramDetails: Map[String, HistogramDetail]
}

/**
 * Aggregates [[WithHistogramDetails]], merging their `histogramDetails`.
 */
private[stats] class AggregateWithHistogramDetails(underlying: Seq[WithHistogramDetails]) extends WithHistogramDetails {

  /**
   * Merges the resulting `histogramDetails` of the underlying sequence.  Where
   * the different implementations have overlapping keys in their
   * `histogramDetails`, it chooses the first implementations.
   */
  def histogramDetails: Map[String, HistogramDetail] =
    underlying.foldLeft[Map[String, HistogramDetail]](Map.empty[String, HistogramDetail]) { (acc, cur) =>
      cur.histogramDetails ++ acc
    }
}

object AggregateWithHistogramDetails {
  def apply(detailed: Seq[WithHistogramDetails]): WithHistogramDetails = {
    require(detailed.nonEmpty, "Cannot create aggregate WithHistogramDetails from nothing")

    if (detailed.size == 1) detailed.head
    else new AggregateWithHistogramDetails(detailed)
  }
}
