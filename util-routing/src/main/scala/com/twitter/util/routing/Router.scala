package com.twitter.util.routing

import com.twitter.logging.Logger
import com.twitter.util.{Closable, ClosableOnce, Future, Time}
import scala.util.control.NonFatal

object Router {
  val Log = Logger.get(Router.getClass)
}

/**
 * A generic interface for routing an input to an optional matching route.
 *
 * @tparam Input The Input type used when determining a route destination
 * @tparam Route The resulting route type. A [[Route]] may have dynamic
 *               properties and may be found to satisfy multiple [[Input]]
 *               instances. As there may not be a 1-to-1 mapping of
 *               [[Input]] to [[Route]], it is encouraged that a [[Route]]
 *               encapsulates its relationship to an [[Input]].
 *
 *               An example to consider would be an HTTP [[Router]].
 *               An HTTP request contains a Method (i.e. GET, POST) and
 *               a URI. A [[Router]] for a REST API may match "GET /users/{id}"
 *               for multiple HTTP requests (i.e. "GET /users/123" AND "GET /users/456" would map
 *               to the same destination [[Route]]). As a result, the [[Route]] in this example
 *               should be aware of the method, path, and and the destination/logic responsible
 *               for handling an [[Input]] that matches.
 *
 *               Another property to consider for a [[Route]] is "uniqueness". It may or may not
 *               be desirable for a [[Router]] to contain multiple routes that correspond to
 *               the same [[Input]].
 *
 * @note A [[Router]] should be considered immutable unless explicitly noted.
 */
trait Router[Input, Route] extends (Input => Option[Route]) with ClosableOnce {
  import Router.Log

  /** The [[Logger]] to use for this [[Router]]. Can be overridden to customize. */
  protected def logger: Logger = Log

  /**
   * A label used for identifying this Router (i.e. for distinguishing between [[Router]] instances
   * in error messages or for StatsReceiver scope).
   */
  def label: String

  /**
   * All of the [[Route]] routes contained within this [[Router]]
   *
   * @note This property is used to linearly determine the routes when [[closed()]] is called.
   *       It may also be used as a way to determine what routes are defined within a [[Router]]
   *       (i.e. for generating meaningful error messages). This property does not imply
   *       any relationship with [[apply]] or [[find]] or that a [[Router]]'s runtime behavior
   *       needs to be linear.
   */
  def routes: Iterable[Route]

  /**
   * Attempt to route an [[Input]] to a [[Route]] route known by this Router.
   *
   * @param input The [[Input]] to use for determining an available route
   *
   * @return `Some(Route)` if a route is found to match, `None` otherwise
   *
   * @note A [[ClosedRouterException]] will be thrown if [[close]] has been initiated on this
   *       [[router]] and subsequent routing attempts are received.
   */
  final def apply(input: Input): Option[Route] =
    if (isClosed) throw ClosedRouterException(this)
    else find(input)

  /**
   * Logic used to determine if this [[Router]] contains a [[Route]]
   * for an [[Input]].
   *
   * @param input The [[Input]] to determine a route for.
   *
   * @return `Some(Route)` if a matching route is defined, `None` if a route destination is
   *         not defined for the [[Input]].
   *
   * @note This method is only meant to be called within the [[Router]]'s [[apply]], which
   *       handles lifecycle concerns. It should not be accessed directly.
   */
  protected def find(input: Input): Option[Route]

  /**
   * @inheritdoc
   * @note NonFatal exceptions encountered when calling [[close()]] on a [[Route]] will be
   *       suppressed and Fatal exceptions will take on the same exception behavior of a
   *       [[Future.join]].
   */
  override protected def closeOnce(deadline: Time): Future[Unit] =
    Future.join(routes.map(closeIfClosable(_, deadline)).toSeq)

  private[this] def closeIfClosable(route: Route, deadline: Time): Future[Unit] = route match {
    case closable: Closable =>
      closable.close(deadline).rescue {
        case NonFatal(e) =>
          logger.warning(
            e,
            s"Error encountered when attempting to close route '$route' in router '$label'")
          Future.Done
      }
    case _ =>
      Future.Done
  }
}
