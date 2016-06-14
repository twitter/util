package com.twitter.concurrent

object Once {
  /**
   * Returns a function that runs `fn` the first time it's called and then never
   * again.  If the returned function is invoked while another thread is running
   * `fn` for the first (and only) time, the interloping thread blocks until
   * `fn` has finished.
   */
  def apply(fn: => Unit): () => Unit = {
    new Function0[Unit] {
      // we can't use method synchronization because of https://issues.scala-lang.org/browse/SI-9814
      val lock = new Object
      @volatile var executed = false
      def apply(): Unit = if (!executed) {
        lock.synchronized {
          if (!executed) {
            fn
            executed = true
          }
        }
      }
    }
  }
}
