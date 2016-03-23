package com.twitter.finagle.stats

/**
 * Buckets consist of a lower limit and an upper limit. In histograms, all 
 * values that fall inside these limits are counted as the same.
 *
 * The lower limit is inclusive and the upper limit is exclusive
 * 
 * @param count number of data points which landed in that bucket.
 */
case class BucketAndCount(lowerLimit: Long, upperLimit: Long, count: Int)