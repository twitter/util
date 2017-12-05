package com.twitter.util

import scala.collection.mutable

class MapToSetAdapter[A](map: mutable.Map[A, A]) extends mutable.Set[A] {
  def +=(elem: A): this.type = {
    map(elem) = elem
    this
  }
  def -=(elem: A): this.type = {
    map -= elem
    this
  }
  override def size: Int = map.size
  def iterator: Iterator[A] = map.keysIterator
  def contains(elem: A): Boolean = map.contains(elem)
}
