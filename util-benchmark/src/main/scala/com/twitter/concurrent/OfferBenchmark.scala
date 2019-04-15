package com.twitter.concurrent

import com.twitter.concurrent.Tx.{Commit, Result}
import com.twitter.util.{Future, Promise}
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
class OfferBenchmark {
  import OfferBenchmark._

  @Benchmark
  def timeChoose(state: OfferBenchmarkState): Unit = {
    val offers = state.toChooseFrom()
    Offer.choose(state.rngOpt, offers).prepare()
  }
}

object OfferBenchmark {
  // to focus on the benchmarking the impl of `choose`, use dumbed-down impls
  // of the offer and tx classes.
  private class SimpleOffer[T](fut: Future[Tx[T]]) extends Offer[T] {
    override def prepare() = fut
  }

  private class SimpleTx[T](t: T) extends Tx[T] {
    private val futAck = Future.value(Commit(t))
    override def nack(): Unit = ()
    override def ack(): Future[Result[T]] = futAck
  }

  @State(Scope.Benchmark)
  class OfferBenchmarkState {
    @Param(Array("3", "10", "100"))
    var numToChooseFrom: Int = 3

    val rng = new Random(3102957159L)
    val rngOpt = Some(rng)

    // N.B. Traditionally you'd put this type of setup inside of a benchmark setup method per invocation
    // but because the runtime of each invocation is so small, the overhead is high JMH advises against
    // using Invocation setup methods:
    // https://hg.openjdk.java.net/code-tools/jmh/file/bdfc7d3a6ebf/jmh-core/src/main/java/org/openjdk/jmh/annotations/Level.java#l50
    def toChooseFrom(): Seq[Offer[Int]] = {
      val ps = new ArrayBuffer[Promise[Tx[Int]]](numToChooseFrom)
      val ofs = new ArrayBuffer[Offer[Int]](numToChooseFrom)
      var i = 0
      while (i < numToChooseFrom) {
        val p = new Promise[Tx[Int]]
        ps += p
        ofs += new SimpleOffer[Int](p)
        i += 1
      }

      ps(rng.nextInt(numToChooseFrom)).setValue(Tx.const(5))

      ofs.toSeq
    }
  }
}
