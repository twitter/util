package com.twitter.util.routing

import com.twitter.util.logging.Logging
import com.twitter.util.{Closable, ClosableOnce, Future, Time}
import java.lang.{Iterable => JIterable}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

/**
 * A generic interface for routing an input to an optional matching route.
 *
 * @param label A label used for identifying this Router (i.e. for distinguishing between [[Router]]
 *              instances in error messages or for StatsReceiver scope).
 * @param routes All of the `Route` routes contained within this [[Router]]
 *
 *               This property is used to linearly determine the routes when `close()` is called.
 *               It may also be used as a way to determine what routes are defined within a [[Router]]
 *               (i.e. for generating meaningful error messages). This property does not imply
 *               any relationship with [[apply]] or [[find]] or that a [[Router]]'s runtime behavior
 *               needs to be linear.
 * @tparam Input The Input type used when determining a route destination
 * @tparam Route The resulting route type. A `Route` may have dynamic
 *               properties and may be found to satisfy multiple `Input`
 *               instances. As there may not be a 1-to-1 mapping of
 *               `Input` to `Route`, it is encouraged that a `Route`
 *               encapsulates its relationship to an `Input`.
 *
 *               An example to consider would be an HTTP [[Router]].
 *               An HTTP request contains a Method (i.e. GET, POST) and
 *               a URI. A [[Router]] for a REST API may match "GET /users/{id}"
 *               for multiple HTTP requests (i.e. "GET /users/123" AND "GET /users/456" would map
 *               to the same destination `Route`). As a result, the `Route` in this example
 *               should be aware of the method, path, and and the destination/logic responsible
 *               for handling an `Input` that matches.
 *
 *               Another property to consider for a `Route` is "uniqueness". A [[Router]] should be
 *               able to determine either:
 *                 a) a single `Route` for an `Input`
 *                 b) no `Route` for an `Input`
 *               A [[Router]] should NOT be expected to return multiple routes for an `Input`.
 *
 * @note A [[Router]] should be considered immutable unless explicitly noted.
 */
abstract class Router[Input, +Route](val label: String, val routes: Iterable[Route])
    extends (Input => Result)
    with ClosableOnce
    with Logging {

  /** Java-friendly constructor */
  def this(label: String, routes: JIterable[Route]) = this(label, routes.asScala)

  /**
   * Attempt to route an `Input` to a `Route` route known by this Router.
   *
   * @param input The `Input` to use for determining an available route
   *
   * @return [[Found `Found(input: Input, route: Route)`]] if a route is found to match,
   *         [[NotFound]] if there is no match, or
   *         [[RouterClosed]] if `close()` has been initiated on this [[Router]] and subsequent
   *           routing attempts are received.
   */
  final def apply(input: Input): Result =
    if (isClosed) RouterClosed
    else find(input)

  /**
   * Logic used to determine if this [[Router]] contains a `Route`
   * for an `Input`.
   *
   * @param input The `Input` to determine a route for.
   *
   * @return [[Found `Found(input: Input, route: Route)`]] if a matching route is defined,
   *         [[NotFound]] if a route destination is not defined for the `Input`.
   *
   * @note This method is only meant to be called within the [[Router]]'s `apply`, which
   *       handles lifecycle concerns. It should not be accessed directly.
   */
  protected def find(input: Input): Result

  /**
   * @note NonFatal exceptions encountered when calling `close()` on a `Route` will be
   *       suppressed and Fatal exceptions will take on the same exception behavior of a `Future.join`.
   */
  protected def closeOnce(deadline: Time): Future[Unit] =
    Future.join(routes.map(closeIfClosable(_, deadline)).toSeq)

  private[this] def closeIfClosable(route: Route, deadline: Time): Future[Unit] = route match {
    case closable: Closable =>
      closable.close(deadline).rescue {
        case NonFatal(e) =>
          logger.warn(
            s"Error encountered when attempting to close route '$route' in router '$label'",
            e)
          Future.Done
      }
    case _ =>
      Future.Done
  }
}
