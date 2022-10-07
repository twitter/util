package com.twitter.concurrent

import com.twitter.conversions.DurationOps._
import com.twitter.util.Await
import com.twitter.util.Future
import com.twitter.util.MockTimer
import com.twitter.util.Promise
import com.twitter.util.Return
import com.twitter.util.Time
import org.mockito.Mockito._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.mockito.MockitoSugar
import scala.collection.compat.immutable.LazyList
import scala.util.Random

class SimpleOffer[T](var futures: LazyList[Future[Tx[T]]]) extends Offer[T] {
  def this(fut: Future[Tx[T]]) = this(LazyList.continually(fut))
  def this(tx: Tx[T]) = this(LazyList.continually(Future.value(tx)))

  def prepare(): Future[Tx[T]] = {
    val next = futures.head
    futures = futures.tail
    next
  }
}

class OfferSpecHelper {
  val pendingTxs = 0 until 3 map { _ => new Promise[Tx[Int]] }
  val offers = pendingTxs map { tx => spy(new SimpleOffer(tx)) }
  val offer = Offer.choose(offers: _*)
}

class TxReadyHelper extends OfferSpecHelper with MockitoSugar {
  val tx1 = mock[Tx[Int]]
  pendingTxs(1).setValue(tx1)
}

class AllTxsReadyHelper extends OfferSpecHelper with MockitoSugar {
  val txs = for (p <- pendingTxs) yield {
    val tx = mock[Tx[Int]]
    p.setValue(tx)
    tx
  }
}

class OfferOrElseHelper {
  val txp = new Promise[Tx[Int]]
  val e0 = spy(new SimpleOffer(txp))
}

class SyncIntegrationHelper {
  val tx2 = new Promise[Tx[Int]]
  val e0 = spy(
    new SimpleOffer(
      Future.value(Tx.aborted: Tx[Int]) #:: (tx2: Future[Tx[Int]]) #:: LazyList.empty
    )
  )
  val offer = e0 orElse Offer.const(123)
}

class OfferTest extends AnyFunSuite with MockitoSugar {

  def await[A](f: Future[A]): A = Await.result(f, 2.seconds)

  import Tx.Commit
  import Tx.Abort

  test("Offer.map should apply f in after Tx.ack()") {
    // mockito can't spy on anonymous classes.
    val tx = mock[Tx[Int]]
    val result = Future.value(Commit(123))
    when(tx.ack()).thenReturn(result)

    assert(tx.ack() == (result))
    val offer = spy(new SimpleOffer(tx))

    val mapped = offer map { i => (i - 100).toString }

    val f = mapped.prepare() flatMap { tx => tx.ack() }

    assert(await(f) == Commit("23"))
  }

  test("Offer.choose when a tx is already ready: prepare() prepares all") {
    val h = new TxReadyHelper
    import h._

    offers foreach { of => verify(of, never()).prepare() }
    assert(offer.prepare().isDefined == true)
    offers foreach { of => verify(of).prepare() }
  }

  test("Offer.choose when a tx is already ready should select it") {
    val h = new TxReadyHelper
    import h._

    assert(await(offer.prepare()) == tx1)
    verify(tx1, never()).ack()
    verify(tx1, never()).nack()
  }

  test("Offer.choose when a tx is already ready should nack losers") {
    val h = new TxReadyHelper
    import h.offer
    import h.pendingTxs

    offer.prepare()
    val tx = mock[Tx[Int]]
    for (i <- Seq(0, 2)) {
      pendingTxs(i).setValue(tx)
    }
    verify(tx, times(2)).nack()
    verify(tx, never()).ack()
  }

  test("Offer.choose when a tx is ready after prepare() should select it") {
    val h = new OfferSpecHelper
    import h._

    val tx = offer.prepare()
    assert(tx.isDefined == false)
    val tx0 = mock[Tx[Int]]
    pendingTxs(0).setValue(tx0)

    assert(await(tx) == tx0)
  }

  test("Offer.choose when a tx is ready after prepare() should nack losers") {
    val h = new OfferSpecHelper
    import h._

    offer.prepare()
    pendingTxs(0).setValue(mock[Tx[Int]])
    for (i <- Seq(1, 2)) {
      val tx = mock[Tx[Int]]
      pendingTxs(i).setValue(tx)
      verify(tx).nack()
      verify(tx, never()).ack()
    }
  }

