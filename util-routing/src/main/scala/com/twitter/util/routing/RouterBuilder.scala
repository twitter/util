package com.twitter.util.routing

import scala.collection.immutable.Queue

object RouterBuilder {

  def newBuilder[Input, Route, RouterType <: Router[Input, Route]](
    generator: Generator[Input, Route, RouterType]
  ): RouterBuilder[Input, Route, RouterType] =
    RouterBuilder(generator)

}

/**
 * Utility for building and creating [[Router routers]]. The resulting [[Router router]]
 * should be considered immutable, unless the [[Router router's]] implementation
 * explicitly states otherwise.
 *
 * @tparam Input The [[Router router's]] `Input` type.
 * @tparam Route The [[Router router's]] destination `Route` type. It is recommended that the `Route`
 *               is a self-contained/self-describing type for the purpose of validation via
 *               the [[Validator]]. Put differently, the `Route` should know of the
 *               `Input` that maps to itself.
 * @tparam RouterType The type of [[Router]] to build.
 */
case class RouterBuilder[Input, Route, +RouterType <: Router[Input, Route]] private (
  private val generator: Generator[Input, Route, RouterType],
  private val label: String = "router",
  private val routes: Queue[Route] = Queue.empty,
  private val validator: Validator[Route] = Validator.None) {

  /** Set the [[Router.label label]] for the resulting [[Router router]] */
  def withLabel(label: String): RouterBuilder[Input, Route, RouterType] =
    copy(label = label)

  /**
   * Add the route to the routes that will be present in
   * the [[Router router]] when [[newRouter]] is called.
   */
  def withRoute(route: Route): RouterBuilder[Input, Route, RouterType] =
    copy(routes = routes :+ route)

  /**
   * Configure the route validation logic for this builder
   */
  def withValidator(
    validator: Validator[Route]
  ): RouterBuilder[Input, Route, RouterType] =
    copy(validator = validator)

  /** Generate a new [[Router router]] from the defined routes */
  def newRouter(): RouterType = {
    val failures = validator(routes)

    if (failures.nonEmpty) throw ValidationException(failures)
    generator(label, routes)
  }

}
