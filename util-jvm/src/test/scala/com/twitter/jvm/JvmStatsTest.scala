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

  test("memory_pool expression is registered") {
    val memoryPoolExpressionLabels = expressions
      .filter {
        case (k, _) => k.name == "memory_pool"
      }.keySet.map(_.labels)

    assertLabels(memoryPoolExpressionLabels, "role", "jvm")
    assertLabels(memoryPoolExpressionLabels, "kind", "Code_Cache")
    assertLabels(memoryPoolExpressionLabels, "kind", "Compressed_Class_Space")
    assertLabels(memoryPoolExpressionLabels, "kind", "Metaspace")
    assertLabels(memoryPoolExpressionLabels, "kind", "PS_Eden_Space")
    assertLabels(memoryPoolExpressionLabels, "kind", "Heap")
    assertLabels(memoryPoolExpressionLabels, "kind", "PS_Survivor_Space")
    assertLabels(memoryPoolExpressionLabels, "kind", "PS_Old_Gen")
  }

  test("file_descriptors expression is registered") {
    val fileDescriptorsExpressionLabels = expressions
      .filter {
        case (k, _) => k.name == "file_descriptors"
      }.keySet.map(_.labels)

    assertLabels(fileDescriptorsExpressionLabels, "role", "jvm")
  }

  test("gc_cycles expression is registered") {
    val gcCyclesExpressionLabels = expressions
      .filter {
        case (k, _) => k.name == "gc_cycles"
      }.keySet.map(_.labels)
    assert(gcCyclesExpressionLabels.contains(Map("role" -> "jvm", "gc_pool" -> "PS_Scavenge")))
    assert(gcCyclesExpressionLabels.contains(Map("role" -> "jvm", "gc_pool" -> "PS_MarkSweep")))
  }

  test("gc_latency expression is registered") {
    val gcLatenciesExpressionLabels = expressions
      .filter {
        case (k, _) => k.name == "gc_latency"
      }.keySet.map(_.labels)
    assert(gcLatenciesExpressionLabels.contains(Map("role" -> "jvm", "gc_pool" -> "PS_Scavenge")))
    assert(gcLatenciesExpressionLabels.contains(Map("role" -> "jvm", "gc_pool" -> "PS_MarkSweep")))
  }

  test("jvm_uptime expression is registered") {
    val jvmUptimeExpressionLabels = expressions
      .filter {
        case (k, _) => k.name == "jvm_uptime"
      }.keySet.map(_.labels)

    assertLabels(jvmUptimeExpressionLabels, "role", "jvm")
  }

  private[this] def assertMetric(expressionSchema: ExpressionSchema): Unit =
    assert(expressionSchema.expr.isInstanceOf[MetricExpression])

  private[this] def assertLabels(
    labels: scala.collection.Set[Map[String, String]],
    key: String,
    value: String
  ): Unit =
    assert(labels.exists(_.get(key).get == value))
}
