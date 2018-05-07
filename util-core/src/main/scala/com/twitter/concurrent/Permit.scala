package com.twitter.concurrent

/**
 * Token representing an interest in a resource and a way to release that interest.
 */
trait Permit {

  /**
   * Indicate that you are done with your Permit.
   *
   * @note calling this multiple times will result in undefined behavior.
   */
  def release(): Unit
}
