package com.twitter.concurrent

import scala.collection.mutable.ArrayBuffer

import org.specs.Specification
import org.specs.mock.Mockito
import org.mockito.{Matchers, ArgumentCaptor}

import com.twitter.util.{Future, Return, Promise}
import com.twitter.util.{Time, MockTimer}
import com.twitter.conversions.time._

class SimpleOffer[T](var value: Option[T] = None, objs: Seq[AnyRef] = Seq()) extends Offer[T] {
  case class Enqueue(s: Setter, var dequeued: Boolean) {
    def apply(v: T) {
      require(!dequeued)
      val set = s()
      require(set.isDefined)
      set.get(v)
    }
  }
  var enqueues: Seq[Enqueue] = Seq()

  def poll() = value map { v => () => v }
  def enqueue(s: Setter) = {
    val eq = Enqueue(s, false)
    val dq = { () => eq.dequeued = true }
    enqueues ++= Seq(eq)
    dq
  }
  def objects = objs
}

class SimpleSetter[T] extends Offer[T]#Setter {
  private[this] var v: Option[T] = None
  private[this] var taken = false
  def apply() = synchronized {
    if (taken) {
      None
    } else {
      taken = true
      Some { x => v = Some(x) }
    }
  }

  def get = v
}

object OfferSpec extends Specification with Mockito {
  private[OfferSpec] class DynamicHashCode{
    var internalHashCode: Int = 0
    override def hashCode(): Int = internalHashCode
    def ChangeHashCode(a: Int): Unit = {
      internalHashCode = a
    }
  }

  private[OfferSpec] class ConstantHashCode{
    override def hashCode(): Int = 0
  }

  "Offer.ObjectOrder" should {

    "Add and retrieve objects in the order List" in {
      var r1 = new Object()
      var r2 = new Object()

      // add objects to the order list
      Offer.ObjectOrder.indexOf(r1) must be(0)
      Offer.ObjectOrder.order must haveSize(1)
      Offer.ObjectOrder.indexOf(r2) must be(1)
      Offer.ObjectOrder.order must haveSize(2)

      // retrieve objects from the order list
      Offer.ObjectOrder.indexOf(r1) must be(0)
      Offer.ObjectOrder.indexOf(r2) must be(1)

      // what we really want to test is that the entries pointing to GC-ed objects will be removed from
      // the order list when ObjectOrder.Compare is called. However we cannot force GC, so we mimic the
      // behavior by clearing the first entry to pretend its object has been Gc-ed
      Offer.ObjectOrder.order.head.clear()
      Offer.ObjectOrder.order = Offer.ObjectOrder.order.filterNot(r => r.get.isEmpty)
      Offer.ObjectOrder.order must haveSize(1)
    }

    "Changing hashcode" in {
      val a = new DynamicHashCode()
      val b = new ConstantHashCode()
      val originalOrder = Offer.ObjectOrder.compare(a, b)
      originalOrder must be_!=(0)

      a.ChangeHashCode(10)
      Offer.ObjectOrder.compare(a, b) must be_==(originalOrder)
      a.ChangeHashCode(-10)
      Offer.ObjectOrder.compare(a, b) must be_==(originalOrder)
    }
  }

  "Offer.map" should {
    // mockito can't spy on anonymous classes.
    val e = spy(new SimpleOffer[Int](Some(123)))
    val mapped = e map { i => (i - 100).toString }

    "apply f in poll" in {
      mapped.poll() must beSome[() => String].which { _() == "23" }
      there was no(e).enqueue(any)
    }

   "apply f in enqueue" in {
     val s = new SimpleSetter[String]
     mapped.enqueue(s)
     e.enqueues must haveSize(1)
     val eq = e.enqueues.head
     eq(200)  // do the set
     s.get must beSome("100") // trickles up
   }
  }

  "Offer.choose" should {
    val es = Seq(
      spy(new SimpleOffer[Int]),
      spy(new SimpleOffer[Int]),
      spy(new SimpleOffer[Int]))
    val e = Offer.choose(es:_*)

    "select the first among unsatisfied events (poll)" in {
      e.poll() must beNone
      es foreach { e =>
        e.enqueues must beEmpty
        there was one(e).poll()
      }

      es(0).value = Some(333)
      e.poll() must beSome[() => Int].which { _() == 333 }
    }

    "select the first among unsatisfied events (enqueue)" in {
      val s = new SimpleSetter[Int]
      e.enqueue(s)
      es foreach { e =>
        e.enqueues must haveSize(1)
        there was one(e).enqueue(any)
      }

      s.get must beNone
      es(1).enqueues.head(111)
      s.get must beSome(111)
    }

    "shuffle events" in Time.withTimeAt(Time.epoch) { tc =>
      (es zipWithIndex) foreach { case (e, i) => e.value = Some(i) }
      val e = Offer.choose(es:_*)

      e.poll() must beSome[()=> Int].which { _() == 2 }
      tc.advance(2.seconds)
      e.poll() must beSome[() => Int].which { _() == 0 }
    }
  }

  "Offer.sync" should {
    // this appears to be impossible: "lock all involved objects in object-id order"
    "succeed immediately when poll is successful" in {
      val e = spy(new SimpleOffer[Int](Some(111)))
      val f = e()
      f.isDefined must beTrue
      f() must be_==(111)
      there was one(e).poll()
      there was no(e).enqueue(any)
    }

    "enqueue when poll is unsuccesful" in {
      val e = spy(new SimpleOffer[Int]())
      val f = e()
      f.isDefined must beFalse
      there was one(e).poll()
      there was one(e).enqueue(any)
      e.enqueues must haveSize(1)
      val eq = e.enqueues.head
      eq(123)

      "propagate value" in {
        f.isDefined must beTrue
        f() must be_==(123)
      }

      "dequeue" in {
        eq.dequeued must beTrue
      }
    }
  }

