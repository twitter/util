package com.twitter.util.testing

import com.twitter.finagle.stats.exp.ExpressionSchema
import com.twitter.finagle.stats.exp.ExpressionSchemaKey
import com.twitter.finagle.stats.InMemoryStatsReceiver
import org.scalatest.BeforeAndAfterEach
import org.scalatest.Suite
import org.scalatest.SuiteMixin
import scala.collection.mutable

/**
 * Testing trait which provides utilities for testing for proper
 * [[com.twitter.finagle.stats.exp.Expression]] registration
 */
trait ExpressionTestMixin extends SuiteMixin with BeforeAndAfterEach { this: Suite =>

  protected def downstreamLabel: Map[String, String]
  protected val sr: InMemoryStatsReceiver

  protected def nameToKey(
    name: String,
    labels: Map[String, String] = Map(),
    namespaces: Seq[String] = Seq()
  ): ExpressionSchemaKey =
    ExpressionSchemaKey(name, downstreamLabel ++ labels, namespaces)

  override protected def beforeEach(): Unit = {
    sr.clear()
    super.beforeEach()
  }

  /**
   * Given the name, labels, and namespaces that make up a given ExpressionSchemaKey,
   * assert that expression is registered on the StatsReceiver.
   */
  protected def assertExpressionIsRegistered(
    name: String,
    labels: Map[String, String] = Map(),
    namespaces: Seq[String] = Seq()
  ): Unit = {
    assert(sr.expressions.contains(nameToKey(name, labels, namespaces)))
  }

  /**
   * Returns a collection of all registered expressions on the provided StatsReceiver
   */
  protected def getMetricsReview: mutable.Map[ExpressionSchemaKey, ExpressionSchema] = {
    sr.expressions
  }

  /**
   * Given a collection of expected ExpressionSchemaKeys, ensure that collection matches what is
   * registered.
   *
   * @param expected a Set of [[ExpressionSchemaKey]]s that should be registered on the StatsReceiver
   */
  protected def assertExpressionsAsExpected(expected: Set[ExpressionSchemaKey]): Unit = {
    assert(sr.expressions.keySet == expected)
  }
}
