package com.twitter.finagle.stats.exp

import com.twitter.finagle.stats.{NoRoleSpecified, SourceRole}

private[exp] object ExpressionLabels {
  val empty = ExpressionLabels(None, None, NoRoleSpecified)
}

/**
 * ExpressionLabels provide service-related information
 *
 * @param processPath a universal coordinate for the resource
 * @param serviceName a client label or a server label depends on the `role`
 * @param role whether the service is playing the part of client or server regarding this metric
 */
private[exp] case class ExpressionLabels(
  processPath: Option[String],
  serviceName: Option[String],
  role: SourceRole)
