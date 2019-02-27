package com.twitter.jvm

/**
 * Information about the Heap
 *
 * @param allocated Estimated number of bytes
 * that have been allocated so far (into eden)
 *
 * @param tenuringThreshold How many times an Object
 * needs to be copied before being tenured.
 *
 * @param ageHisto Histogram of the number of bytes that
 * have been copied as many times. Note: 0-indexed.
 */
case class Heap(allocated: Long, tenuringThreshold: Long, ageHisto: Seq[Long])
