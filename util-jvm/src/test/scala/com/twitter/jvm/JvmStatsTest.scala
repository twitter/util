package com.twitter.jvm

import com.twitter.finagle.stats.InMemoryStatsReceiver
import com.twitter.finagle.stats.exp.{ExpressionSchema, ExpressionSchemaKey, MetricExpression}
import org.scalatest.funsuite.AnyFunSuite

class JvmStatsTest extends AnyFunSuite {

  def assertMetric(expressionSchema: ExpressionSchema): Unit = {
    assert(expressionSchema.expr.isInstanceOf[MetricExpression])
  }

  test("expressions are instrumented") {
    val receiver = new InMemoryStatsReceiver()
    JvmStats.register(receiver)

    val role = Map((ExpressionSchema.Role, "jvm"))
    val markSweepLabel = role ++ Map(("gc_pool", "PS_MarkSweep"))
    val scavengeLabel = role ++ Map(("gc_pool", "PS_Scavenge"))

    val uptimeKey = ExpressionSchemaKey("jvm_uptime", role, Seq())
    val cyclesKey = ExpressionSchemaKey("gc_cycles", role, Seq())
    val latencyKey = ExpressionSchemaKey("gc_latency", role, Seq())

    assertMetric(receiver.expressions(uptimeKey))
    assertMetric(receiver.expressions(cyclesKey))
    assertMetric(receiver.expressions(latencyKey))

    assertMetric(receiver.expressions(ExpressionSchemaKey("gc_cycles", markSweepLabel, Seq())))
    assertMetric(receiver.expressions(ExpressionSchemaKey("gc_latency", markSweepLabel, Seq())))
    assertMetric(receiver.expressions(ExpressionSchemaKey("gc_cycles", scavengeLabel, Seq())))
    assertMetric(receiver.expressions(ExpressionSchemaKey("gc_latency", scavengeLabel, Seq())))
  }
}
