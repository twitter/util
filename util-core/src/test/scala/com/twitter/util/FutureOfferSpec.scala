package com.twitter.concurrent

import org.specs.Specification
import org.specs.mock.Mockito
import com.twitter.util.{Promise, Return, Try}

object FutureOfferSpec extends Specification with Mockito {
  "Future.toOffer" should {
    "activate when future is satisfied (poll)" in {
      val p = new Promise[Int]
      val o = p.toOffer
      o.poll() must beNone
      p() = Return(123)
      o.poll() must beLike {
         case Some(f) =>
           f() must be_==(Return(123))
      }
    }

    "activate when future is satisfied (enqueue)" in {
      val p = new Promise[Int]
      val o = p.toOffer
      val s = new SimpleSetter[Try[Int]]
      o.enqueue(s)
      s.get must beNone
      p() = Return(123)
      s.get must beSome(Return(123))
    }
  }
}
