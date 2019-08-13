package com.twitter.finagle.stats

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.FunSuite
import org.scalatest.concurrent.{Eventually, IntegrationPatience}

class CumulativeGaugeTest extends FunSuite with Eventually with IntegrationPatience {

  private[this] val sameThreadExecutor = new Executor {
    def execute(command: Runnable): Unit = command.run()
  }

  class TestGauge extends CumulativeGauge(sameThreadExecutor) {
    val numRegisters = new AtomicInteger()
    val numDeregisters = new AtomicInteger()

    def cleanRefs(): Unit = cleanup()

    def register(): Boolean = {
      numRegisters.incrementAndGet()
      true
    }
    def deregister(): Unit = numDeregisters.incrementAndGet()
  }

  test("an empty CumulativeGauge should register on the first gauge added") {
    val gauge = new TestGauge()
    assert(0 == gauge.numRegisters.get)

    gauge.addGauge { 0.0f }
    assert(1 == gauge.numRegisters.get)
  }

  test("a CumulativeGauge with size = 1 should deregister when all gauges are removed") {
    val gauge = new TestGauge()
    val added = gauge.addGauge { 1.0f }
    assert(0 == gauge.numDeregisters.get)

    added.remove()
    assert(1 == gauge.numDeregisters.get)
  }

  test(
    "a CumulativeGauge with size = 1 should not deregister after a System.gc when there are still valid references to the gauge"
  ) {
    val gauge = new TestGauge()
    assert(0 == gauge.numDeregisters.get)
    val added = gauge.addGauge { 1.0f }

    System.gc()
    gauge.cleanRefs()

    assert(0 == gauge.numDeregisters.get)
  }

  test(
    "a CumulativeGauge with size = 1 should deregister after a System.gc when no references are held onto, after enough gets"
  ) {
    val gauge = new TestGauge()
    var added = gauge.addGauge { 1.0f }
    assert(0 == gauge.numDeregisters.get)

    added = null
    System.gc()

    eventually {
      gauge.cleanRefs()
      assert(gauge.getValue == 0.0f)
      assert(gauge.numDeregisters.get > 0)
    }
  }

  test("a CumulativeGauge should sum values across all registered gauges") {
    val gauge = new TestGauge()

    val underlying = 0.until(100).map { _ =>
      gauge.addGauge { 10.0f }
    }
    assert(gauge.getValue == (10.0f * 100))
  }

  test("a CumulativeGauge should discount gauges once removed") {
    val gauge = new TestGauge()

    val underlying = Array.fill(100) { gauge.addGauge { 10.0f } }
    assert(gauge.getValue == (10.0f * 100))
    underlying(0).remove()
    assert(gauge.getValue == (10.0f * 99))
    underlying(1).remove()
    assert(gauge.getValue == (10.0f * 98))
  }

}
