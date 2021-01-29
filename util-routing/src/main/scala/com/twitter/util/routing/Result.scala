package com.twitter.util.routing

/** A result from attempting to find a route via a [[Router]]. */
sealed abstract class Result

/**
 * A result that represents that the [[Router router]] could not determine a defined route
 * for a given input.
 */
case object NotFound extends Result {

  /** Java-friendly Singleton accessor */
  def get(): Result = this
}

/**
 * A result that represents that the [[Router router]] could determine a defined [[Route route]]
 * for a given [[Input input]].
 *
 * @param input The [[Input input]] when attempting to find a route.
 * @param route The [[Route route]] that was found.
 * @tparam Input The input type for the [[Router]].
 * @tparam Route The route type for the [[Router]].
 */
final case class Found[Input, Route](input: Input, route: Route) extends Result

/**
 * A result that represents that the [[Router router]] has been closed and will no longer attempt
 * to find routes for any inputs.
 */
case object RouterClosed extends Result {

  /** Java-friendly Singleton accessor */
  def get(): Result = this
}
