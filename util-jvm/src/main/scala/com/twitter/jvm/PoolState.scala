package com.twitter.jvm

import com.twitter.util.StorageUnit

case class PoolState(numCollections: Long, capacity: StorageUnit, used: StorageUnit) {
  def -(other: PoolState): PoolState = PoolState(
    numCollections = this.numCollections - other.numCollections,
    capacity = other.capacity,
    used = this.used + other.capacity - other.used +
      other.capacity * (this.numCollections - other.numCollections - 1)
  )

  override def toString: String =
    s"PoolState(n=$numCollections,remaining=${capacity - used}[$used of $capacity])"
}
