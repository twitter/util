package com.twitter.concurrent

object Once {
  /**
   * Returns a function that runs `fn` the first time it's called and then never
   * again.
   */
  def apply(fn: => Unit): () => Unit = {
    var executed = false
    new Function0[Unit] {
      def apply(): Unit = synchronized {
        if (!executed) {
          executed = true
          fn
        }
      }
    }
  }
}
