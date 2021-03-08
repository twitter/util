package com.twitter.finagle.stats.exp

import com.twitter.finagle.stats.{MetricSchema, StatsReceiver}
import scala.annotation.varargs

private[twitter] sealed trait Expression {
  def plus(other: Expression): Expression = FunctionExpression("plus", Seq(this, other))

  def minus(other: Expression): Expression = FunctionExpression("minus", Seq(this, other))

  def divide(other: Expression): Expression = FunctionExpression("divide", Seq(this, other))

  def multiply(other: Expression): Expression =
    FunctionExpression("multiply", Seq(this, other))

  @varargs
  def func(name: String, rest: Expression*): Expression =
    FunctionExpression(name, this +: rest)
}

private[twitter] object Expression {
  def getStatsReceivers(expr: Expression): Set[StatsReceiver] = expr match {
    case FunctionExpression(_, exprs) =>
      exprs.foldLeft(Set.empty[StatsReceiver]) {
        case (acc, expr) =>
          getStatsReceivers(expr) ++ acc
      }
    case MetricExpression(schema) => Set(schema.metricBuilder.statsReceiver)
    case ConstantExpression(_, statsReceiver) => Set(statsReceiver)
  }
  def apply(num: Double, statsReceiver: StatsReceiver): Expression =
    ConstantExpression(num.toString, statsReceiver)
  def apply(schema: MetricSchema): Expression = MetricExpression(schema)
}

private[twitter] case class ConstantExpression(repr: String, statsReceiver: StatsReceiver)
    extends Expression

private[twitter] case class FunctionExpression(fnName: String, exprs: Seq[Expression])
    extends Expression {
  require(exprs.size != 0, "Functions must have at least 1 argument")
}

private[twitter] case class MetricExpression(schema: MetricSchema) extends Expression
