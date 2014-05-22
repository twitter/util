package com.twitter.concurrent


import org.scalatest.WordSpec

import org.scalatest.mock.MockitoSugar
import org.mockito.ArgumentCaptor
import com.twitter.util.{Await, Return}
import com.twitter.common.objectsize.ObjectSizeCalculator
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BrokerTest extends WordSpec with MockitoSugar {
  "Broker" should {
    "send data (send, recv)" in {
      val br = new Broker[Int]
      val sendF = br.send(123).sync()
      assert(sendF.isDefined === false)
      val recvF = br.recv.sync()
      assert(recvF.isDefined === true)
      assert(Await.result(recvF) === 123)
      assert(sendF.isDefined === true)
    }

    "send data (recv, send)" in {
      val br = new Broker[Int]
      val recvF = br.recv.sync()
      assert(recvF.isDefined === false)
      val sendF = br.send(123).sync()
      assert(sendF.isDefined === true)
      assert(recvF.isDefined === true)

      assert(Await.result(recvF) === 123)
    }

    "queue receivers (recv, recv, send, send)" in {
      val br = new Broker[Int]
      val r0, r1 = br.recv.sync()
      assert(r0.isDefined === false)
      assert(r1.isDefined === false)
      val s = br.send(123)
      assert(s.sync().poll === Some(Return.Unit))
      assert(r0.poll === Some(Return(123)))
      assert(r1.isDefined === false)
      assert(s.sync().poll === Some(Return.Unit))
      assert(r1.poll === Some(Return(123)))
      assert(s.sync().isDefined === false)
    }

    "queue senders (send, send, recv, recv)" in {
      val br = new Broker[Int]
      val s0, s1 = br.send(123).sync()
      assert(s0.isDefined === false)
      assert(s1.isDefined === false)
      val r = br.recv
      assert(r.sync().poll === Some(Return(123)))
      assert(s0.poll === Some(Return.Unit))
      assert(s1.isDefined === false)
      assert(r.sync().poll === Some(Return(123)))
      assert(s1.poll === Some(Return.Unit))
      assert(r.sync().isDefined === false)
    }

    "interrupts" should {
      "removes queued receiver" in {
        val br = new Broker[Int]
        val recvF = br.recv.sync()
        recvF.raise(new Exception)
        assert(br.send(123).sync().poll === None)
        assert(recvF.poll === None)
      }

      "removes queued sender" in {
        val br = new Broker[Int]
        val sendF = br.send(123).sync()
        sendF.raise(new Exception)
        assert(br.recv.sync().poll === None)
        assert(sendF.poll === None)
      }

      "doesn't result in space leaks" in {
        val br = new Broker[Int]

        assert(Offer.select(Offer.const(1), br.recv).poll === Some(Return(1)))
        val initial = ObjectSizeCalculator.getObjectSize(br)

        for (_ <- 0 until 1000) {
          assert(Offer.select(Offer.const(1), br.recv).poll === Some(Return(1)))
          assert(ObjectSizeCalculator.getObjectSize(br) === initial)
        }
      }

      "works with orElse" in {
        val b0, b1 = new Broker[Int]

        val o = b0.recv orElse b1.recv
        val f = o.sync()
        assert(f.isDefined === false)

        val sendf0 = b0.send(12).sync()
        assert(sendf0.isDefined === false)
        val sendf1 = b1.send(32).sync()
        assert(sendf1.isDefined === true)
        assert(f.poll === Some(Return(32)))

        assert(o.sync().poll === Some(Return(12)))
        assert(sendf0.poll === Some(Return.Unit))
      }
    }

    "integrate" in {
      val br = new Broker[Int]
      val offer = Offer.choose(br.recv, Offer.const(999))
      assert(offer.sync().poll === Some(Return(999)))

      val item = br.recv.sync()
      assert(item.isDefined === false)

      assert(br.send(123).sync().poll === Some(Return.Unit))
      assert(item.poll === Some(Return(123)))
    }
  }
}
