package com.twitter.finagle.stats.exp

sealed trait Bounds

case class AcceptableRange(lowerBoundInclusive: Double, upperBoundExclusive: Double) extends Bounds

case class MonotoneThreshold(
  operator: Operator,
  badThreshold: Double,
  goodThreshold: Double,
  lowerBoundInclusive: Option[Double] = None,
  upperBoundExclusive: Option[Double] = None)
    extends Bounds

case object Unbounded extends Bounds
