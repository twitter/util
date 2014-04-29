package com.twitter.concurrent


import org.scalatest.{WordSpec, Matchers}
import org.scalatest.mock.MockitoSugar
import com.twitter.util.{Promise, Return, Try}

class FutureOfferSpec extends WordSpec with Matchers with MockitoSugar {
  "Future.toOffer" should {
    "activate when future is satisfied (poll)" in {
      val p = new Promise[Int]
      val o = p.toOffer
      o.prepare().poll shouldEqual None
      p() = Return(123)
      assert(o.prepare().poll match {
         case Some(Return(tx)) =>
           tx.ack().poll match {
             case Some(Return(Tx.Commit(Return(123)))) => true
           }
      })
    }
  }
}
