package com.twitter.util.routing

import scala.util.control.NoStackTrace

/**
 * Exception thrown when an attempt to route an `Input` to a [[Router]] where `close` has been
 * initiated on the [[Router]].
 *
 * @param router The [[Router]] where `close` has already been initiated.
 */
case class ClosedRouterException(router: Router[_, _])
    extends RuntimeException(s"Attempting to route on a closed router with label '${router.label}'")
    with NoStackTrace
