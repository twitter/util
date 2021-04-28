package com.twitter.finagle.stats.exp

import com.twitter.finagle.stats.{MetricUnit, SourceRole, StatsReceiver, Unspecified}

/**
 * ExpressionSchema is builder class that construct an expression with its metadata.
 *
 * @param name  this is going to be an important query key when fetching expressions
 * @param labels  service related information, see [[ExpressionLabels]]
 * @param namespaces  a list of namespaces the expression belongs to, this is usually
 *                    used to indicate a tenant in a multi-tenancy systems or similar concepts.
 *                    For standalone services, this should be empty.
 * @param expr  class representation of the expression, see [[Expression]]
 * @param bounds  thresholds for this expression
 * @param description human-readable description of an expression's significance
 * @param unit the unit associated with the metrics value (milliseconds, megabytes, requests, etc)
 * @param exprQuery string representation of the expression
 */
case class ExpressionSchema private (
  name: String,
  labels: ExpressionLabels,
  expr: Expression,
  namespaces: Seq[String],
  bounds: Bounds,
  description: String,
  unit: MetricUnit,
  exprQuery: String) {
  def withBounds(bounds: Bounds): ExpressionSchema = copy(bounds = bounds)

  def withDescription(description: String): ExpressionSchema = copy(description = description)

  def withUnit(unit: MetricUnit): ExpressionSchema = copy(unit = unit)

  /**
   * Configure the expression with the given namespaces. The path can be composed of segments or
   * a single string. This is for multi-tenancy system tenants or similar concepts and should
   * remain unset for standalone services.
   */
  def withNamespaces(name: String*): ExpressionSchema =
    copy(namespaces = this.namespaces ++ name)

  private[finagle] def withRole(role: SourceRole): ExpressionSchema =
    copy(labels = labels.copy(role = role))

  private[finagle] def withServiceName(name: String): ExpressionSchema =
    copy(labels = labels.copy(serviceName = Some(name)))

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
    ExpressionSchema(
      name = name,
      labels = ExpressionLabels.empty,
      namespaces = Seq.empty,
      expr = expr,
      bounds = Unbounded.get,
      description = "Unspecified",
      unit = Unspecified,
      exprQuery = "")
}
