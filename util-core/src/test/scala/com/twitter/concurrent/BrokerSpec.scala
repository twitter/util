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
  }
}
