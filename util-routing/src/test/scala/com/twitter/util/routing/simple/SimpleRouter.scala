package com.twitter.util.routing.simple

import com.twitter.util.routing.{Found, NotFound, Result, Router}
import com.twitter.util.{Future, Time}
import java.util.concurrent.atomic.AtomicInteger

private[routing] class SimpleRouter(routeMap: Map[String, SimpleRoute])
    extends Router[String, SimpleRoute]("test-router", routeMap.values) {

  val closedTimes: AtomicInteger = new AtomicInteger(0)

  protected def find(req: String): Result = routeMap.get(req) match {
    case Some(r) => Found(req, r)
    case _ => NotFound
  }

  override protected def closeOnce(deadline: Time): Future[Unit] = {
    closedTimes.incrementAndGet()
    Future.Done
  }

}