  test("Offer.choose when all txs are ready should shuffle winner") {
    Time.withTimeAt(Time.epoch) { tc =>
      val h = new AllTxsReadyHelper
      import h._

      val shuffledOffer = Offer.choose(Some(new Random(Time.now.inNanoseconds)), offers)
      val histo = new Array[Int](3)
      for (_ <- 0 until 1000) {
        for (tx <- shuffledOffer.prepare())
          histo(txs.indexOf(tx)) += 1
      }

      assert(histo(0) == 311)
      assert(histo(1) == 346)
      assert(histo(2) == 343)
    }
  }

  test("Offer.choose when all txs are ready should nack losers") {
    val h = new AllTxsReadyHelper
    import h._

    val tx = await(offer.prepare())
    for (loser <- txs if loser ne tx) verify(loser).nack()
  }

  test("Offer.choose should work with 0 offers") {
    val h = new OfferSpecHelper

    val of = Offer.choose()
    assert(of.sync().poll == None)
  }

  test("Offer.sync when Tx.prepare is immediately available: when it commits") {
    val txp = new Promise[Tx[Int]]
    val offer = spy(new SimpleOffer(txp))
    val tx = mock[Tx[Int]]
    val result = Future.value(Commit(123))
    when(tx.ack()).thenReturn(result)

    assert(tx.ack() == (result))
    txp.setValue(tx)

    assert(await(offer.sync()) == 123)
    verify(tx, times(2)).ack()
    verify(tx, never()).nack()
  }

  test("Offer.sync when Tx.prepare is immediately available: retry when it aborts") {
    val txps = new Promise[Tx[Int]] #:: new Promise[Tx[Int]] #:: LazyList.empty
    val offer = spy(new SimpleOffer(txps))
    val badTx = mock[Tx[Int]]
    val result = Future.value(Abort)
    when(badTx.ack()).thenReturn(result)

    assert(badTx.ack() == (result))
    txps(0).setValue(badTx)

    val syncd = offer.sync()

    assert(syncd.poll == None)
    verify(badTx, times(2)).ack()
    verify(offer, times(2)).prepare()

    val okTx = mock[Tx[Int]]
    val okResult = Future.value(Commit(333))
    when(okTx.ack()).thenReturn(okResult)

    assert(okTx.ack() == (okResult))
    txps(1).setValue(okTx)

    assert(syncd.poll == Some(Return(333)))

    verify(okTx, times(2)).ack()
    verify(offer, times(2)).prepare()
  }

  test("Offer.sync when Tx.prepare is delayed: when it commits") {
    val tx = mock[Tx[Int]]
    val result = Future.value(Commit(123))
    when(tx.ack()).thenReturn(result)
    assert(tx.ack() == (result))
    val offer = spy(new SimpleOffer(tx))

    assert(await(offer.sync()) == 123)
    verify(tx, times(2)).ack()
    verify(tx, never()).nack()
    verify(offer).prepare()
  }

  test("Offer.const should always provide the same result") {
    val offer = Offer.const(123)

    assert(offer.sync().poll == Some(Return(123)))
    assert(offer.sync().poll == Some(Return(123)))
  }

  test("Offer.const should evaluate argument for each prepare()") {
    var i = 0
    val offer = Offer.const { i = i + 1; i }
    assert(offer.sync().poll == Some(Return(1)))
    assert(offer.sync().poll == Some(Return(2)))
  }

  test(
    "Offer.orElse with const orElse should prepare orElse event when prepare isn't immediately available") {
    val h = new OfferOrElseHelper
    import h._

    val e1 = Offer.const(123)
    val offer = e0 orElse e1
    assert(offer.sync().poll == Some(Return(123)))
    verify(e0).prepare()
    val tx = mock[Tx[Int]]
    txp.setValue(tx)
    verify(tx).nack()
    verify(tx, never()).ack()
  }

