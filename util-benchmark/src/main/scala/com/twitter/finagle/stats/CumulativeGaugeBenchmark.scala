package com.twitter.finagle.stats

import com.twitter.util.StdBenchAnnotations
import java.util
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

// ./sbt 'project util-benchmark' 'jmh:run CumulativeGaugeBenchmark'
@State(Scope.Benchmark)
class CumulativeGaugeBenchmark extends StdBenchAnnotations {
  import CumulativeGaugeBenchmark._

  private[this] var cg: CumulativeGauge = null

  @Param(Array("1", "10", "100", "1000", "10000"))
  private[this] var num = 1

  // hold strong references to each gauge
  private[this] val gauges = new util.LinkedList[Gauge]()

  private[this] var theGauge: () => Float = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    cg = new CGauge()
    gauges.clear()
    0.until(num).foreach { _ =>
      gauges.add(cg.addGauge(1))
    }
  }

  @Benchmark
  def getValue: Float =
    cg.getValue

  @Benchmark
  @Warmup(iterations = 50, batchSize = 100)
  @Measurement(iterations = 100, batchSize = 100)
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  @Fork(5)
  def addGauge(): Gauge = {
    cg.addGauge(1)
  }

}

object CumulativeGaugeBenchmark {

  class CGauge extends CumulativeGauge {

    override def register(): Boolean = true

    override def deregister(): Unit = ()
  }
}
