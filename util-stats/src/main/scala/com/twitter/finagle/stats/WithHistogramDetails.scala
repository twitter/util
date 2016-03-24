package com.twitter.finagle.stats

/** 
 * Allow [[StatsReceivers]] to provide snapshots of histogram counts.
 * Implementations must expose a map where keys are the name
 * of the stat and values are the contents of the histogram. 
 */ 
trait WithHistogramDetails {
  def histogramDetails: Map[String, HistogramDetail]
}
