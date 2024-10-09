package com.twitter.util

import java.lang.Integer.numberOfLeadingZeros
import scala.collection.mutable

object MapUtil {

  def newHashMap[K, V](expectedSize: Int, loadFactor: Double = 0.75): mutable.HashMap[K, V] = {
    new mutable.HashMap[K, V]() {
      this._loadFactor = (loadFactor * 1000).toInt
      override protected def initialSize: Int = {
        roundUpToNextPowerOfTwo((expectedSize / loadFactor).toInt max 4)
      }
    }
  }

  private[this] def roundUpToNextPowerOfTwo(target: Int): Int = {
    1 << -numberOfLeadingZeros(target - 1)
  }
}
