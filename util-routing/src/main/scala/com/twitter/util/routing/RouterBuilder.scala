package com.twitter.util.routing

import scala.collection.mutable.ArrayBuffer

/**
 * Contract for a [[Router]] builder. The resulting [[RouterType]]
 * should be considered immutable, unless the [[RouterType]] implementation
 * explicitly states otherwise.
 *
 * @param routeValidator A function that will be used to validate a [[Route]] when it is
 *                       added to this builder via [[withRoute]]. If a [[Route]] is not
 *                       valid, the [[routeValidator]] should throw a [[RouteValidationException]].
 *
 *                       Ex:
 *                       {{{
 *                         case class ExampleRoute(in: Int, out: Int)
 *
 *                         val routeValidator: ExampleRoute => Unit = {
 *                           case ExampleRoute(in, out) if in < out =>
 *                             // route is valid
 *                             ()
 *                           case ExampleRoute(in, out) =>
 *                             // route is not valid
 *                             throw InvalidRouteException(s"'$in' is not less than '$out'")
 *                         }
 *                       }}}
 * @tparam Input The [[Router]]'s `Input` type.
 * @tparam Route The [[Router]]'s destination `Route` type. It is recommended that the `Route`
 *               is a self-contained/self-describing type for the purpose of validation via
 *               the [[routeValidator]]. Put differently, the `Route` should know of the
 *               `Input` that maps to itself.
 * @tparam RouterType The type of [[Router]] to build.
 */
abstract class RouterBuilder[Input, Route, RouterType <: Router[Input, Route]](
  routeValidator: Route => Unit) {

  def this() = this((_: Route) => ())

  private[this] val routes: ArrayBuffer[Route] = new ArrayBuffer[Route]()

  /**
   * Add the [[route]] to the routes that will be present in
   * the [[RouterType]] when [[newRouter()]] is called.
   *
   * @param route The [[Route]] to be added.
   *
   * @return
   *
   * @note The [[routeValidator]] will be exercised with the [[route]]
   *       and may throw an [[RouteValidationException]]
   */
  def withRoute(route: Route): this.type = synchronized {
    routeValidator(route)
    routes += route
    this
  }

  /**
   * Generate a new [[RouterType]] from the defined [[routes]].
   * @param routes The [[Route]]s to be used for building the resulting [[RouterType]].
   * @return The resulting [[RouterType]] made up of the underlying [[routes]].
   */
  protected def newRouter(routes: Iterable[Route]): RouterType

  /**
   * @return A new [[RouterType]] made up of this builder's defined routes.
   */
  def newRouter(): RouterType = newRouter(routes)

}
