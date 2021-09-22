package com.twitter.finagle.stats

import com.twitter.finagle.stats.exp.ExpressionSchema
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try

/**
 * BroadcastStatsReceiver is a helper object that create a StatsReceiver wrapper around multiple
 * StatsReceivers (n).
 */
object BroadcastStatsReceiver {
  def apply(receivers: Seq[StatsReceiver]): StatsReceiver = receivers.filterNot(_.isNull) match {
    case Seq() => NullStatsReceiver
    case Seq(fst) => fst
    case Seq(first, second) => new Two(first, second)
    case more => new N(more)
  }

  private class Two(first: StatsReceiver, second: StatsReceiver)
      extends StatsReceiver
      with DelegatingStatsReceiver {
    val repr: AnyRef = this

    def counter(metricBuilder: MetricBuilder) = new BroadcastCounter.Two(
      first.counter(metricBuilder),
      second.counter(metricBuilder)
    )

    def stat(metricBuilder: MetricBuilder) = new BroadcastStat.Two(
      first.stat(metricBuilder),
      second.stat(metricBuilder)
    )

    def addGauge(metricBuilder: MetricBuilder)(f: => Float) = new Gauge {
      val firstGauge = first.addGauge(metricBuilder)(f)
      val secondGauge = second.addGauge(metricBuilder)(f)
      def remove(): Unit = {
        firstGauge.remove()
        secondGauge.remove()
      }
      def metadata: Metadata = MultiMetadata(Seq(firstGauge.metadata, secondGauge.metadata))
    }

    override protected[finagle] def registerExpression(
      expressionSchema: ExpressionSchema
    ): Try[Unit] = {
      Try.collect(
        Seq(
          first.registerExpression(expressionSchema),
          second.registerExpression(expressionSchema))) match {
        case Return(_) => Return.Unit
        case Throw(throwable) => Throw(throwable)
      }
    }

    def underlying: Seq[StatsReceiver] = Seq(first, second)

    override def toString: String =
      s"Broadcast($first, $second)"
  }

  private class N(srs: Seq[StatsReceiver]) extends StatsReceiver with DelegatingStatsReceiver {
    val repr: AnyRef = this

    def counter(metricBuilder: MetricBuilder) =
      BroadcastCounter(srs.map { _.counter(metricBuilder) })

    def stat(metricBuilder: MetricBuilder) =
      BroadcastStat(srs.map { _.stat(metricBuilder) })

    def addGauge(metricBuilder: MetricBuilder)(f: => Float) = new Gauge {
      val gauges = srs.map { _.addGauge(metricBuilder)(f) }
      def remove(): Unit = gauges.foreach { _.remove() }
      def metadata: Metadata = MultiMetadata(gauges.map(_.metadata))
    }

    override protected[finagle] def registerExpression(
      expressionSchema: ExpressionSchema
    ): Try[Unit] =
      Try.collect(srs.map(_.registerExpression(expressionSchema))) match {
        case Return(_) => Return.Unit
        case Throw(throwable) => Throw(throwable)
      }

    def underlying: Seq[StatsReceiver] = srs

    override def toString: String =
      s"Broadcast(${underlying.mkString(", ")})"
  }
}

/**
 * BroadcastCounter is a helper object that create a Counter wrapper around multiple
 * Counters (n).
 * For performance reason, we have specialized cases if n == (0, 1, 2, 3 or 4)
 */
object BroadcastCounter {
  def apply(counters: Seq[Counter]): Counter = counters match {
    case Seq() => NullCounter
    case Seq(counter) => counter
    case Seq(a, b) => new Two(a, b)
    case Seq(a, b, c) => new Three(a, b, c)
    case Seq(a, b, c, d) => new Four(a, b, c, d)
    case more => new N(more)
  }

  private object NullCounter extends Counter {
    def incr(delta: Long): Unit = ()
    def metadata: Metadata = NoMetadata
  }

  private[stats] class Two(a: Counter, b: Counter) extends Counter {
    def incr(delta: Long): Unit = {
      a.incr(delta)
      b.incr(delta)
    }
    def metadata: Metadata = MultiMetadata(Seq(a.metadata, b.metadata))
  }

  private class Three(a: Counter, b: Counter, c: Counter) extends Counter {
    def incr(delta: Long): Unit = {
      a.incr(delta)
      b.incr(delta)
      c.incr(delta)
    }
    def metadata: Metadata = MultiMetadata(Seq(a.metadata, b.metadata, c.metadata))
  }

  private class Four(a: Counter, b: Counter, c: Counter, d: Counter) extends Counter {
    def incr(delta: Long): Unit = {
      a.incr(delta)
      b.incr(delta)
      c.incr(delta)
      d.incr(delta)
    }
    def metadata: Metadata = MultiMetadata(Seq(a.metadata, b.metadata, c.metadata, d.metadata))
  }

  private class N(counters: Seq[Counter]) extends Counter {
    def incr(delta: Long): Unit = { counters.foreach(_.incr(delta)) }
    def metadata: Metadata = MultiMetadata(counters.map(_.metadata))
  }
}

/**
 * BroadcastStat is a helper object that create a Counter wrapper around multiple
 * Stats (n).
 * For performance reason, we have specialized cases if n == (0, 1, 2, 3 or 4)
 */
object BroadcastStat {
  def apply(stats: Seq[Stat]): Stat = stats match {
    case Seq() => NullStat
    case Seq(counter) => counter
    case Seq(a, b) => new Two(a, b)
    case Seq(a, b, c) => new Three(a, b, c)
    case Seq(a, b, c, d) => new Four(a, b, c, d)
    case more => new N(more)
  }

  private object NullStat extends Stat {
    def add(value: Float): Unit = ()
    def metadata: Metadata = NoMetadata
  }

  private[stats] class Two(a: Stat, b: Stat) extends Stat {
    def add(value: Float): Unit = {
      a.add(value)
      b.add(value)
    }
    def metadata: Metadata = MultiMetadata(Seq(a.metadata, b.metadata))
  }

  private class Three(a: Stat, b: Stat, c: Stat) extends Stat {
    def add(value: Float): Unit = {
      a.add(value)
      b.add(value)
      c.add(value)
    }
    def metadata: Metadata = MultiMetadata(Seq(a.metadata, b.metadata, c.metadata))
  }

  private class Four(a: Stat, b: Stat, c: Stat, d: Stat) extends Stat {
    def add(value: Float): Unit = {
      a.add(value)
      b.add(value)
      c.add(value)
      d.add(value)
    }
    def metadata: Metadata = MultiMetadata(Seq(a.metadata, b.metadata, c.metadata, d.metadata))
  }

  private class N(stats: Seq[Stat]) extends Stat {
    def add(value: Float): Unit = { stats.foreach(_.add(value)) }
    def metadata: Metadata = MultiMetadata(stats.map(_.metadata))
  }
}
