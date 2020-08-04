package com.twitter.util.routing

import java.lang.{Iterable => JIterable}
import java.util.function.Function
import scala.jdk.CollectionConverters._

object Validator {

  /** [[Validator]] that does no validation and always returns empty error results */
  val None: Validator[Any] = new Validator[Any] {
    override def apply(routes: Iterable[Any]): Iterable[ValidationError] = Iterable.empty
  }

  /** Java-friendly creation of a [[Validator]] */
  def create[T](fn: Function[JIterable[T], JIterable[ValidationError]]): Validator[T] =
    new Validator[T] {
      override def apply(routes: Iterable[T]): Iterable[ValidationError] =
        fn(routes.asJava).asScala
    }

}

/**
 * Functional alias for determining whether all defined results are valid for a specific
 * [[Router router]] type.
 */
abstract class Validator[-Route] extends (Iterable[Route] => Iterable[ValidationError])
