package com.twitter.util

trait Extractable[T] {
  def apply(): T
}
