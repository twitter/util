package com.twitter.util

object FutureTask {
  def apply[A](fn: => A): FutureTask[A] = new FutureTask[A](fn)
}

class FutureTask[A](fn: => A) extends Promise[A] with Runnable {
  def run(): Unit = {
    update(Try(fn))
  }
}
