package com.twitter.finagle.stats.exp

import com.twitter.finagle.stats.MetricBuilder.HistogramType
import com.twitter.finagle.stats.exp.Expression.HistogramComponent
import com.twitter.finagle.stats.{MetricBuilder, StatsReceiver}
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.varargs

private[twitter] object Expression {

  sealed trait HistogramComponent
  case object Min extends HistogramComponent
  case object Max extends HistogramComponent
  case object Avg extends HistogramComponent
  case object Sum extends HistogramComponent
  case object Count extends HistogramComponent

  private[exp] def getStatsReceivers(expr: Expression): Set[StatsReceiver] = expr match {
    case FunctionExpression(_, exprs) =>
      exprs.foldLeft(Set.empty[StatsReceiver]) {
        case (acc, expr) =>
          getStatsReceivers(expr) ++ acc
      }
    case MetricExpression(metricBuilder) => Set(metricBuilder.statsReceiver)
    case HistogramExpression(metricBuilder, _) => Set(metricBuilder.statsReceiver)
    case ConstantExpression(_) => Set.empty
  }

  /**
   * Create a expression with a constant number in double
   */
  def apply(num: Double): Expression = ConstantExpression(num.toString)

  /**
   * Create a histogram expression
   * @param component the histogram component either a [[HistogramComponent]] for Left
   *                  or a percentile in Double for Right.
   */
  def apply(
    metricBuilder: MetricBuilder,
    component: Either[HistogramComponent, Double]
  ): HistogramExpression = {
    require(
      metricBuilder.metricType == HistogramType,
      "this method is for creating histogram expression")
    HistogramExpression(metricBuilder, component)
  }

  /**
   * Create an single expression wrapping a counter or gauge.
   */
  def apply(metricBuilder: MetricBuilder): Expression = {
    require(metricBuilder.metricType != HistogramType, "provide a component for histogram")
    MetricExpression(metricBuilder)
  }

  // utility methods shared by Metrics.scala and InMemoryStatsReceiver
  private[stats] def replaceExpression(
    expression: Expression,
    metricBuilders: ConcurrentHashMap[Int, MetricBuilder]
  ): Expression = {
    expression match {
      case f @ FunctionExpression(_, exprs) =>
        f.copy(exprs = exprs.map(replaceExpression(_, metricBuilders)))
      case m @ MetricExpression(metricBuilder) if metricBuilder.kernel.isDefined =>
        val builder = metricBuilders.get(metricBuilder.kernel.get)
        if (builder != null) m.copy(builder)
        else m
      case h @ HistogramExpression(metricBuilder, _) if metricBuilder.kernel.isDefined =>
        val builder = metricBuilders.get(metricBuilder.kernel.get)
        if (builder != null) h.copy(builder)
        else h
      case otherExpression => otherExpression
    }
  }

  private[this] def reformSchema(builder: MetricBuilder): MetricBuilder = builder
}

/**
 * Metrics with their arithmetical(or others) calculations
 */
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

/**
 * Represents a constant double number
 */
case class ConstantExpression private (repr: String) extends Expression

/**
 * Represents compound metrics and their arithmetical(or others) calculations
 */
case class FunctionExpression private (fnName: String, exprs: Seq[Expression]) extends Expression {
  require(exprs.size != 0, "Functions must have at least 1 argument")
}

/**
 * Represents the leaf metrics
 */
case class MetricExpression private (metricBuilder: MetricBuilder) extends Expression

/**
 * Represent a histogram expression with specified component, for example the average, or a percentile
 * @param component either a [[HistogramComponent]] or a percentile in Double
 */
case class HistogramExpression private (
  metricBuilder: MetricBuilder,
  component: Either[HistogramComponent, Double])
    extends Expression
