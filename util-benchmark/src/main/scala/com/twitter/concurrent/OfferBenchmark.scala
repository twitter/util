package com.twitter.concurrent

import com.google.caliper.SimpleBenchmark
import com.twitter.concurrent.Tx.{Commit, Result}
import com.twitter.util.{Future, Promise}
import scala.util.Random

class OfferBenchmark extends SimpleBenchmark {

  private class SimpleOffer[T](fut: Future[Tx[T]]) extends Offer[T] {
    override def prepare() = fut
  }
  private class SimpleTx[T](t: T) extends Tx[T] {
    private val futAck = Future.value(Commit(t))
    override def nack(): Unit = ()
    override def ack(): Future[Result[T]] = futAck
  }

  def timeChoose(reps: Int) {
    val tx = Tx.const(5)
    val data: Array[Seq[Offer[Int]]] = Array.fill(reps) {
      val pendingTxs = Seq.fill(3) { new Promise[Tx[Int]] }
      val offers = pendingTxs map { p => new SimpleOffer[Int](p) }
      pendingTxs(1).setValue(tx) // mark one as completed
      offers
    }

    val rnd = new Random(4125128989L)
    var i = 0
    while (i < reps) {
      val offers = data(i)
      Offer.choose(rnd, offers)
      i += 1
    }
  }

}
