package com.twitter.finagle.stats.exp

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.stats._
import com.twitter.util.{Stopwatch, Time, TimeControl}
import org.scalatest.FunSuite
import scala.util.control.NonFatal

class ExprEndToEndTest extends FunSuite {
  def mySlowQuery(ctl: TimeControl, succeed: Boolean): Unit = {
    ctl.advance(50.milliseconds)
    if (!succeed) {
      throw new Exception("boom!")
    }
  }

  test("Demonstrate an end to end example of using metrics") {
    val sr = new InMemoryStatsReceiver
    val clientSR = RoleConfiguredStatsReceiver(sr, Client, Some("downstream"))

    val successMb =
      CounterSchema(new MetricBuilder(name = Seq("success"), statsReceiver = clientSR))
    val failuresMb =
      CounterSchema(new MetricBuilder(name = Seq("failures"), statsReceiver = clientSR))
    val latencyMb =
      HistogramSchema(new MetricBuilder(name = Seq("latency"), statsReceiver = clientSR))

    val sum = Expression(successMb).plus(Expression(failuresMb))
    val successCounter = clientSR.counter(successMb)
    val failuresCounter = clientSR.counter(failuresMb)
    val latencyStat = clientSR.stat(latencyMb)

    val successRate = ExpressionSchema("success_rate", Expression(successMb).divide(sum))
      .withBounds(MonotoneThresholds(GreaterThan, 99.5, 99.75))
      .withUnit(Percentage)
      .withDescription("The success rate of the slow query")

    val latency = ExpressionSchema("latency", Expression(latencyMb))
      .withUnit(Milliseconds)
      .withDescription("The latency of the slow query")

    successRate.register()
    latency.register()

    def runTheQuery(succeed: Boolean): Unit = {
      Time.withCurrentTimeFrozen { ctl =>
        val elapsed = Stopwatch.start()
        try {
          mySlowQuery(ctl, succeed)
          successCounter.incr()
        } catch {
          case NonFatal(exn) =>
            failuresCounter.incr()
        } finally {
          latencyStat.add(elapsed().inMilliseconds)
        }
      }
    }
    runTheQuery(true)
    runTheQuery(false)

    assert(sr.expressions("success_rate_downstream").expr == successRate.expr)
    assert(sr.expressions("latency_downstream").expr == latency.expr)

    assert(sr.counters(Seq("success")) == 1)
    assert(sr.counters(Seq("failures")) == 1)
    assert(sr.stats(Seq("latency")) == Seq(50, 50))

    assert(sr.expressions("success_rate_downstream").labels.role == Client)
  }
}
