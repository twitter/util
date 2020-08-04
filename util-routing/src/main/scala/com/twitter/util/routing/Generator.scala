package com.twitter.util.routing

import java.lang.{Iterable => JIterable}
import java.util.function.BiFunction
import scala.jdk.CollectionConverters._

object Generator {

  /** Java-friendly creation of a [[Generator]] */
  def create[Input, Route, RouterType <: Router[Input, Route]](
    fn: BiFunction[String, JIterable[Route], RouterType]
  ): Generator[Input, Route, RouterType] = new Generator[Input, Route, RouterType] {
    override def apply(label: String, routes: Iterable[Route]): RouterType =
      fn(label, routes.asJava)
  }

}

/**
 * Functional alias for creating a [[RouterType router]] given a [[String label]] and
 * all of the defined [[Route routes]] for the router to be generated.
 */
abstract class Generator[Input, Route, +RouterType <: Router[Input, Route]]
    extends ((String, Iterable[Route]) => RouterType)
