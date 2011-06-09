package com.twitter.hashing


import scala.collection.mutable

class DistributionTester[A](distributor: Distributor[A]) {

  /**
  * Returns a normalized standard deviation indicating how well the keys
  * are distributed between the nodes. The closer to 0 the better.
  */
  def distributionDeviation(keys: Seq[Long]): Double = {
    val keysPerNode = mutable.Map[A, Int]()
    keys map { distributor.nodeForHash(_) } foreach { key =>
      if (!keysPerNode.contains(key)) keysPerNode(key) = 0
      keysPerNode(key) += 1
    }
    var frequencies = keysPerNode.values.toList
    frequencies ++= 0 until (distributor.nodeCount - frequencies.size) map { _ => 0 }
    val average = frequencies.sum.toDouble / frequencies.size
    val diffs = frequencies.map { v => math.pow((v - average), 2) }
    val sd = math.sqrt(diffs.sum / (frequencies.size - 1))
    sd / average
  }
}