package com.twitter.concurrent

import org.specs.SpecificationWithJUnit
import org.specs.mock.Mockito

import com.twitter.util.{Future, Return, Promise, Await}
import com.twitter.util.{Time, MockTimer}
import com.twitter.conversions.time._
import scala.util.Random

class SimpleOffer[T](var futures: Stream[Future[Tx[T]]]) extends Offer[T] {
  def this(fut: Future[Tx[T]]) = this(Stream.continually(fut))
  def this(tx: Tx[T]) = this(Stream.continually(Future.value(tx)))

  def prepare() = {
    val next #:: rest = futures
    futures = rest
    next
  }
}

class OfferSpec extends SpecificationWithJUnit with Mockito {
  import Tx.{Commit, Abort}

  "Offer.map" should {
    // mockito can't spy on anonymous classes.
    val tx = mock[Tx[Int]]
    tx.ack() returns Future.value(Commit(123))
    val offer = spy(new SimpleOffer(tx))

    val mapped = offer map { i => (i - 100).toString }

    "apply f in after Tx.ack()" in {
      val f = mapped.prepare() flatMap { tx => tx.ack() }
      f must beLike {
        case Future(Return(Commit("23"))) => true
      }
    }
  }

  "Offer.choose" should {
    val pendingTxs = 0 until 3 map { _ => new Promise[Tx[Int]] }
    val offers = pendingTxs map { tx => spy(new SimpleOffer(tx)) }
    val offer = Offer.choose(offers:_*)

    "when a tx is already ready" in {
      val tx1 = mock[Tx[Int]]
      pendingTxs(1).setValue(tx1)

      "prepare() prepares all" in {
        offers foreach { of => there was no(of).prepare() }
        offer.prepare().isDefined must beTrue
        offers foreach { of => there was one(of).prepare() }
      }

      "select it" in {
        offer.prepare() must beLike {
          case Future(Return(tx)) => tx eq tx1
        }
        there was no(tx1).ack()
        there was no(tx1).nack()
      }

      "nack losers" in {
        offer.prepare()
        for (i <- Seq(0, 2)) {
          val tx = mock[Tx[Int]]
          pendingTxs(i).setValue(tx)
          there was one(tx).nack()
          there was no(tx).ack()
        }
      }
    }

    "when a tx is ready after prepare()" in {
      "select it" in {
        val tx = offer.prepare()
        tx.isDefined must beFalse
        val tx0 = mock[Tx[Int]]
        pendingTxs(0).setValue(tx0)
        tx must beLike {
          case Future(Return(tx)) => tx eq tx0
        }
      }

      "nack losers" in {
        offer.prepare()
        pendingTxs(0).setValue(mock[Tx[Int]])
        for (i <- Seq(1, 2)) {
          val tx = mock[Tx[Int]]
          pendingTxs(i).setValue(tx)
          there was one(tx).nack()
          there was no(tx).ack()
        }
      }
    }

    "when all txs are ready" in {
      val txs = for (p <- pendingTxs) yield {
        val tx = mock[Tx[Int]]
        p.setValue(tx)
        tx
      }

      "shuffle winner" in Time.withTimeAt(Time.epoch) { tc =>
        val shuffledOffer = Offer.choose(new Random(Time.now.inNanoseconds), offers)
        val histo = new Array[Int](3)
        for (_ <- 0 until 1000) {
          for (tx <- shuffledOffer.prepare())
            histo(txs.indexOf(tx)) += 1
        }

        histo(0) must be_==(311)
        histo(1) must be_==(346)
        histo(2) must be_==(343)
      }

      "nack losers" in {
        offer.prepare() must beLike {
          case Future(Return(tx)) =>
            for (loser <- txs if loser ne tx)
              there was one(loser).nack()
            true
        }
      }
    }

    "work with 0 offers" in {
      val of = Offer.choose()
      of.sync().poll must beNone
    }
  }

  "Offer.sync" should {
    "when Tx.prepare is immediately available" in {
      "when it commits" in {
        val txp = new Promise[Tx[Int]]
        val offer = spy(new SimpleOffer(txp))
        val tx = mock[Tx[Int]]
        tx.ack() returns Future.value(Commit(123))
        txp.setValue(tx)
        offer.sync() must beLike {
          case Future(Return(123)) => true
        }
        there was one(tx).ack()
        there was no(tx).nack()
      }

      "retry when it aborts" in {
        val txps = new Promise[Tx[Int]] #:: new Promise[Tx[Int]] #:: Stream.empty
        val offer = spy(new SimpleOffer(txps))
        val badTx = mock[Tx[Int]]
        badTx.ack() returns Future.value(Abort)
        txps(0).setValue(badTx)

        val syncd = offer.sync()

        syncd.poll must beNone
        there was one(badTx).ack()
        there were two(offer).prepare()

        val okTx = mock[Tx[Int]]
        okTx.ack() returns Future.value(Commit(333))
        txps(1).setValue(okTx)

        syncd.poll must beSome(Return(333))

        there was one(okTx).ack()
        there were two(offer).prepare()
      }
    }

    "when Tx.prepare is delayed" in {
      "when it commits" in {
        val tx = mock[Tx[Int]]
        tx.ack() returns Future.value(Commit(123))
        val offer = spy(new SimpleOffer(tx))

        offer.sync() must beLike {
          case Future(Return(123)) => true
        }
        there was one(tx).ack()
        there were no(tx).nack()
        there was one(offer).prepare()
      }
    }
  }

