package com.twitter.finagle.stats.exp

import com.twitter.finagle.stats.{MetricUnit, SourceRole, StatsReceiver, Unspecified}

/**
 * ExpressionSchema is builder class that construct an expression with its metadata.
 *
 * @param name  this is going to be an important query key when fetching expressions
 * @param labels  service related information
 * @param namespace  a list of namespaces the expression belongs to, this is usually
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
  labels: Map[String, String],
  expr: Expression,
  namespace: Seq[String],
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
  def withNamespace(name: String*): ExpressionSchema =
    copy(namespace = this.namespace ++ name)

  def withLabel(labelName: String, labelValue: String): ExpressionSchema =
    copy(labels = labels + (labelName -> labelValue))

  private[finagle] def withRole(role: SourceRole): ExpressionSchema =
    withLabel(ExpressionSchema.Role, role.toString)

  private[finagle] def withServiceName(name: String): ExpressionSchema =
    withLabel(ExpressionSchema.ServiceName, name)

  def register(): Unit = {
    Expression.getStatsReceivers(expr).toSeq match {
      case Seq(sr) => sr.registerExpression(this)
      case srs: Seq[StatsReceiver] if srs.nonEmpty => srs.map(_.registerExpression(this))
      case _ => // should not happen
    }
  }

  def schemaKey(): ExpressionSchemaKey = {
    ExpressionSchemaKey(name, labels, namespace)
  }
}

/**
 * ExpressionSchemaKey is a class that exists to serve as a key into a Map of ExpressionSchemas.
 * It is simply a subset of the fields of the ExpressionSchema. Namely:
 * @param name
 * @param serviceName
 * @param namespaces
 */
case class ExpressionSchemaKey(
  name: String,
  labels: Map[String, String],
  namespaces: Seq[String])

// expose for testing in twitter-server
private[twitter] object ExpressionSchema {
  val Role: String = "role"
  val ServiceName: String = "service_name"
  val ProcessPath: String = "process_path"

  def apply(name: String, expr: Expression): ExpressionSchema =
    ExpressionSchema(
      name = name,
      labels = Map.empty,
      namespace = Seq.empty,
      expr = expr,
      bounds = Unbounded.get,
      description = "Unspecified",
      unit = Unspecified,
      exprQuery = "")
}
