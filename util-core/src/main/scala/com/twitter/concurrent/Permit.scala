package com.twitter.concurrent

trait Permit {
  def release(): Unit
}
