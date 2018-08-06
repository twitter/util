package com.twitter.finagle.stats

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class InMemoryStatsReceiverTest extends FunSuite with Eventually with IntegrationPatience {

  test("clear") {
    val inMemoryStatsReceiver = new InMemoryStatsReceiver
    inMemoryStatsReceiver.counter("counter").incr()
    inMemoryStatsReceiver.counter("counter").incr(2)
    assert(inMemoryStatsReceiver.counter("counter")() == 3)

    inMemoryStatsReceiver.stat("stat").add(1.0f)
    inMemoryStatsReceiver.stat("stat").add(2.0f)
    assert(inMemoryStatsReceiver.stat("stat")() == Seq(1.0f, 2.0f))

    inMemoryStatsReceiver.addGauge("gauge") { 1 }
    assert(inMemoryStatsReceiver.gauges.contains(Seq("gauge")))

    inMemoryStatsReceiver.clear()

    assert(!inMemoryStatsReceiver.counters.contains(Seq("counter")))
    assert(!inMemoryStatsReceiver.stats.contains(Seq("stat")))
    assert(!inMemoryStatsReceiver.gauges.contains(Seq("gauge")))
  }

  test("threadsafe counter") {
    val inMemoryStatsReceiver = new InMemoryStatsReceiver
    (1 to 50).par.foreach(_ => inMemoryStatsReceiver.counter("same").incr())
    eventually {
      assert(inMemoryStatsReceiver.counter("same")() == 50)
    }
  }

  test("threadsafe stats") {
    val inMemoryStatsReceiver = new InMemoryStatsReceiver
    (1 to 50).par.foreach(_ => inMemoryStatsReceiver.stat("same").add(1.0f))
    eventually {
      assert(inMemoryStatsReceiver.stat("same")().size == 50)
    }
  }

  test("ReadableCounter.toString") {
    val stats = new InMemoryStatsReceiver()
    val c = stats.counter("a", "b")
    assert("Counter(a/b=0)" == c.toString)
    c.incr()
    assert("Counter(a/b=1)" == c.toString)
  }

  test("ReadableGauge.toString") {
    var n = 0
    val stats = new InMemoryStatsReceiver()
    val g = stats.addGauge("a", "b") { n }
    assert("Gauge(a/b=0.0)" == g.toString)

    n = 11
    assert("Gauge(a/b=11.0)" == g.toString)
  }

  test("ReadableStat.toString") {
    val stats = new InMemoryStatsReceiver()
    val s = stats.stat("a", "b")
    assert("Stat(a/b=[])" == s.toString)

    s.add(1)
    assert("Stat(a/b=[1.0])" == s.toString)

    s.add(2)
    s.add(3)
    assert("Stat(a/b=[1.0,2.0,3.0])" == s.toString)

    s.add(4)
    assert("Stat(a/b=[1.0,2.0,3.0... (omitted 1 value(s))])" == s.toString)
  }

  test("histogramDetails when empty") {
    val stats = new InMemoryStatsReceiver()
    assert(stats.histogramDetails == Map.empty)
  }

  test("histogramDetails edges") {
    val stats = new InMemoryStatsReceiver()
    val s1 = stats.stat("a", "b")
    val s2 = stats.stat("a", "c")

    // test counting and edge cases
    s1.add(Int.MaxValue)
    s1.add(0)
    s1.add(Int.MaxValue)
    s1.add(0)

    // test other edges cases
    s2.add(-5)
    s2.add(Long.MaxValue)
    assert(
      stats.histogramDetails("a/b").counts ==
        Seq(BucketAndCount(0, 1, 2), BucketAndCount(Int.MaxValue - 1, Int.MaxValue, 2))
    )
    assert(
      stats.histogramDetails("a/c").counts ==
        Seq(BucketAndCount(0, 1, 1), BucketAndCount(Int.MaxValue - 1, Int.MaxValue, 1))
    )
  }

  test("keeps track of verbosity") {
    val stats = new InMemoryStatsReceiver()
    stats.stat(Verbosity.Debug, "foo")
    stats.counter(Verbosity.Default, "bar")
    stats.addGauge(Verbosity.Debug, "baz") { 0f }

    assert(stats.verbosity(Seq("foo")) == Verbosity.Debug)
    assert(stats.verbosity(Seq("bar")) == Verbosity.Default)
    assert(stats.verbosity(Seq("baz")) == Verbosity.Debug)
  }

  test("print") {
    val baos = new ByteArrayOutputStream()
    val ps = new PrintStream(baos, true, "utf-8")

    try {
      val inMemoryStatsReceiver = new InMemoryStatsReceiver
      inMemoryStatsReceiver.counter("x", "y", "z").incr()
      inMemoryStatsReceiver.counter("x", "y", "z").incr(2)
      assert(inMemoryStatsReceiver.counter("x", "y", "z")() == 3)

      inMemoryStatsReceiver.counter("x", "y", "q").incr()
      inMemoryStatsReceiver.counter("x", "y", "q").incr(2)
      assert(inMemoryStatsReceiver.counter("x", "y", "q")() == 3)

      inMemoryStatsReceiver.counter("counter").incr()
      inMemoryStatsReceiver.counter("counter").incr(2)
      assert(inMemoryStatsReceiver.counter("counter")() == 3)

      inMemoryStatsReceiver.counter("a", "b", "c").incr()
      inMemoryStatsReceiver.counter("a", "b", "c").incr(2)
      assert(inMemoryStatsReceiver.counter("a", "b", "c")() == 3)

      inMemoryStatsReceiver.counter("a", "m", "n").incr()
      inMemoryStatsReceiver.counter("a", "m", "n").incr(2)
      assert(inMemoryStatsReceiver.counter("a", "m", "n")() == 3)

      inMemoryStatsReceiver.stat("stat").add(1.0f)
      inMemoryStatsReceiver.stat("stat").add(2.0f)
      assert(inMemoryStatsReceiver.stat("stat")() == Seq(1.0f, 2.0f))

      inMemoryStatsReceiver.stat("d", "e", "f").add(1.0f)
      inMemoryStatsReceiver.stat("d", "e", "f").add(2.0f)
      inMemoryStatsReceiver.stat("d", "e", "f").add(3.0f)
      inMemoryStatsReceiver.stat("d", "e", "f").add(4.0f)
      assert(inMemoryStatsReceiver.stat("d", "e", "f")() == Seq(1.0f, 2.0f, 3.0f, 4.0f))

      inMemoryStatsReceiver.addGauge("gauge") {
        1
      }
      assert(inMemoryStatsReceiver.gauges.contains(Seq("gauge")))
      inMemoryStatsReceiver.addGauge("g", "h", "i") {
        1
      }
      assert(inMemoryStatsReceiver.gauges.contains(Seq("g", "h", "i")))

      inMemoryStatsReceiver.print(ps)
      val content = new String(baos.toByteArray, StandardCharsets.UTF_8)
      val parts = content.split('\n')

      assert(parts.length == 9)
      assert(parts(0) == "a/b/c 3")
      assert(parts(1) == "a/m/n 3")
      assert(parts(2) == "counter 3")
      assert(parts(3) == "x/y/q 3")
      assert(parts(4) == "x/y/z 3")
      assert(parts(5) == "g/h/i 1.000000")
      assert(parts(6) == "gauge 1.000000")
      assert(parts(7) == "d/e/f 2.500000 [1.0,2.0,3.0... (omitted 1 value(s))]")
      assert(parts(8) == "stat 1.500000 [1.0,2.0]")
    } finally {
      ps.close()
    }
  }

  test("print include headers") {
    val baos = new ByteArrayOutputStream()
    val ps = new PrintStream(baos, true, "utf-8")

    try {
      val inMemoryStatsReceiver = new InMemoryStatsReceiver
      inMemoryStatsReceiver.counter("x", "y", "z").incr()
      inMemoryStatsReceiver.counter("x", "y", "z").incr(2)
      assert(inMemoryStatsReceiver.counter("x", "y", "z")() == 3)

      inMemoryStatsReceiver.counter("x", "y", "q").incr()
      inMemoryStatsReceiver.counter("x", "y", "q").incr(2)
      assert(inMemoryStatsReceiver.counter("x", "y", "q")() == 3)

      inMemoryStatsReceiver.counter("counter").incr()
      inMemoryStatsReceiver.counter("counter").incr(2)
      assert(inMemoryStatsReceiver.counter("counter")() == 3)

      inMemoryStatsReceiver.counter("a", "b", "c").incr()
      inMemoryStatsReceiver.counter("a", "b", "c").incr(2)
      assert(inMemoryStatsReceiver.counter("a", "b", "c")() == 3)

      inMemoryStatsReceiver.counter("a", "m", "n").incr()
      inMemoryStatsReceiver.counter("a", "m", "n").incr(2)
      assert(inMemoryStatsReceiver.counter("a", "m", "n")() == 3)

      inMemoryStatsReceiver.stat("stat").add(1.0f)
      inMemoryStatsReceiver.stat("stat").add(2.0f)
      assert(inMemoryStatsReceiver.stat("stat")() == Seq(1.0f, 2.0f))

      inMemoryStatsReceiver.stat("d", "e", "f").add(1.0f)
      inMemoryStatsReceiver.stat("d", "e", "f").add(2.0f)
      inMemoryStatsReceiver.stat("d", "e", "f").add(3.0f)
      inMemoryStatsReceiver.stat("d", "e", "f").add(4.0f)
      assert(inMemoryStatsReceiver.stat("d", "e", "f")() == Seq(1.0f, 2.0f, 3.0f, 4.0f))

      inMemoryStatsReceiver.addGauge("gauge") {
        1
      }
      assert(inMemoryStatsReceiver.gauges.contains(Seq("gauge")))
      inMemoryStatsReceiver.addGauge("g", "h", "i") {
        1
      }
      assert(inMemoryStatsReceiver.gauges.contains(Seq("g", "h", "i")))

      inMemoryStatsReceiver.print(ps, includeHeaders = true)
      val content = new String(baos.toByteArray, StandardCharsets.UTF_8)
      val parts = content.filterNot(_ == '\r').split('\n')

      assert(parts.length == 17)
      assert(parts(0) == "Counters:")
      assert(parts(1) == "---------")
      assert(parts(2) == "a/b/c 3")
      assert(parts(3) == "a/m/n 3")
      assert(parts(4) == "counter 3")
      assert(parts(5) == "x/y/q 3")
      assert(parts(6) == "x/y/z 3")
      assert(parts(7) == "")
      assert(parts(8) == "Gauges:")
      assert(parts(9) == "-------")
      assert(parts(10) == "g/h/i 1.000000")
      assert(parts(11) == "gauge 1.000000")
      assert(parts(12) == "")
      assert(parts(13) == "Stats:")
      assert(parts(14) == "------")
      assert(parts(15) == "d/e/f 2.500000 [1.0,2.0,3.0... (omitted 1 value(s))]")
      assert(parts(16) == "stat 1.500000 [1.0,2.0]")
    } finally {
      ps.close()
    }
  }

  test("print include headers skip empty") {
    val baos = new ByteArrayOutputStream()
    val ps = new PrintStream(baos, true, "utf-8")

    try {
      val inMemoryStatsReceiver = new InMemoryStatsReceiver
      inMemoryStatsReceiver.counter("x", "y", "z").incr()
      inMemoryStatsReceiver.counter("x", "y", "z").incr(2)
      assert(inMemoryStatsReceiver.counter("x", "y", "z")() == 3)

      inMemoryStatsReceiver.counter("x", "y", "q").incr()
      inMemoryStatsReceiver.counter("x", "y", "q").incr(2)
      assert(inMemoryStatsReceiver.counter("x", "y", "q")() == 3)

      inMemoryStatsReceiver.counter("counter").incr()
      inMemoryStatsReceiver.counter("counter").incr(2)
      assert(inMemoryStatsReceiver.counter("counter")() == 3)

      inMemoryStatsReceiver.counter("a", "b", "c").incr()
      inMemoryStatsReceiver.counter("a", "b", "c").incr(2)
      assert(inMemoryStatsReceiver.counter("a", "b", "c")() == 3)

      inMemoryStatsReceiver.counter("a", "m", "n").incr()
      inMemoryStatsReceiver.counter("a", "m", "n").incr(2)
      assert(inMemoryStatsReceiver.counter("a", "m", "n")() == 3)

      inMemoryStatsReceiver.stat("stat").add(1.0f)
      inMemoryStatsReceiver.stat("stat").add(2.0f)
      assert(inMemoryStatsReceiver.stat("stat")() == Seq(1.0f, 2.0f))

      inMemoryStatsReceiver.stat("d", "e", "f").add(1.0f)
      inMemoryStatsReceiver.stat("d", "e", "f").add(2.0f)
      inMemoryStatsReceiver.stat("d", "e", "f").add(3.0f)
      inMemoryStatsReceiver.stat("d", "e", "f").add(4.0f)
      assert(inMemoryStatsReceiver.stat("d", "e", "f")() == Seq(1.0f, 2.0f, 3.0f, 4.0f))

      inMemoryStatsReceiver.print(ps, includeHeaders = true)
      val content = new String(baos.toByteArray, StandardCharsets.UTF_8)
      val parts = content.filterNot(_ == '\r').split('\n')

      assert(parts.length == 12)
      assert(parts(0) == "Counters:")
      assert(parts(1) == "---------")
      assert(parts(2) == "a/b/c 3")
      assert(parts(3) == "a/m/n 3")
      assert(parts(4) == "counter 3")
      assert(parts(5) == "x/y/q 3")
      assert(parts(6) == "x/y/z 3")
      assert(parts(7) == "")
      assert(parts(8) == "Stats:")
      assert(parts(9) == "------")
      assert(parts(10) == "d/e/f 2.500000 [1.0,2.0,3.0... (omitted 1 value(s))]")
      assert(parts(11) == "stat 1.500000 [1.0,2.0]")
    } finally {
      ps.close()
    }
  }
}
