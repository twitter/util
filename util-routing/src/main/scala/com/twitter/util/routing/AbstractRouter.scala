package com.twitter.util.routing

import com.twitter.util.Try
import java.lang.{Iterable => JIterable}
import java.util.Optional
import scala.jdk.CollectionConverters._

/**
 * Java-friendly version of [[Router]]
 *
 * @param label A label used for identifying this Router (i.e. for distinguishing between [[Router]]
 *              instances in error messages or for StatsReceiver scope).
 * @param jRoutes All of the `Route` routes contained within this [[Router]], represented as
 *                a Java `Iterable`.
 * @tparam Input The Input type used to determine a route destination
 * @tparam Route The output/resulting route type
 */
abstract class AbstractRouter[Input, Route](val label: String, jRoutes: JIterable[Route])
    extends Router[Input, Route] {

  /** Java-friendly version of [[apply]] */
  final def route(input: Input): Optional[Route] = apply(input) match {
    case Some(route) => Optional.of(route)
    case _ => Optional.empty()
  }

  /** Java-friendly version of [[find]] */
  protected def findAny(input: Input): Optional[Route]

  override final protected def find(input: Input): Option[Route] = {
    val found = findAny(input)
    Try(found.get()).toOption
  }

  override final val routes: Iterable[Route] = jRoutes.asScala

}
