package com.twitter.util

import scala.collection.mutable

object MapUtil {

  def newHashMap[K, V](expectedSize: Int, loadFactor: Double = 0.75): mutable.HashMap[K, V] = {
    new mutable.HashMap[K, V]((expectedSize / loadFactor).toInt, loadFactor)
  }
}