  "Offer.const" should {
    "always provide the same result" in {
      val e = Offer.const(123)
      e.poll() must beSome[() => Int].which { _() == 123 }
      e.poll() must beSome[() => Int].which { _() == 123 }
    }

    "evaluate argument for each poll()" in {
      var i = 0
      val e = Offer.const { i = i + 1; i }
      e.poll() must beSome[() => Int].which { _() == 1 }
      e.poll() must beSome[() => Int].which { _() == 2 }
    }

    "catch illegal use" in {
      val e = Offer.const(123)
      e.enqueue(new SimpleSetter[Int]) must throwA[IllegalStateException]
    }
  }

  "Offer.orElse" should {
    "with const orElse" in {
      val e0 = spy(new SimpleOffer[Int])
      val e1 = mock[Offer[Int]]
      e1.poll() returns Some(() => 123)
      val e = e0 orElse e1

      "poll orElse event when poll fails" in {
        e.poll() must beSome[() => Int].which { _() == 123 }
        there was one(e0).poll()
        there was one(e1).poll()
        there was no(e0).enqueue(any)
        there was no(e1).enqueue(any)
      }

      "not poll orElse event when poll succeed" in {
        e0.value = Some(999)
        e.poll() must beSome[() => Int].which { _() == 999 }
        there was one(e0).poll()
        there was no(e1).poll()
        there was no(e0).enqueue(any)
        there was no(e1).enqueue(any)
      }

      "not enqueue orElse event" in {
        e1.poll() returns None
        e.poll() must beNone
        there was one(e0).poll()
        there was one(e1).poll()
        there was no(e0).enqueue(any)
        there was no(e1).enqueue(any)
        e.enqueue(new SimpleSetter[Int])
        there was no(e0).enqueue(any)
        there was one(e1).enqueue(any)
      }
    }
  }

  "Offer.foreach" should {
    "synchronize on offers forever" in {
      val b = new Broker[Int]
      var count = 0
      b.recv foreach { _ => count += 1 }
      count must be_==(0)
      b.send(1)().isDefined must beTrue
      count must be_==(1)
      b.send(1)().isDefined must beTrue
      count must be_==(2)
    }
  }

  "Offer.timeout" should {
    "be available after timeout (poll)" in Time.withTimeAt(Time.epoch) { tc =>
      implicit val timer = new MockTimer
      val e = Offer.timeout(10.seconds)
      e.poll() must beNone
      tc.advance(9.seconds)
      timer.tick()
      e.poll() must beNone
      tc.advance(1.second)
      timer.tick()
      e.poll() must beSomething
    }

    "be available after timeout (enqueue)" in Time.withTimeAt(Time.epoch) { tc =>
      implicit val timer = new MockTimer
      val e = Offer.timeout(10.seconds)
      val s = new SimpleSetter[Unit]
      e.enqueue(s)
      s.get must beNone
      tc.advance(9.seconds)
      timer.tick()
      s.get must beNone
      tc.advance(1.second)
      timer.tick()
      s.get must beSomething
    }

    "cancel timer task when cancelled" in Time.withTimeAt(Time.epoch) { tc =>
      implicit val timer = new MockTimer
      val e = Offer.timeout(10.seconds)
      val s = new SimpleSetter[Unit]
      val cancel = e.enqueue(s)
      s.get must beNone
      timer.tasks must haveSize(1)
      val task = timer.tasks(0)
      task.isCancelled must beFalse
      cancel()
      task.isCancelled must beTrue
      tc.advance(10.seconds)
      timer.tick()
      s.get must beNone  // didn't fire. was cancelled.
    }
  }

  "Offer.enumToChannel" should {
    val ch = new ChannelSource[Int]
    val b = new Broker[Int]
    val buf = new ArrayBuffer[Int]
    b.recv.enumToChannel(ch)

    "synchronize only when a responder is present" in {
      val f0 = b.send(123)()
      f0.isDefined must beFalse
      val r0 = ch respond { v => buf += v; Future.Done }
      f0.isDefined must beTrue
      buf.toSeq must be_==(Seq(123))
      b.send(333)().isDefined must beTrue
      buf.toSeq must be_==(Seq(123, 333))
      r0.dispose()
      b.send(999)().isDefined must beTrue  // buffered
      val f1 = b.send(111)()
      f1.isDefined must beFalse
      ch respond { v => buf += v; Future.Done }
      f1.isDefined must beTrue
      buf.toSeq must be_==(Seq(123, 333, 999, 111))
    }

    "stop synchronizing on close" in {
      ch respond { v => buf += v; Future.Done }
      b.send(123)().isDefined must beTrue
      b.send(333)().isDefined must beTrue
      buf.toSeq must be_==(Seq(123, 333))
      ch.close()
      b.send(444)().isDefined must beTrue // buffered
      buf.toSeq must be_==(Seq(123, 333))  // but value doesn't make it.
      b.send(444)().isDefined must beFalse
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

      val f = o()
      f.isDefined must beFalse
      b1.send("hey")().isDefined must beTrue
      f.isDefined must beTrue
      f() must be_==("hey")

      val gf = b0.recv()
      gf.isDefined must beFalse
      val of = o()
      of.isDefined must beTrue
      of() must be_==("put!")
      gf.isDefined must beTrue
      gf() must be_==(123)

      // syncing again fails.
      o().isDefined must beFalse
    }
  }
}

