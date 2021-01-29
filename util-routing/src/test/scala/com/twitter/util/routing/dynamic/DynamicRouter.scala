package com.twitter.util.routing.dynamic

import com.twitter.util.routing.{
  Found,
  Generator,
  RouterInfo,
  NotFound,
  Result,
  Router,
  RouterBuilder
}

private[routing] object DynamicRouter {
  def newBuilder(): RouterBuilder[Request, DynamicRoute, DynamicRouter] =
    RouterBuilder.newBuilder(new Generator[Request, DynamicRoute, DynamicRouter] {
      def apply(labelAndRoutes: RouterInfo[DynamicRoute]): DynamicRouter =
        new DynamicRouter(labelAndRoutes.routes.toSeq)
    })
}

// example to show how a router might be built using dynamic inputs
private[routing] class DynamicRouter(handlers: Seq[DynamicRoute])
    extends Router[Request, DynamicRoute]("dynamic-test-router", handlers) {

  // example showing how we can optimize dynamic routes to lookup by a property of the input
  private[this] val handlersByMethod: Map[Method, Seq[DynamicRoute]] = handlers.groupBy(_.method)

  protected def find(input: Request): Result =
    handlersByMethod.get(input.method) match {
      case Some(handlers) =>
        handlers.find(_.canHandle(input)) match {
          case Some(r) => Found(input, r)
          case _ => NotFound
        }
      case _ => NotFound
    }
}
