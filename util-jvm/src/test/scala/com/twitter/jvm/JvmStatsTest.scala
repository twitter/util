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

    val expressions = receiver.getAllExpressionsWithLabel(ExpressionSchema.Role, "jvm")

    assert(expressions.size == 7)

    expressions.foreach { (x: (ExpressionSchemaKey, ExpressionSchema)) =>
      assertMetric(x._2)
    }
  }
}
