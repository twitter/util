package com.twitter.conversions

import java.util.concurrent.Callable
import scala.language.implicitConversions

/**
 * Implicits for turning a block of code into a Runnable or Callable.
 */
object ThreadOps {

  implicit def makeRunnable(f: => Unit): Runnable = new Runnable() {
    def run(): Unit = f
  }

  implicit def makeCallable[T](f: => T): Callable[T] = new Callable[T]() {
    def call(): T = f
  }

}
