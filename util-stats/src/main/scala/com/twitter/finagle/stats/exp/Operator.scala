package com.twitter.finagle.stats.exp

sealed trait Operator {
  def isIncreasing: Boolean
}

case object GreaterThan extends Operator {
  override def toString: String = ">"
  val isIncreasing: Boolean = true
}

case object LessThan extends Operator {
  override def toString: String = "<"
  val isIncreasing: Boolean = false
}
