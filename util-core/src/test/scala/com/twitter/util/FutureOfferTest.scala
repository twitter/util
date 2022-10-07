package com.twitter.concurrent

import com.twitter.util.Promise
import com.twitter.util.Return
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.mockito.MockitoSugar

class FutureOfferTest extends AnyFunSuite with MockitoSugar {
  test("Future.toOffer should activate when future is satisfied (poll)") {
    val p = new Promise[Int]
    val o = p.toOffer
    assert(o.prepare().poll == None)
    p() = Return(123)
    assert(o.prepare().poll match {
      case Some(Return(tx)) =>
        tx.ack().poll match {
          case Some(Return(Tx.Commit(Return(123)))) => true
          case _ => false
        }
      case _ => false
    })
  }
}
