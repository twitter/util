package com.twitter.util

import scala.collection.mutable

object MapUtil {

  def newHashMap[K, V](initialCapacity: Int, loadFactor: Double = 0.75): mutable.HashMap[K, V] = {
    new mutable.HashMap[K, V]() {
      this._loadFactor = (loadFactor * 1000).toInt
      override protected val initialSize: Int = (size.toLong / loadFactor).toInt
    }
  }
}
