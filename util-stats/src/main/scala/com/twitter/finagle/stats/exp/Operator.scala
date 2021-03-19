package com.twitter.finagle.stats.exp

import com.fasterxml.jackson.annotation.JsonValue

/**
 * Represent Metric Expression thresholds operator.
 */
sealed trait Operator {
  def isIncreasing: Boolean
  @JsonValue
  def toJson: String
}

case object GreaterThan extends Operator {
  def toJson: String = ">"
  val isIncreasing: Boolean = true
}

case object LessThan extends Operator {
  def toJson: String = "<"
  val isIncreasing: Boolean = false
}