  test(
    "Offer.orElse with const orElse should not prepare orElse event when the result is immediately available") {
    val h = new OfferOrElseHelper
    import h._

    val e1 = spy(new SimpleOffer(LazyList.empty))
    val offer = e0 orElse e1
    val tx = mock[Tx[Int]]
    val result = Future.value(Commit(321))
    when(tx.ack()).thenReturn(result)

    assert(tx.ack() == (result))
    txp.setValue(tx)
    assert(offer.sync().poll == Some(Return(321)))
    verify(e0).prepare()
    verify(tx, times(2)).ack()
    verify(tx, never()).nack()
    verify(e1, never).prepare()
  }
  test(
    "Offer.orElse sync integration: when first transaction aborts should select first again if tx2 is ready") {
    val h = new SyncIntegrationHelper
    import h._

    val tx = mock[Tx[Int]]
    val result = Future.value(Commit(321))
    when(tx.ack()).thenReturn(result)

    assert(tx.ack() == (result))
    tx2.setValue(tx)

    assert(offer.sync().poll == Some(Return(321)))
    verify(e0, times(2)).prepare()
    verify(tx, times(2)).ack()
    verify(tx, never()).nack()
  }

  test(
    "Offer.orElse sync integration: when first transaction aborts should select alternative if not ready the second time") {
    val h = new SyncIntegrationHelper
    import h._

    assert(offer.sync().poll == Some(Return(123)))
    verify(e0, times(2)).prepare()

    val tx = mock[Tx[Int]]
    tx2.setValue(tx)
    verify(tx).nack()
  }

  test("Offer.foreach should synchronize on offers forever") {
    val b = new Broker[Int]
    var count = 0
    b.recv foreach { _ => count += 1 }
    assert(count == 0)
    assert(b.send(1).sync().isDefined == true)
    assert(count == 1)
    assert(b.send(1).sync().isDefined == true)
    assert(count == 2)
  }

  test("Offer.timeout should be available after timeout (prepare)") {
    Time.withTimeAt(Time.epoch) { tc =>
      implicit val timer = new MockTimer
      val e = Offer.timeout(10.seconds)
      assert(e.prepare().isDefined == false)
      tc.advance(9.seconds)
      timer.tick()
      assert(e.prepare().isDefined == false)
      tc.advance(1.second)
      timer.tick()
      assert(e.prepare().isDefined == true)
    }
  }

  test("Offer.timeout should cancel timer tasks when losing") {
    Time.withTimeAt(Time.epoch) { tc =>
      implicit val timer = new MockTimer
      val e10 = Offer.timeout(10.seconds) map { _ => 10 }
      val e5 = Offer.timeout(5.seconds) map { _ => 5 }

      val item = Offer.select(e5, e10)
      assert(item.poll == None)
      assert(timer.tasks.size == 2)
      assert(timer.nCancelled == 0)

      tc.advance(6.seconds)
      timer.tick()

      assert(item.poll == Some(Return(5)))
      assert(timer.tasks.size == 0)
      assert(timer.nCancelled == 1)
    }
  }

  test("Offer.prioritize should prioritize high priority offer") {
    val offers = 0 until 10 map { i =>
      val tx = mock[Tx[Int]]
      val result = Future.value(Commit(i))
      when(tx.ack()).thenReturn(result)
      new SimpleOffer(Future.value(tx))
    }
    val chosenOffer = Offer.prioritize(offers: _*)
    val of = chosenOffer.sync()
    assert(of.isDefined == true)
    assert(Await.result(of) == 0)
  }

  test("Integration should select across multiple brokers") {
    val b0 = new Broker[Int]
    val b1 = new Broker[String]

    val o = Offer.choose(
      b0.send(123) const { "put!" },
      b1.recv
    )

    val f = o.sync()
    assert(f.isDefined == false)
    assert(b1.send("hey").sync().isDefined == true)
    assert(f.isDefined == true)
    assert(Await.result(f) == "hey")

    val gf = b0.recv.sync()
    assert(gf.isDefined == false)
    val of = o.sync()
    assert(of.isDefined == true)
    assert(Await.result(of) == "put!")
    assert(gf.isDefined == true)
    assert(Await.result(gf) == 123)

    // syncing again fails.
    assert(o.sync().isDefined == false)
  }
}
