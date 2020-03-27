package com.twitter.finagle.stats

import com.twitter.conversions.DurationOps._
import com.twitter.util.{Await, Future}
import java.util.concurrent.TimeUnit
import org.mockito.Mockito._
import org.scalatest.FunSuite
import scala.collection.mutable.ArrayBuffer

class StatsReceiverTest extends FunSuite {
  test("RollupStatsReceiver counter/stats") {
    val mem = new InMemoryStatsReceiver
    val receiver = new RollupStatsReceiver(mem)

    receiver.counter("toto", "titi", "tata").incr()
    assert(mem.counters(Seq("toto")) == 1)
    assert(mem.counters(Seq("toto", "titi")) == 1)
    assert(mem.counters(Seq("toto", "titi", "tata")) == 1)

    receiver.counter("toto", "titi", "tutu").incr()
    assert(mem.counters(Seq("toto")) == 2)
    assert(mem.counters(Seq("toto", "titi")) == 2)
    assert(mem.counters(Seq("toto", "titi", "tata")) == 1)
    assert(mem.counters(Seq("toto", "titi", "tutu")) == 1)
  }

  test("Broadcast Counter/Stat") {
    class MemCounter extends Counter {
      var c: Long = 0
      def incr(delta: Long): Unit = { c += delta }
    }
    val c1 = new MemCounter
    val c2 = new MemCounter
    val broadcastCounter = BroadcastCounter(Seq(c1, c2))
    assert(c1.c == 0)
    assert(c2.c == 0)

    broadcastCounter.incr()
    assert(c1.c == 1)
    assert(c2.c == 1)

    class MemStat extends Stat {
      var values: scala.collection.Seq[Float] = ArrayBuffer.empty[Float]
      def add(f: Float): Unit = { values = values :+ f }
    }
    val s1 = new MemStat
    val s2 = new MemStat
    val broadcastStat = BroadcastStat(Seq(s1, s2))
    assert(s1.values == Seq.empty)
    assert(s2.values == Seq.empty)

    broadcastStat.add(1f)
    assert(s1.values == Seq(1f))
    assert(s2.values == Seq(1f))
  }

  test("StatsReceiver time") {
    val receiver = spy(new InMemoryStatsReceiver)

    Stat.time(receiver.stat("er", "mah", "gerd")) { () }
    verify(receiver, times(1)).stat("er", "mah", "gerd")

    Stat.time(receiver.stat("er", "mah", "gerd"), TimeUnit.NANOSECONDS) { () }
    verify(receiver, times(2)).stat("er", "mah", "gerd")

    val stat = receiver.stat("er", "mah", "gerd")
    verify(receiver, times(3)).stat("er", "mah", "gerd")

    Stat.time(stat, TimeUnit.DAYS) { () }
    verify(receiver, times(3)).stat("er", "mah", "gerd")
  }

  test("StatsReceiver timeFuture") {
    val receiver = spy(new InMemoryStatsReceiver)

    Await.ready(Stat.timeFuture(receiver.stat("2", "chainz")) { Future.Unit }, 1.second)
    verify(receiver, times(1)).stat("2", "chainz")

    Await.ready(
      Stat.timeFuture(receiver.stat("2", "chainz"), TimeUnit.MINUTES) { Future.Unit },
      1.second
    )
    verify(receiver, times(2)).stat("2", "chainz")

    val stat = receiver.stat("2", "chainz")
    verify(receiver, times(3)).stat("2", "chainz")

    Await.result(Stat.timeFuture(stat, TimeUnit.HOURS) { Future.Unit }, 1.second)
    verify(receiver, times(3)).stat("2", "chainz")
  }

  test("StatsReceiver.scope: prefix stats by a scope string") {
    val receiver = new InMemoryStatsReceiver
    val scoped = receiver.scope("foo")
    receiver.counter("bar").incr()
    scoped.counter("baz").incr()

    assert(receiver.counters(Seq("bar")) == 1)
    assert(receiver.counters(Seq("foo", "baz")) == 1)
  }

  test("StatsReceiver.scope: don't prefix with the empty string") {
    val receiver = new InMemoryStatsReceiver
    val scoped = receiver.scope("")
    receiver.counter("bar").incr()
    scoped.counter("baz").incr()

    assert(receiver.counters(Seq("bar")) == 1)
    assert(receiver.counters(Seq("baz")) == 1)
  }

  test("StatsReceiver.scope: no namespace") {
    val receiver = new InMemoryStatsReceiver
    val scoped = receiver.scope()
    receiver.counter("bar").incr()

    assert(receiver.counters(Seq("bar")) == 1)
  }

  test("StatsReceiver.scope: multiple prefixes") {
    val receiver = new InMemoryStatsReceiver
    val scoped = receiver.scope("foo", "bar", "shoe")
    scoped.counter("baz").incr()

    assert(receiver.counters(Seq("foo", "bar", "shoe", "baz")) == 1)
  }

  test("Scoped equality") {
    val sr = new InMemoryStatsReceiver
    assert(sr == sr)
    assert(sr.scope("foo") != sr.scope("bar"))
  }

  test("Scoped forwarding to NullStatsReceiver") {
    assert(NullStatsReceiver.scope("foo").scope("bar").isNull)
  }

  test("toString") {
    assert("NullStatsReceiver" == NullStatsReceiver.toString)
    assert("NullStatsReceiver" == NullStatsReceiver.scope("hi").scopeSuffix("bye").toString)

    assert(
      "DenylistStatsReceiver(NullStatsReceiver)" ==
        new DenylistStatsReceiver(NullStatsReceiver, { _ => false }).toString
    )

    val inMem = new InMemoryStatsReceiver()
    assert("InMemoryStatsReceiver" == inMem.toString)

    assert("InMemoryStatsReceiver/scope1" == inMem.scope("scope1").toString)
    assert(
      "InMemoryStatsReceiver/scope1/scope2" ==
        inMem.scope("scope1").scope("scope2").toString
    )

    assert(
      "InMemoryStatsReceiver/begin/end" ==
        inMem.scopeSuffix("end").scope("begin").toString
    )

    assert(
      "InMemoryStatsReceiver/begin/mid/end" ==
        inMem.scope("begin").scopeSuffix("end").scope("mid").toString
    )

    assert(
      "Broadcast(InMemoryStatsReceiver, InMemoryStatsReceiver)" ==
        BroadcastStatsReceiver(Seq(inMem, inMem)).toString
    )

    assert(
      "Broadcast(InMemoryStatsReceiver, InMemoryStatsReceiver, InMemoryStatsReceiver)" ==
        BroadcastStatsReceiver(Seq(inMem, inMem, inMem)).toString
    )

  }

}
