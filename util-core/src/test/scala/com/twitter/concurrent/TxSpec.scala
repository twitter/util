package com.twitter.concurrent

import org.specs.SpecificationWithJUnit
import com.twitter.util.Return

class TxSpec extends SpecificationWithJUnit {
  "Tx.twoParty" should {
    "commit when everything goes dandy" in {
      val (stx, rtx) = Tx.twoParty(123)
      val sf = stx.ack()
      sf.poll must beNone
      val rf = rtx.ack()
      sf.poll must beSome(Return(Tx.Commit(())))
      rf.poll must beSome(Return(Tx.Commit(123)))
    }

    "abort when receiver nacks" in {
      val (stx, rtx) = Tx.twoParty(123)
      val sf = stx.ack()
      sf.poll must beNone
      rtx.nack()
      sf.poll must beSome(Return(Tx.Abort))
    }

    "abort when sender nacks" in {
      val (stx, rtx) = Tx.twoParty(123)
      val rf = rtx.ack()
      rf.poll must beNone
      stx.nack()
      rf.poll must beSome(Return(Tx.Abort))
    }

    "complain on ack ack" in {
      val (stx, rtx) = Tx.twoParty(123)
      rtx.ack() mustNot throwAn[Exception]
      rtx.ack() must throwA(Tx.AlreadyAckd)
    }

    "complain on ack nack" in {
      val (stx, rtx) = Tx.twoParty(123)
      rtx.ack() mustNot throwAn[Exception]
      rtx.nack() must throwA(Tx.AlreadyAckd)
    }

    "complain on nack nack" in {
      val (stx, rtx) = Tx.twoParty(123)
      rtx.nack() mustNot throwAn[Exception]
      rtx.nack() must throwA(Tx.AlreadyNackd)
    }

    "complain when already done" in {
      val (stx, rtx) = Tx.twoParty(123)
      stx.ack() mustNot throwAn[Exception]
      rtx.ack() mustNot throwAn[Exception]
      stx.ack() must throwA(Tx.AlreadyDone)
    }
  }
}
