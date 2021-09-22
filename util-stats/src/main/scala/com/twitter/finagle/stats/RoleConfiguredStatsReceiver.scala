package com.twitter.finagle.stats

import com.twitter.finagle.stats.exp.ExpressionSchema
import com.twitter.util.Try

/**
 * A StatsReceiver proxy that configures all counter, stat, and gauge
 * SourceRoles to the passed in "role".
 *
 * @param self The underlying StatsReceiver to which translated names are passed
 * @param role the role used for SourceRole Metadata
 */
case class RoleConfiguredStatsReceiver(
  protected val self: StatsReceiver,
  role: SourceRole,
  name: Option[String] = None)
    extends StatsReceiverProxy {

  override def counter(metricBuilder: MetricBuilder): Counter = {
    self.counter(metricBuilder.withRole(role))
  }

  override def stat(metricBuilder: MetricBuilder): Stat = {
    self.stat(metricBuilder.withRole(role))
  }

  override def addGauge(metricBuilder: MetricBuilder)(f: => Float): Gauge = {
    self.addGauge(metricBuilder.withRole(role))(f)
  }

  override def registerExpression(expressionSchema: ExpressionSchema): Try[Unit] = {
    val configuredExprSchema = name match {
      case Some(serviceName) => expressionSchema.withRole(role).withServiceName(serviceName)
      case None => expressionSchema.withRole(role)
    }
    self.registerExpression(configuredExprSchema)
  }
}
