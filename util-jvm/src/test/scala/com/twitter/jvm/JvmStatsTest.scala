package com.twitter.jvm

import com.twitter.finagle.stats.InMemoryStatsReceiver
import com.twitter.finagle.stats.exp.ExpressionSchema
import com.twitter.finagle.stats.exp.MetricExpression
import org.scalatest.funsuite.AnyFunSuite

class JvmStatsTest extends AnyFunSuite {

  private[this] val receiver = new InMemoryStatsReceiver()
  JvmStats.register(receiver)
  private[this] val expressions = receiver.getAllExpressionsWithLabel(ExpressionSchema.Role, "jvm")

  test("expressions are instrumented") {
    expressions.foreach {
      case (_, expressionSchema) =>
        assertMetric(expressionSchema)
    }
  }

  test("expressions are registered") {
    assertExpressionLabels("memory_pool")
    assertExpressionLabels("file_descriptors")
    assertExpressionLabels("gc_cycles")
    assertExpressionLabels("gc_latency")
    assertExpressionLabels("jvm_uptime")
  }

  private[this] def assertMetric(expressionSchema: ExpressionSchema): Unit =
    assert(expressionSchema.expr.isInstanceOf[MetricExpression])

  private[this] def assertExpressionLabels(expressionKey: String): Unit =
    assert(expressions.keysIterator.exists(_.name == expressionKey))
}
