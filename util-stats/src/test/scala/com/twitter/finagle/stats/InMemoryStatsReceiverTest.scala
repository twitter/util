package com.twitter.finagle.stats

import com.twitter.finagle.stats.MetricBuilder.CounterType
import com.twitter.finagle.stats.MetricBuilder.GaugeType
import com.twitter.finagle.stats.MetricBuilder.HistogramType
import com.twitter.finagle.stats.MetricBuilder.Identity
import com.twitter.finagle.stats.exp.Expression
import com.twitter.finagle.stats.exp.ExpressionSchema
import com.twitter.finagle.stats.exp.ExpressionSchemaKey
import com.twitter.finagle.stats.exp.HistogramComponent
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.funsuite.AnyFunSuite
import scala.collection.parallel.immutable.ParRange

class InMemoryStatsReceiverTest extends AnyFunSuite with Eventually with IntegrationPatience {

  // scalafix:off StoreGaugesAsMemberVariables
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
    new ParRange(1 to 50).foreach(_ => inMemoryStatsReceiver.counter("same").incr())
    eventually {
      assert(inMemoryStatsReceiver.counter("same")() == 50)
    }
  }

  test("threadsafe stats") {
    val inMemoryStatsReceiver = new InMemoryStatsReceiver
    new ParRange(1 to 50).foreach(_ => inMemoryStatsReceiver.stat("same").add(1.0f))
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
    val g = stats.addGauge("a", "b") { n.toFloat }
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

  test("register expressions") {
    val stats = new InMemoryStatsReceiver()
    ExpressionSchema(
      "a",
      Expression(
        MetricBuilder(name = Seq("counter"), metricType = CounterType, statsReceiver = stats)))
    stats.expressions.contains(ExpressionSchemaKey("a", Map(), Nil))
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
  // scalafix:on StoreGaugesAsMemberVariables

  test("creating metrics stores their metadata and clear()ing should remove that metadata") {
    val stats = new InMemoryStatsReceiver()
    stats.addGauge("coolGauge") { 3 }
    assert(stats.schemas(Seq("coolGauge")).isInstanceOf[MetricBuilder])
    stats.counter("sweetCounter")
    assert(stats.schemas(Seq("sweetCounter")).isInstanceOf[MetricBuilder])
    stats.stat("radHisto")
    assert(stats.schemas(Seq("radHisto")).isInstanceOf[MetricBuilder])

    stats.clear()
    assert(stats.schemas.isEmpty)
  }

  test("printSchemas should print schemas") {
    val stats = new InMemoryStatsReceiver()
    stats.addGauge("coolGauge") { 3 }
    stats.counter("sweet", "counter")
    stats.scope("rad").stat("histo")

    val baos = new ByteArrayOutputStream()
    val ps = new PrintStream(baos, true, "utf-8")
    try {
      stats.printSchemas(ps)
      val content = new String(baos.toByteArray, StandardCharsets.UTF_8)
      val parts = content.split('\n')

      assert(parts.length == 3)
      assert(
        parts(
          0) == "coolGauge MetricBuilder(false, No description provided, Unspecified, NoRoleSpecified, Verbosity(default), None, Full(coolGauge, Map()), List(), None, Vector(), GaugeType, InMemoryStatsReceiver)")
      assert(
        parts(
          1) == "rad/histo MetricBuilder(false, No description provided, Unspecified, NoRoleSpecified, Verbosity(default), None, Hierarchical(rad/histo, Map()), List(), None, Vector(), HistogramType, InMemoryStatsReceiver/rad)")
      assert(
        parts(
          2) == "sweet/counter MetricBuilder(false, No description provided, Unspecified, NoRoleSpecified, Verbosity(default), None, Hierarchical(sweet/counter, Map()), List(), None, Vector(), CounterType, InMemoryStatsReceiver)")
    } finally {
      ps.close()
    }
  }

  test("expressions can be hydrated from metadata that metrics use") {
    val sr = new InMemoryStatsReceiver

    val aCounter = sr.scope("test").counter("a")
    val bHisto = sr.scope("test").stat("b")
    val cGauge = sr.scope("test").addGauge("c") { 1 }

    val expression = ExpressionSchema(
      "test_expression",
      Expression(aCounter.metadata).plus(Expression(bHisto.metadata, HistogramComponent.Min)
        .plus(Expression(cGauge.metadata)))
    ).build()

    // what we expected as hydrated metric builders
    val aaSchema =
      MetricBuilder(
        name = Seq("test", "a"),
        metricType = CounterType,
        statsReceiver = sr).withHierarchicalOnly
    val bbSchema =
      MetricBuilder(
        name = Seq("test", "b"),
        metricType = HistogramType,
        statsReceiver = sr).withHierarchicalOnly
    val ccSchema =
      MetricBuilder(
        name = Seq("test", "c"),
        metricType = GaugeType,
        statsReceiver = sr).withHierarchicalOnly

    val expected_expression = ExpressionSchema(
      "test_expression",
      Expression(aaSchema).plus(
        Expression(bbSchema, HistogramComponent.Min).plus(Expression(ccSchema))))

    assert(sr.expressions
      .get(ExpressionSchemaKey("test_expression", Map(), Nil)).get.expr == expected_expression.expr)
  }

  test("getExpressionsWithLabel") {
    val sr = new InMemoryStatsReceiver

    val aCounter = sr.scope("test").counter("a")

    ExpressionSchema(
      "test_expression_0",
      Expression(aCounter.metadata)
    ).withLabel(ExpressionSchema.Role, "test").build()

    ExpressionSchema(
      "test_expression_1",
      Expression(aCounter.metadata)
    ).withLabel(ExpressionSchema.Role, "dont appear").build()

    val withLabel = sr.getAllExpressionsWithLabel(ExpressionSchema.Role, "test")
    assert(withLabel.size == 1)

    assert(
      !withLabel.contains(
        ExpressionSchemaKey("test_expression_1", Map((ExpressionSchema.Role, "dont appear")), Nil)))

    assert(sr.getAllExpressionsWithLabel("nonexistent key", "nonexistent value").isEmpty)
  }

  private class SupportsDimensionalStatsReceiver extends InMemoryStatsReceiver {
    var supportsDimensional: java.lang.Boolean = null

    private def hasDimensions(mb: MetricBuilder): Boolean = mb.identity match {
      case Identity.Hierarchical(_, _) => false
      case Identity.Full(_, _) => true
    }

    def reset(): Unit = {
      supportsDimensional = null
    }

    override def counter(metricBuilder: MetricBuilder): ReadableCounter = {
      val c = super.counter(metricBuilder)
      supportsDimensional = hasDimensions(c.metadata.toMetricBuilder.get)
      c
    }

    override def stat(metricBuilder: MetricBuilder): ReadableStat = {
      val s = super.stat(metricBuilder)
      supportsDimensional = hasDimensions(s.metadata.toMetricBuilder.get)
      s
    }

    override def addGauge(metricBuilder: MetricBuilder)(f: => Float): Gauge = {
      val g = super.addGauge(metricBuilder)(f)
      supportsDimensional = hasDimensions(g.metadata.toMetricBuilder.get)
      g
    }
  }

  test("StatsReceiver.scope.counter: dimensional metrics are disabled without a label") {
    val receiver = new SupportsDimensionalStatsReceiver
    val scoped = receiver.scope("foo")

    scoped.counter("bar")
    assert(!receiver.supportsDimensional)
  }

  test("StatsReceiver.counter: varargs metrics names longer than 1 disable dimensional metrics") {
    val receiver = new SupportsDimensionalStatsReceiver
    receiver.counter("foo", "bar")
    assert(!receiver.supportsDimensional)
    receiver.reset()

    receiver.counter(Some("description"), "foo", "bar")
    assert(!receiver.supportsDimensional)
    receiver.reset()

    receiver.counter("description", Verbosity.Default, "foo", "bar")
    assert(!receiver.supportsDimensional)
  }

  test("StatsReceiver.counter: varargs metrics names of 1 allow dimensional metrics") {
    val receiver = new SupportsDimensionalStatsReceiver
    receiver.counter("bar")
    assert(receiver.supportsDimensional)
    receiver.reset()

    receiver.counter(Some("description"), "bar")
    assert(receiver.supportsDimensional)
    receiver.reset()

    receiver.counter("description", Verbosity.Default, "bar")
    assert(receiver.supportsDimensional)
  }

  test("StatsReceiver.scope.addGauge: dimensional metrics are disabled without a label") {
    val receiver = new SupportsDimensionalStatsReceiver
    val scoped = receiver.scope("foo")

    scoped.addGauge("bar") { 1.0f }
    assert(!receiver.supportsDimensional)
  }

  test("StatsReceiver.addGauge: varargs metrics names longer than 1 disable dimensional metrics") {
    val receiver = new SupportsDimensionalStatsReceiver
    receiver.addGauge("foo", "bar") { 1.0f }
    assert(!receiver.supportsDimensional)
    receiver.reset()

    receiver.addGauge(Some("description"), "foo", "bar") { 1.0f }
    assert(!receiver.supportsDimensional)
    receiver.reset()

    receiver.addGauge("description", Verbosity.Default, "foo", "bar") { 1.0f }
    assert(!receiver.supportsDimensional)
  }

  test("StatsReceiver.addGauge: varargs metrics names of 1 allow dimensional metrics") {
    val receiver = new SupportsDimensionalStatsReceiver
    receiver.addGauge("bar") { 1.0f }
    assert(receiver.supportsDimensional)
    receiver.reset()

    receiver.addGauge(Some("description"), "bar") { 1.0f }
    assert(receiver.supportsDimensional)
    receiver.reset()

    receiver.addGauge("description", Verbosity.Default, "bar") { 1.0f }
    assert(receiver.supportsDimensional)
  }

  test("StatsReceiver.scope.stat: dimensional metrics are disabled without a label") {
    val receiver = new SupportsDimensionalStatsReceiver
    val scoped = receiver.scope("foo")

    scoped.stat("bar")
    assert(!receiver.supportsDimensional)
  }

  test("StatsReceiver.stat: varargs metrics names longer than 1 disable dimensional metrics") {
    val receiver = new SupportsDimensionalStatsReceiver
    receiver.stat("foo", "bar")
    assert(!receiver.supportsDimensional)
    receiver.reset()

    receiver.stat(Some("description"), "foo", "bar")
    assert(!receiver.supportsDimensional)
    receiver.reset()

    receiver.stat("description", Verbosity.Default, "foo", "bar")
    assert(!receiver.supportsDimensional)
  }

  test("StatsReceiver.stat: varargs metrics names of 1 allow dimensional metrics") {
    val receiver = new SupportsDimensionalStatsReceiver
    receiver.stat("bar")
    assert(receiver.supportsDimensional)
    receiver.reset()

    receiver.stat(Some("description"), "bar")
    assert(receiver.supportsDimensional)
    receiver.reset()

    receiver.stat("description", Verbosity.Default, "bar")
    assert(receiver.supportsDimensional)
  }

}
