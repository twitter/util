package com.twitter.concurrent

import org.specs.Specification
import org.specs.mock.Mockito
import org.mockito.{Matchers, ArgumentCaptor}

object BrokerSpec extends Specification with Mockito {
  "Broker (unbuffered)" should {
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
    
    "when closed" in {
      val br = new Broker[Int]
      val cf = br.onClose()
      val rf = br.recv()
      cf.isDefined must beFalse
      rf.isDefined must beFalse

      "notify" in {
        br.close()
        cf.isDefined must beTrue
      }
      
      "stop further offers" in {
        br.close()
        val sf = br.send(333)()
        sf.isDefined must beFalse
        rf.isDefined must beFalse
      }
      
      "multiplex correctly" in {
        val f = Offer.select(
          br.onClose const { 999 }, 
          br.recv)
        f.isDefined must beFalse
        br.close()
        f.isDefined must beTrue
        f() must be_==(999)
      }
    }
  }
}