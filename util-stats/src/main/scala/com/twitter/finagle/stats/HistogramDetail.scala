package com.twitter.finagle.stats

/**
 * Details of a histogram's data points. 
 */
trait HistogramDetail {
  /**
   * Returns a sequence of nonzero bucket counts.
   *
   * Example return value from counts:
   * Seq(BucketAndCount(4, 5, 10), BucketAndCount(990, 1000, 7), 
   * 	BucketAndCount(2137204091, Int.MaxValue, 5))
   *
   * Interpretation: 
   * 10 Values in [4, 5) were received, 7 values in
   * [990, 1000) were received, and 5 values in
   * [2137204091, Int.MaxValue) were received.  
   */
  def counts: Seq[BucketAndCount]

}
