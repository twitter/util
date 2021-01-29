package com.twitter.util.routing

import java.lang.{Iterable => JIterable}
import scala.jdk.CollectionConverters._

/** The information needed to generate a [[Router]] via a [[Generator]]. */
case class RouterInfo[+Route](label: String, routes: Iterable[Route]) {

  /** Java-friendly constructor. */
  def this(label: String, jRoutes: JIterable[Route]) = this(label, jRoutes.asScala)

  /** Java-friendly accessor to the `Iterable[Route]` routes. */
  def jRoutes(): JIterable[_ <: Route] = routes.asJava
}
