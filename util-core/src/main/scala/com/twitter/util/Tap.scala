package com.twitter.util

/**
 * Contains an implicit conversion to make any object tappable.
 */
object Tap {
  implicit def anyToTap[A](underlying: A) = new Tap(underlying)
}

/**
 * Adds Ruby-style tap method to Scala.
 *
 * Example usage:
 *
 * import Tap._
 * val order = new Order().tap { o =>
 *   o.setStatus('UNPAID')
 * }
 *
 * See also:
 * - http://www.naildrivin5.com/blog/2012/06/22/tap-versus-intermediate-variables.html
 * - http://stackoverflow.com/questions/3241101/with-statement-equivalent-for-scala
 */
class Tap[A](underlying: A) {

  /**
   * Ruby-style tap method
   */
  def tap(func: A => Unit): A = {
    func(underlying)
    underlying
  }
}