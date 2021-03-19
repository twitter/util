package com.twitter.finagle.stats.exp

import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

/**
 * Thresholds are specified per-expression. The default is Unbounded.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes(
  Array(
    new Type(value = classOf[AcceptableRange], name = "range"),
    new Type(value = classOf[MonotoneThresholds], name = "monotone"),
    new Type(value = classOf[Unbounded], name = "unbounded")
  )
)
sealed trait Bounds

/**
 * The state is considered healthy when the expression computation is inside this range.
 * Health status and expression computation are not necessary linearly correlated.
 */
case class AcceptableRange(lowerBoundInclusive: Double, upperBoundExclusive: Double) extends Bounds

/**
 * Health status and expression computation are linearly correlated, and the healthier
 * direction is indicated by [[operator]].
 *
 * [lowerBoundInclusive, badThreshold]  bad
 * [badThreshold, goodThreshold]        ok
 * [goodThreshold, upperBoundExclusive] good
 *
 * @note all ranges are lower inclusive and upper exclusive.
 *
 * @param operator  GreaterThan or LessThan
 * @param lowerBoundInclusive Optional, undefined meaning unbounded lower bound.
 * @param upperBoundExclusive Optional, undefined meaning unbounded upper bound.
 */
case class MonotoneThresholds(
  operator: Operator,
  badThreshold: Double,
  goodThreshold: Double,
  lowerBoundInclusive: Option[Double] = None,
  upperBoundExclusive: Option[Double] = None)
    extends Bounds

// does not use case object because it cannot provide `Class` for @JsonSubTypes
// see https://github.com/scala/scala/pull/9279
object Unbounded {
  val get = Unbounded()
}
case class Unbounded() extends Bounds
