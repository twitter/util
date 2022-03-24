package com.twitter.finagle.stats.exp

import com.twitter.finagle.stats.exp.ExpressionNames._
import com.twitter.finagle.stats.Milliseconds
import com.twitter.finagle.stats.Percentage
import com.twitter.finagle.stats.Requests

/**
 * A utility object to create common [[ExpressionSchema]]s with user defined
 * [[Expression]]s.
 */
private[twitter] object DefaultExpression {

  /**
   * Create an [[ExpressionSchema]] to calculate success rate with a user
   * defined {@code success} [[Expression]] and a {@code failure}
   * [[Expression]].
   *
   * @param success an [[Expression]] to measure the success metric.
   * @param failure an [[Expression]] to measure the failure metric.
   * @return an [[ExpressionSchema]] that calculates success rate from success
   *         and failure metric. The default unit is [[Percentage]], the
   *         default bounds is [[MonotoneThresholds]] with operator as
   *         [[GreaterThan]], bad threshold as 99.5, good threshold as 99.97.
   *
   */
  def successRate(success: Expression, failure: Expression): ExpressionSchema =
    ExpressionSchema(
      successRateName,
      Expression(100).multiply(success.divide(success.plus(failure))))
      .withBounds(MonotoneThresholds(GreaterThan, 99.5, 99.97))
      .withUnit(Percentage)
      .withDescription(s"Default $successRateName expression")

  /**
   * Create an [[ExpressionSchema]] that calculates throughput with a user
   * defined {@code throughput} [[Expression]].
   *
   * @param throughput an [[Expression]] to measure the throughput metric
   * @return an [[ExpressionSchema]] that calculates the throughput. The
   *         default unit is [[Requests]].
   */
  def throughput(throughput: Expression): ExpressionSchema =
    requestUnitExpression(throughputName, throughput)

  /**
   * Create an [[ExpressionSchema]] that calculates failures with a user
   * defined {@code failures} [[Expression]].
   *
   * @param failures an [[Expression]] to measure the failures metric
   * @return an [[ExpressionSchema]] that calculates the failures. The
   *         default unit is [[Requests]].
   */
  def failures(failures: Expression): ExpressionSchema =
    requestUnitExpression(failuresName, failures)

  /**
   * Create an [[ExpressionSchema]] that calculates p99 latencies with a user
   * defined {@code latencyP99} [[Expression]].
   *
   * @param latencyP99 an [[Expression]] to measure the p99 latencies metric.
   * @return an [[ExpressionSchema]] that calculates the p99 latencies. The
   *         default unit is [[Milliseconds]].
   */
  def latency99(latencyP99: Expression): ExpressionSchema =
    ExpressionSchema(latencyName, latencyP99)
      .withUnit(Milliseconds)
      .withDescription("Default P99 latency expression")

  private[this] def requestUnitExpression(name: String, expression: Expression) =
    ExpressionSchema(name, expression)
      .withUnit(Requests)
      .withDescription(s"Default $name expression")
}

private[twitter] object ExpressionNames {
  val successRateName = "success_rate"
  val throughputName = "throughput"
  val latencyName = "latency"
  val failuresName = "failures"
}