  "Offer.const" should {
    "always provide the same result" in {
      val offer = Offer.const(123)

      offer.sync().poll must beSome(Return(123))
      offer.sync().poll must beSome(Return(123))
    }

    "evaluate argument for each prepare()" in {
      var i = 0
      val offer = Offer.const { i = i + 1; i }
      offer.sync().poll must beSome(Return(1))
      offer.sync().poll must beSome(Return(2))
    }
  }

  "Offer.orElse" should {
    "with const orElse" in {
      val txp = new Promise[Tx[Int]]
      val e0 = spy(new SimpleOffer(txp))

      "prepare orElse event when prepare isn't immediately available" in {
        val e1 = Offer.const(123)
        val offer = e0 orElse e1
        offer.sync().poll must beSome(Return(123))
        there was one(e0).prepare()
        val tx = mock[Tx[Int]]
        txp.setValue(tx)
        there was one(tx).nack()
        there were no(tx).ack()
      }

      "not prepare orElse event when the result is immediately available" in {
        val e1 = spy(new SimpleOffer(Stream.empty))
        val offer = e0 orElse e1
        val tx = mock[Tx[Int]]
        tx.ack() returns Future.value(Commit(321))
        txp.setValue(tx)
        offer.sync().poll must beSome(Return(321))
        there was one(e0).prepare()
        there was one(tx).ack()
        there was no(tx).nack()
        there was no(e1).prepare()
      }
    }

    "sync integration: when first transaction aborts" in {
      val tx2 = new Promise[Tx[Int]]
      val e0 = spy(new SimpleOffer(Future.value(Tx.aborted: Tx[Int]) #:: (tx2: Future[Tx[Int]]) #:: Stream.empty))
      val offer = e0 orElse Offer.const(123)

      "select first again if tx2 is ready" in {
        val tx = mock[Tx[Int]]
        tx.ack() returns Future.value(Commit(321))
        tx2.setValue(tx)

        offer.sync().poll must beSome(Return(321))
        there were two(e0).prepare()
        there was one(tx).ack()
        there was no(tx).nack()
      }

      "select alternative if not ready the second time" in {
        offer.sync().poll must beSome(Return(123))
        there were two(e0).prepare()

        val tx = mock[Tx[Int]]
        tx2.setValue(tx)
        there was one(tx).nack()
      }
    }
  }

  "Offer.foreach" should {
    "synchronize on offers forever" in {
      val b = new Broker[Int]
      var count = 0
      b.recv foreach { _ => count += 1 }
      count must be_==(0)
      b.send(1).sync().isDefined must beTrue
      count must be_==(1)
      b.send(1).sync().isDefined must beTrue
      count must be_==(2)
    }
  }

  "Offer.timeout" should {
    "be available after timeout (prepare)" in Time.withTimeAt(Time.epoch) { tc =>
      implicit val timer = new MockTimer
      val e = Offer.timeout(10.seconds)
      e.prepare().isDefined must beFalse
      tc.advance(9.seconds)
      timer.tick()
      e.prepare().isDefined must beFalse
      tc.advance(1.second)
      timer.tick()
      e.prepare().isDefined must beTrue
    }

    "cancel timer tasks when losing" in Time.withTimeAt(Time.epoch) { tc =>
      implicit val timer = new MockTimer
      val e10 = Offer.timeout(10.seconds) map { _ => 10 }
      val e5 = Offer.timeout(5.seconds) map { _ => 5 }

      val item = Offer.select(e5, e10)
      item.poll must beNone
      timer.tasks must haveSize(2)
      timer.nCancelled must be_==(0)

      tc.advance(6.seconds)
      timer.tick()

      item.poll must beSome(Return(5))
      timer.tasks must haveSize(0)
      timer.nCancelled must be_==(1)
    }
  }

  "Integration" should {
    "select across multiple brokers" in {
      val b0 = new Broker[Int]
      val b1 = new Broker[String]

      val o = Offer.choose(
        b0.send(123) const { "put!" },
        b1.recv
      )

      val f = o.sync()
      f.isDefined must beFalse
      b1.send("hey").sync().isDefined must beTrue
      f.isDefined must beTrue
      Await.result(f) must be_==("hey")

      val gf = b0.recv.sync()
      gf.isDefined must beFalse
      val of = o.sync()
      of.isDefined must beTrue
      Await.result(of) must be_==("put!")
      gf.isDefined must beTrue
      Await.result(gf) must be_==(123)

      // syncing again fails.
      o.sync().isDefined must beFalse
    }
  }
}
