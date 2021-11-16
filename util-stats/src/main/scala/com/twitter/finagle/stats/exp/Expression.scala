package com.twitter.finagle.stats.exp

import com.twitter.finagle.stats.LoadedStatsReceiver
import com.twitter.finagle.stats.Metadata
import com.twitter.finagle.stats.MetricBuilder
import com.twitter.finagle.stats.MetricBuilder.HistogramType
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.stats.exp.Expression.HistogramComponent
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
    case MetricExpression(metricBuilder, _) => Set(metricBuilder.statsReceiver)
    case HistogramExpression(metricBuilder, _) => Set(metricBuilder.statsReceiver)
    case ConstantExpression(_) => Set.empty
    case StringExpression(_) => Set(LoadedStatsReceiver)
    case NoExpression => Set.empty
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
    metadata: Metadata,
    component: Either[HistogramComponent, Double]
  ): Expression = {
    metadata.toMetricBuilder match {
      case Some(metricBuilder) =>
        require(
          metricBuilder.metricType == HistogramType,
          "this method is for creating histogram expression")
        HistogramExpression(metricBuilder, component)
      case None =>
        NoExpression
    }
  }

  /**
   * Create an single expression wrapping a counter or gauge.
   * @param metadata  MetricBuilder instance
   * @param showRollup  If set true, inform the expression handler to render all tail metrics
   */
  def apply(metadata: Metadata, showRollup: Boolean = false): Expression = {
    metadata.toMetricBuilder match {
      case Some(metricBuilder) =>
        require(metricBuilder.metricType != HistogramType, "provide a component for histogram")
        MetricExpression(metricBuilder, showRollup)
      case None =>
        NoExpression
    }
  }

  /**
   * Represent an expression constructed from metrics in strings, user would need to provide
   * role and service name if needed to the [[ExpressionSchema]], otherwise remain unspecified.
   *
   * @note [[StringExpression]] is for conveniently creating expressions without getting a hold of
   *       [[StatsReceiver]] or [[MetricBuilder]], if they present or easy to access, users should
   *       prefer creating expression via Metric instances.
   *       StringExpressions are not guaranteed to be real metrics, please proceed with proper
   *       validation and testing.
   *
   * @param exprs a sequence of metrics in strings
   */
  def apply(exprs: Seq[String]): Expression = StringExpression(exprs)
}

/**
 * Metrics with their arithmetical(or others) calculations
 */
private[twitter] sealed trait Expression {
  final def plus(other: Expression): Expression = func("plus", other)

  final def minus(other: Expression): Expression = func("minus", other)

  final def divide(other: Expression): Expression = func("divide", other)

  final def multiply(other: Expression): Expression =
    func("multiply", other)

  @varargs
  def func(name: String, rest: Expression*): Expression =
    FunctionExpression(name, this +: rest)
}

/**
 * Represents a constant double number
 */
case class ConstantExpression private[exp] (repr: String) extends Expression

/**
 * Represents compound metrics and their arithmetical(or others) calculations
 */
case class FunctionExpression private[exp] (fnName: String, exprs: Seq[Expression])
    extends Expression {
  require(exprs.size != 0, "Functions must have at least 1 argument")
}

/**
 * Represents the leaf metrics
 */
case class MetricExpression private[exp] (metricBuilder: MetricBuilder, showRollup: Boolean)
    extends Expression

/**
 * Represent a histogram expression with specified component, for example the average, or a percentile
 * @param component either a [[HistogramComponent]] or a percentile in Double
 */
case class HistogramExpression private[exp] (
  metricBuilder: MetricBuilder,
  component: Either[HistogramComponent, Double])
    extends Expression

/**
 * Represent an expression constructed from metrics in strings
 * @see Expression#apply
 */
case class StringExpression private[exp] (exprs: Seq[String]) extends Expression

case object NoExpression extends Expression {
  @varargs
  override def func(name: String, rest: Expression*): Expression = NoExpression
}
