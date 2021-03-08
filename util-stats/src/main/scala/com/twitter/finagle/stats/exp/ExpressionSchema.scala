package com.twitter.finagle.stats.exp

import com.twitter.finagle.stats.{MetricUnit, SourceRole, StatsReceiver, Unspecified}

case class ExpressionSchema private (
  name: String,
  labels: ExpressionLabels,
  expr: Expression,
  bounds: Bounds,
  description: String,
  unit: MetricUnit) {
  def withBounds(bounds: Bounds): ExpressionSchema = copy(bounds = bounds)

  def withDescription(description: String): ExpressionSchema = copy(description = description)

  def withUnit(unit: MetricUnit): ExpressionSchema = copy(unit = unit)

  private[finagle] def withRole(role: SourceRole): ExpressionSchema =
    copy(labels = labels.copy(role = role))

  def register(): Unit = {
    Expression.getStatsReceivers(expr).toSeq match {
      case Seq(sr) => sr.registerExpression(this)
      case srs: Seq[StatsReceiver] if srs.nonEmpty => srs.map(_.registerExpression(this))
      case _ => // should not happen
    }
  }
}

// expose for testing in twitter-server
private[twitter] object ExpressionSchema {
  def apply(name: String, expr: Expression): ExpressionSchema =
    ExpressionSchema(name, ExpressionLabels.empty, expr, Unbounded, "Unspecified", Unspecified)
}
