package com.twitter.finagle.stats.exp

import com.twitter.finagle.stats.MetricUnit
import com.twitter.finagle.stats.Milliseconds
import com.twitter.finagle.stats.Percentage
import com.twitter.finagle.stats.Requests
import org.scalatest.funsuite.AnyFunSuite

class DefaultExpressionTest extends AnyFunSuite {
  test("success rate expression") {
    val successRate = DefaultExpression.successRate(Expression(97), Expression(3))
    assertExpression(successRate, ExpressionNames.successRateName, Percentage)
    assert(successRate.bounds == MonotoneThresholds(GreaterThan, 99.5, 99.97))
  }

  test("throughput") {
    val throughput = DefaultExpression.throughput(Expression(7777))
    assertExpression(throughput, ExpressionNames.throughputName, Requests)
  }

  test("failures") {
    val failures = DefaultExpression.failures(Expression(0))
    assertExpression(failures, ExpressionNames.failuresName, Requests)
  }

  test("latency p99") {
    val latencyP99 = DefaultExpression.latency99(Expression(1000))
    assertExpression(latencyP99, ExpressionNames.latencyName, Milliseconds)
  }

  private[this] def assertExpression(
    expression: ExpressionSchema,
    name: String,
    unit: MetricUnit
  ): Unit = {
    assert(expression.name == name)
    assert(expression.unit == unit)
  }
}
