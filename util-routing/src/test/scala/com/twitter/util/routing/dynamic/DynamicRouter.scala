package com.twitter.util.routing.dynamic

import com.twitter.util.routing.{Router, RouterBuilder}

private[routing] object DynamicRouter {
  def newBuilder(): DynamicTestRouterBuilder = new DynamicTestRouterBuilder()

  class DynamicTestRouterBuilder extends RouterBuilder[Request, DynamicRoute, DynamicRouter] {
    override protected def newRouter(routes: Iterable[DynamicRoute]): DynamicRouter =
      new DynamicRouter(routes.toSeq)
  }
}

// example to show how a router might be built using dynamic inputs
private[routing] class DynamicRouter(handlers: Seq[DynamicRoute])
    extends Router[Request, DynamicRoute] {

  // example showing how we can optimize dynamic routes to lookup by a property of the input
  private[this] val handlersByMethod: Map[Method, Seq[DynamicRoute]] = handlers.groupBy(_.method)

  override def label: String = "dynamic-test-router"

  override def routes: Iterable[DynamicRoute] = handlers

  override protected def find(input: Request): Option[DynamicRoute] =
    handlersByMethod.get(input.method) match {
      case Some(handlers) => handlers.find(_.canHandle(input))
      case _ => None
    }
}
