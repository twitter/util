package com.twitter.concurrent

object Once {

  /**
   * Returns a function that runs `fn` the first time it's called and then never
   * again.  If the returned function is invoked while another thread is running
   * `fn` for the first (and only) time, the interloping thread blocks until
   * `fn` has finished.
   *
   * If `fn` throws, it may be run more than once.
   */
  def apply(fn: => Unit): () => Unit = {
    new Function0[Unit] {
      // we can't use method synchronization because of https://issues.scala-lang.org/browse/SI-9814
      // this `val self = this` indirection convinces the scala compiler to use object
      // synchronization instead of method synchronization
      val self = this
      @volatile var executed = false
      def apply(): Unit = if (!executed) {
        self.synchronized {
          if (!executed) {
            fn
            executed = true
          }
        }
      }
    }
  }
}
