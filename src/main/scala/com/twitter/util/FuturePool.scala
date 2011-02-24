package com.twitter.util

import java.util.concurrent.{ExecutorService, Callable}

case class FuturePool(executor: ExecutorService) {
  def apply[T](f: => T): Future[T] = {
    val out = new Promise[T]
    executor.submit(new Runnable {
      def run = out.update(Try(f))
    })
    out
  }
}