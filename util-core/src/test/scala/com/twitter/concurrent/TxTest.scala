package com.twitter.concurrent


import org.scalatest.WordSpec

import com.twitter.util.Return
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TxTest extends WordSpec {
  "Tx.twoParty" should {
    "commit when everything goes dandy" in {
      val (stx, rtx) = Tx.twoParty(123)
      val sf = stx.ack()
      assert(sf.poll === None)
      val rf = rtx.ack()
      assert(sf.poll === Some(Return(Tx.Commit(()))))
      assert(rf.poll === Some(Return(Tx.Commit(123))))
    }

    "abort when receiver nacks" in {
      val (stx, rtx) = Tx.twoParty(123)
      val sf = stx.ack()
      assert(sf.poll === None)
      rtx.nack()
      assert(sf.poll === Some(Return(Tx.Abort)))
    }

    "abort when sender nacks" in {
      val (stx, rtx) = Tx.twoParty(123)
      val rf = rtx.ack()
      assert(rf.poll === None)
      stx.nack()
      assert(rf.poll === Some(Return(Tx.Abort)))
    }

    "complain on ack ack" in {
      val (stx, rtx) = Tx.twoParty(123)
      rtx.ack()

      assert(intercept[Exception] {
        rtx.ack()
      } === Tx.AlreadyAckd)
    }

    "complain on ack nack" in {
      val (stx, rtx) = Tx.twoParty(123)
      rtx.ack()

      assert(intercept[Exception] {
        rtx.nack()
      } === Tx.AlreadyAckd)
    }

    "complain on nack ack" in {
      val (stx, rtx) = Tx.twoParty(123)
      rtx.nack()

      assert(intercept[Exception] {
        rtx.ack()
      } === Tx.AlreadyNackd)
    }

    "complain on nack nack" in {
      val (stx, rtx) = Tx.twoParty(123)
      rtx.nack()

      assert(intercept[Exception] {
        rtx.nack()
      } === Tx.AlreadyNackd)
    }

    "complain when already done" in {
      val (stx, rtx) = Tx.twoParty(123)
      stx.ack()
      rtx.ack()

      assert(intercept[Exception] {
        stx.ack()
      } === Tx.AlreadyDone)
    }
  }
}
