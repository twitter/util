package com.twitter.concurrent

import org.specs.SpecificationWithJUnit
import org.specs.mock.Mockito
import org.mockito.{Matchers, ArgumentCaptor}
import com.twitter.util.Return
import com.twitter.common.objectsize.ObjectSizeCalculator

class BrokerSpec extends SpecificationWithJUnit with Mockito {
  "Broker" should {
    "send data (send, recv)" in {
      val br = new Broker[Int]
      val sendF = br.send(123)()
      sendF.isDefined must beFalse
      val recvF = br.recv()
      recvF.isDefined must beTrue
      recvF() must be_==(123)
      sendF.isDefined must beTrue
    }

    "send data (recv, send)" in {
      val br = new Broker[Int]
      val recvF = br.recv()
      recvF.isDefined must beFalse
      val sendF = br.send(123)()
      sendF.isDefined must beTrue
      recvF.isDefined must beTrue

      recvF() must be_==(123)
    }

    "queue receivers (recv, recv, send, send)" in {
      val br = new Broker[Int]
      val r0, r1 = br.recv()
      r0.isDefined must beFalse
      r1.isDefined must beFalse
      val s = br.send(123)
      s().poll must beSome(Return(()))
      r0.poll must beSome(Return(123))
      r1.isDefined must beFalse
      s().poll must beSome(Return(()))
      r1.poll must beSome(Return(123))
      s().isDefined must beFalse
    }

    "queue senders (send, send, recv, recv)" in {
      val br = new Broker[Int]
      val s0, s1 = br.send(123)()
      s0.isDefined must beFalse
      s1.isDefined must beFalse
      val r = br.recv
      r().poll must beSome(Return(123))
      s0.poll must beSome(Return(()))
      s1.isDefined must beFalse
      r().poll must beSome(Return(123))
      s1.poll must beSome(Return(()))
      r().isDefined must beFalse
    }

    "cancellation" in {
      "removes queued receiver" in {
        val br = new Broker[Int]
        val recvF = br.recv.sync()
        recvF.cancel()
        br.send(123).sync().poll must beNone
        recvF.poll must beNone
      }

      "removes queued sender" in {
        val br = new Broker[Int]
        val sendF = br.send(123).sync()
        sendF.cancel()
        br.recv.sync().poll must beNone
        sendF.poll must beNone
      }

      "doesn't result in space leaks" in {
        val br = new Broker[Int]

        Offer.select(Offer.const(1), br.recv).poll must beSome(Return(1))
        val initial = ObjectSizeCalculator.getObjectSize(br)

        for (_ <- 0 until 1000) {
          Offer.select(Offer.const(1), br.recv).poll must beSome(Return(1))
          ObjectSizeCalculator.getObjectSize(br) must be_==(initial)
        }
      }

      "works with orElse" in {
        val b0, b1 = new Broker[Int]

        val o = b0.recv orElse b1.recv
        val f = o.sync()
        f.isDefined must beFalse

        val sendf0 = b0.send(12).sync()
        sendf0.isDefined must beFalse
        val sendf1 = b1.send(32).sync()
        sendf1.isDefined must beTrue
        f.poll must beSome(Return(32))

        o.sync().poll must beSome(Return(12))
        sendf0.poll must beSome(Return(()))
      }
    }

    "integrate" in {
      val br = new Broker[Int]
      val offer = Offer.choose(br.recv, Offer.const(999))
      offer.sync().poll must beSome(Return(999))

      val item = br.recv.sync()
      item.isDefined must beFalse

      br.send(123).sync().poll must beSome(Return(()))
      item.poll must beSome(Return(123))
    }
  }
}
