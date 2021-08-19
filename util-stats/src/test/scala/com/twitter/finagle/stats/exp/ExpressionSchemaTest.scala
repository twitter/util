package com.twitter.finagle.stats.exp

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.stats.MetricBuilder.{CounterType, HistogramType}
import com.twitter.finagle.stats._
import com.twitter.util.{Stopwatch, Time, TimeControl}
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

      val latency = ExpressionSchema("latency", Expression(latencyMb, Right(0.99)))
        .withUnit(Milliseconds)
        .withDescription("The latency of the slow query")

      successRate.register()
      latency.register()

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

      val downstreamLabel =
        Map(ExpressionSchema.Role -> Client.toString, ExpressionSchema.ServiceName -> "downstream")
      assert(
        sr.expressions(
            ExpressionSchemaKey("success_rate", downstreamLabel, Nil)).name == successRate.name)
      assert(
        sr.expressions(
            ExpressionSchemaKey("success_rate", downstreamLabel, Nil)).expr == successRate.expr)
      assert(
        sr.expressions(ExpressionSchemaKey("latency", downstreamLabel, Nil)).name == latency.name)
      assert(
        sr.expressions(ExpressionSchemaKey("latency", downstreamLabel, Nil)).expr == latency.expr)

      assert(sr.counters(Seq("success")) == 1)
      assert(sr.counters(Seq("failures")) == 1)
      assert(sr.stats(Seq("latency")) == Seq(50, 50))

      assert(
        sr.expressions(ExpressionSchemaKey("success_rate", downstreamLabel, Nil)).labels(
            ExpressionSchema.Role) == Client.toString)
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

  test("expressions with different namespaces are all stored") {
    new Ctx {
      val successRate1 = ExpressionSchema("success_rate", Expression(successMb).divide(sum))
        .withNamespace("section1")
        .withBounds(MonotoneThresholds(GreaterThan, 99.5, 99.75))
        .withUnit(Percentage)
        .withDescription("The success rate of the slow query")
        .register()

      val successRate2 = ExpressionSchema("success_rate", Expression(successMb).divide(sum))
        .withNamespace("section2", "a")
        .withBounds(MonotoneThresholds(GreaterThan, 99.5, 99.75))
        .withUnit(Percentage)
        .withDescription("The success rate of the slow query")
        .register()

      assert(sr.expressions.values.size == 2)
      val namespaces = sr.expressions.values.toSeq.map { exprSchema =>
        exprSchema.namespace
      }
      assert(namespaces.contains(Seq("section1")))
      assert(namespaces.contains(Seq("section2", "a")))
    }
  }
}
