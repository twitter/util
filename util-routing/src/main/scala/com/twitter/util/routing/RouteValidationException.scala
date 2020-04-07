package com.twitter.util.routing

/**
 * Exception thrown when a [[RouterBuilder]] encounters a `Route` that is not valid for
 * the [[Router]] type it is building.
 */
case class RouteValidationException(msg: String, cause: Throwable) extends Exception(msg, cause) {
  def this(msg: String) = this(msg, null)
  def this(cause: Throwable) = this(null, cause)
}
