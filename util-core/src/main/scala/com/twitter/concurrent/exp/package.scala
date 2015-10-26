package com.twitter.concurrent

package object exp {

  /**
   * Backwards compatibility forwarding for
   * [[com.twitter.concurrent.AsyncStream]]
   */
  @deprecated("Use `com.twitter.concurrent.AsyncStream` instead", "2015-10-20")
  type AsyncStream[+A] = com.twitter.concurrent.AsyncStream[A]

  /**
   * Backwards compatibility forwarding for the
   * [[com.twitter.concurrent.AsyncStream]] companion object.
   */
  @deprecated("Use `com.twitter.concurrent.AsyncStream` instead", "2015-10-20")
  val AsyncStream = com.twitter.concurrent.AsyncStream

}
