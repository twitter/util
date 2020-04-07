package com.twitter.util.routing.simple

import com.twitter.util.routing.Router
import com.twitter.util.{Future, Time}
import java.util.concurrent.atomic.AtomicInteger

private[routing] class SimpleRouter(routeMap: Map[String, SimpleRoute])
    extends Router[String, SimpleRoute] {

  override def label: String = "test-router"

  val closedTimes: AtomicInteger = new AtomicInteger(0)

  def find(req: String): Option[SimpleRoute] = routeMap.get(req)

  override protected def closeOnce(deadline: Time): Future[Unit] = {
    closedTimes.incrementAndGet()
    Future.Done
  }

  override def routes: Iterable[SimpleRoute] = routeMap.values

}
