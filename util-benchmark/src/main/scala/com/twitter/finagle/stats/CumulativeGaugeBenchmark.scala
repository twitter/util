package com.twitter.finagle.stats

import com.twitter.finagle.stats.CumulativeGaugeBenchmark.CumulativeStatsRecv
import com.twitter.util.StdBenchAnnotations
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
import scala.collection.mutable

// ./sbt 'project util-benchmark' 'run .*CumulativeGaugeBenchmark.*'
@State(Scope.Benchmark)
class CumulativeGaugeBenchmark extends StdBenchAnnotations {

  @Param(Array("1", "10", "100"))
  private[this] var num = 1

  private[this] var getStatsRecv: CumulativeStatsRecv = _

  // hold strong references to each gauge
  private[this] var gauges: mutable.ArrayBuffer[Gauge] = _

  private[this] var theGauge: () => Float = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    getStatsRecv = new CumulativeStatsRecv()
    gauges = new mutable.ArrayBuffer[Gauge](10000)
    0.until(num).foreach { _ =>
      gauges += getStatsRecv.addGauge("get_gauge")(1f)
    }
    theGauge = getStatsRecv.gauges(Seq("get_gauge"))
  }

  @TearDown(Level.Iteration)
  def teardown(): Unit =
    gauges.foreach(_.remove())


  @Benchmark
  def getValue: Float =
    theGauge()

  @Benchmark
  @Warmup(batchSize = 100)
  @Measurement(batchSize = 100)
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def addGauge(): Gauge = {
    val g = getStatsRecv.addGauge("add_gauge")(2f)
    gauges += g
    g
  }

}

object CumulativeGaugeBenchmark {

  class CumulativeStatsRecv extends StatsReceiverWithCumulativeGauges {
    override val repr: AnyRef = this
    override def counter(name: String*): Counter = ???
    override def stat(name: String*): Stat = ???

    var gauges = Map.empty[Seq[String], () => Float]

    override protected[this] def registerGauge(name: Seq[String], f: => Float): Unit =
      gauges += name -> (() => f)

    override protected[this] def deregisterGauge(name: Seq[String]): Unit =
      gauges -= name
  }

}
