package com.twitter.concurrent

import com.twitter.util.StdBenchAnnotations
import org.openjdk.jmh.annotations.{Benchmark, Scope, State}
import org.openjdk.jmh.infra.Blackhole

@State(Scope.Benchmark)
class BrokerBenchmark extends StdBenchAnnotations {
  // It's safe to share brokers across iterations because each benchmark pairs
  // a send with a recv.
  private[this] val q = new Broker[Int]
  private[this] val p = new Broker[Int]

  @Benchmark
  def sendAndRecv(blackhole: Blackhole): Unit = {
    val f = q.send(1).sync()
    blackhole.consume(f)
    val g = q.recv.sync()
    blackhole.consume(g)
  }

  @Benchmark
  def recvAndSend(blackhole: Blackhole): Unit = {
    val g = q.recv.sync()
    blackhole.consume(g)
    val f = q.send(2).sync()
    blackhole.consume(f)
  }

  // Especially notable is that, when selecting over multiple brokers (as we do
  // below), both send and recv operations are guaranteed to affect the same
  // broker. Offer.select commits to the communication that can proceed, which
  // means that if we attempt to send to multiple brokers, only one broker will
  // be modified, the state of the other brokers is unaffected.

  @Benchmark
  def selectRecvAndSend(blackhole: Blackhole): Unit = {
    val f = Offer.select(q.recv, p.recv)
    blackhole.consume(f)
    val g = Offer.select(q.send(3), p.send(3))
    blackhole.consume(g)
  }

  @Benchmark
  def selectSendAndRecv(blackhole: Blackhole): Unit = {
    val g = Offer.select(q.send(3), p.send(3))
    blackhole.consume(g)
    val f = Offer.select(q.recv, p.recv)
    blackhole.consume(f)
  }
}
