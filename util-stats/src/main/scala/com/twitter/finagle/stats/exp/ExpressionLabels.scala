package com.twitter.finagle.stats.exp

import com.twitter.finagle.stats.{NoRoleSpecified, SourceRole}

private[exp] object ExpressionLabels {
  val empty = ExpressionLabels(None, None, NoRoleSpecified)
}
private[exp] case class ExpressionLabels(
  processPath: Option[String],
  serviceName: Option[String],
  role: SourceRole)
