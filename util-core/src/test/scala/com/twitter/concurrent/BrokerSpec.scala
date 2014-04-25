package com.twitter.concurrent


import org.scalatest.{WordSpec, Matchers}
import org.scalatest.mock.MockitoSugar
import org.mockito.ArgumentCaptor
import com.twitter.util.{Await, Return}
import com.twitter.common.objectsize.ObjectSizeCalculator

class BrokerSpec extends WordSpec with Matchers with MockitoSugar {
  "Broker" should  {
    "send data (send, recv)" in {
      val br = new Broker[Int]
      val sendF = br.send(123).sync()
      sendF.isDefined shouldBe false
      val recvF = br.recv.sync()
      recvF.isDefined shouldBe true
      Await.result(recvF) shouldEqual(123)
      sendF.isDefined shouldBe true
    }

    "send data (recv, send)" in {
      val br = new Broker[Int]
      val recvF = br.recv.sync()
      recvF.isDefined shouldBe false
      val sendF = br.send(123).sync()
      sendF.isDefined shouldBe true
      recvF.isDefined shouldBe true

      Await.result(recvF) shouldEqual(123)
    }

    "queue receivers (recv, recv, send, send)" in {
      val br = new Broker[Int]
      val r0, r1 = br.recv.sync()
      r0.isDefined shouldBe false
      r1.isDefined shouldBe false
      val s = br.send(123)
      s.sync().poll shouldEqual Some(Return.Unit)
      r0.poll shouldEqual Some(Return(123))
      r1.isDefined shouldBe false
      s.sync().poll shouldEqual Some(Return.Unit)
      r1.poll shouldEqual Some(Return(123))
      s.sync().isDefined shouldBe false
    }

    "queue senders (send, send, recv, recv)" in {
      val br = new Broker[Int]
      val s0, s1 = br.send(123).sync()
      s0.isDefined shouldBe false
      s1.isDefined shouldBe false
      val r = br.recv
      r.sync().poll shouldEqual Some(Return(123))
      s0.poll shouldEqual Some(Return.Unit)
      s1.isDefined shouldBe false
      r.sync().poll shouldEqual Some(Return(123))
      s1.poll shouldEqual Some(Return.Unit)
      r.sync().isDefined shouldBe false
    }

    "interrupts" should {
      "removes queued receiver" in {
        val br = new Broker[Int]
        val recvF = br.recv.sync()
        recvF.raise(new Exception)
        br.send(123).sync().poll shouldEqual None
        recvF.poll shouldEqual None
      }

      "removes queued sender" in {
        val br = new Broker[Int]
        val sendF = br.send(123).sync()
        sendF.raise(new Exception)
        br.recv.sync().poll shouldEqual None
        sendF.poll shouldEqual None
      }

      "doesn't result in space leaks" in {
        val br = new Broker[Int]

        Offer.select(Offer.const(1), br.recv).poll shouldEqual Some(Return(1))
        val initial = ObjectSizeCalculator.getObjectSize(br)

        for (_ <- 0 until 1000) {
          Offer.select(Offer.const(1), br.recv).poll shouldEqual Some(Return(1))
          ObjectSizeCalculator.getObjectSize(br) shouldEqual(initial)
        }
      }

      "works with orElse" in {
        val b0, b1 = new Broker[Int]

        val o = b0.recv orElse b1.recv
        val f = o.sync()
        f.isDefined shouldBe false

        val sendf0 = b0.send(12).sync()
        sendf0.isDefined shouldBe false
        val sendf1 = b1.send(32).sync()
        sendf1.isDefined shouldBe true
        f.poll shouldEqual Some(Return(32))

        o.sync().poll shouldEqual Some(Return(12))
        sendf0.poll shouldEqual Some(Return.Unit)
      }
    }

    "integrate" in {
      val br = new Broker[Int]
      val offer = Offer.choose(br.recv, Offer.const(999))
      offer.sync().poll shouldEqual Some(Return(999))

      val item = br.recv.sync()
      item.isDefined shouldBe false

      br.send(123).sync().poll shouldEqual Some(Return.Unit)
      item.poll shouldEqual Some(Return(123))
    }
  }
}
