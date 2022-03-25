package com.twitter.finagle.stats.exp

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.stats.MetricBuilder.CounterType
import com.twitter.finagle.stats.MetricBuilder.HistogramType
import com.twitter.finagle.stats._
import com.twitter.util.Stopwatch
import com.twitter.util.Time
import com.twitter.util.TimeControl
import scala.util.control.NonFatal
import org.scalatest.funsuite.AnyFunSuite

class ExpressionSchemaTest extends AnyFunSuite {
  trait Ctx {
    val sr = new InMemoryStatsReceiver
    val clientSR = RoleConfiguredStatsReceiver(sr, Client, Some("downstream"))

    val successMb =
      MetricBuilder(name = Seq("success"), metricType = CounterType, statsReceiver = clientSR)
    val failuresMb =
      MetricBuilder(name = Seq("failures"), metricType = CounterType, statsReceiver = clientSR)
    val latencyMb =
      MetricBuilder(name = Seq("latency"), metricType = HistogramType, statsReceiver = clientSR)
    val sum = Expression(successMb).plus(Expression(failuresMb))

    def slowQuery(ctl: TimeControl, succeed: Boolean): Unit = {
      ctl.advance(50.milliseconds)
      if (!succeed) {
        throw new Exception("boom!")
      }
    }

    val downstreamLabel =
      Map(ExpressionSchema.Role -> Client.toString, ExpressionSchema.ServiceName -> "downstream")

    protected[this] def nameToKey(
      name: String,
      labels: Map[String, String] = Map(),
      namespaces: Seq[String] = Seq()
    ): ExpressionSchemaKey =
      ExpressionSchemaKey(name, downstreamLabel ++ labels, namespaces)
  }

  test("Demonstrate an end to end example of using metric expressions") {
    new Ctx {
      val successCounter = clientSR.counter(successMb)
      val failuresCounter = clientSR.counter(failuresMb)
      val latencyStat = clientSR.stat(latencyMb)

      val successRate = ExpressionSchema("success_rate", Expression(successMb).divide(sum))
        .withBounds(MonotoneThresholds(GreaterThan, 99.5, 99.75))
        .withUnit(Percentage)
        .withDescription("The success rate of the slow query")

      val latency =
        ExpressionSchema("latency", Expression(latencyMb, HistogramComponent.Percentile(0.99)))
          .withUnit(Milliseconds)
          .withDescription("The latency of the slow query")

      successRate.build()
      latency.build()

      def runTheQuery(succeed: Boolean): Unit = {
        Time.withCurrentTimeFrozen { ctl =>
          val elapsed = Stopwatch.start()
          try {
            slowQuery(ctl, succeed)
            successCounter.incr()
          } catch {
            case NonFatal(exn) =>
              failuresCounter.incr()
          } finally {
            latencyStat.add(elapsed().inMilliseconds.toFloat)
          }
        }
      }
      runTheQuery(true)
      runTheQuery(false)

      assert(sr.expressions(nameToKey("success_rate")).name == successRate.name)
      assert(sr.expressions(nameToKey("success_rate")).expr == successRate.expr)
      assert(sr.expressions(nameToKey("latency", Map("bucket" -> "p99"))).name == latency.name)
      assert(sr.expressions(nameToKey("latency", Map("bucket" -> "p99"))).expr == latency.expr)

      assert(sr.counters(Seq("success")) == 1)
      assert(sr.counters(Seq("failures")) == 1)
      assert(sr.stats(Seq("latency")) == Seq(50, 50))

      assert(
        sr.expressions(nameToKey("success_rate")).labels(ExpressionSchema.Role) == Client.toString)
    }
  }

  test("create a histogram expression requires a component") {
    new Ctx {
      val e = intercept[IllegalArgumentException] {
        ExpressionSchema("latency", Expression(latencyMb))
      }

      assert(e.getMessage.contains("provide a component for histogram"))
    }
  }

  test("histogram expressions come with default labels") {
    new Ctx {
      val latencyP90 =
        ExpressionSchema("latency", Expression(latencyMb, HistogramComponent.Percentile(0.9)))
          .build()
      val latencyP99 =
        ExpressionSchema("latency", Expression(latencyMb, HistogramComponent.Percentile(0.99)))
          .build()
      val latencyAvg =
        ExpressionSchema("latency", Expression(latencyMb, HistogramComponent.Avg)).build()

      assert(sr.expressions.contains(nameToKey("latency", Map("bucket" -> "p90"))))
      assert(sr.expressions.contains(nameToKey("latency", Map("bucket" -> "p99"))))
      assert(sr.expressions.contains(nameToKey("latency", Map("bucket" -> "avg"))))
    }
  }

  test("expressions with different namespaces are all stored") {
    new Ctx {
      val successRate1 = ExpressionSchema("success_rate", Expression(successMb).divide(sum))
        .withNamespace("section1")
        .withBounds(MonotoneThresholds(GreaterThan, 99.5, 99.75))
        .withUnit(Percentage)
        .withDescription("The success rate of the slow query")
        .build()

      val successRate2 = ExpressionSchema("success_rate", Expression(successMb).divide(sum))
        .withNamespace("section2", "a")
        .withBounds(MonotoneThresholds(GreaterThan, 99.5, 99.75))
        .withUnit(Percentage)
        .withDescription("The success rate of the slow query")
        .build()

      assert(sr.expressions.values.size == 2)
      val namespaces = sr.expressions.values.toSeq.map { exprSchema =>
        exprSchema.namespace
      }
      assert(namespaces.contains(Seq("section1")))
      assert(namespaces.contains(Seq("section2", "a")))
    }
  }

  test("expressions with the same key - name, labels and namespaces") {
    new Ctx {
      val successRate1 = ExpressionSchema("success", Expression(successMb))
        .withNamespace("section1")
        .withBounds(MonotoneThresholds(GreaterThan, 99.5, 99.75))
        .withUnit(Percentage)
        .withDescription("The success rate of the slow query")
        .build()

      val successRate2 = ExpressionSchema("success", Expression(successMb).divide(sum))
        .withNamespace("section1")
        .withBounds(MonotoneThresholds(GreaterThan, 99.5, 99.75))
        .withUnit(Percentage)
        .withDescription("The success rate of the slow query")
        .build()

      // only store the first
      // the second attempt will be a Throw
      assert(sr.expressions.values.size == 1)
      assert(
        sr.expressions(nameToKey("success", Map(), Seq("section1"))).expr == Expression(successMb))
      assert(successRate1.isReturn)
      assert(successRate2.isThrow)
    }
  }

  test("StringExpression nested in FunctionExpression") {
    val loadedSr = LoadedStatsReceiver.self
    val sr = new InMemoryStatsReceiver
    LoadedStatsReceiver.self = sr
    val successRate = ExpressionSchema(
      "success_rate",
      Expression(100).multiply(
        Expression(Seq("clnt", "aclient", "success"), isCounter = true).divide(
          Expression(Seq("clnt", "aclient", "success"), isCounter = true).plus(
            Expression(Seq("clnt", "aclient", "failures"), isCounter = true))))
    ).withRole(Client).withServiceName("aclient").build()

    assert(sr.expressions.values.size == 1)
    assert(
      sr.expressions.contains(
        ExpressionSchemaKey(
          "success_rate",
          Map("role" -> "Client", "service_name" -> "aclient"),
          Seq())))
    LoadedStatsReceiver.self = loadedSr
  }
}
