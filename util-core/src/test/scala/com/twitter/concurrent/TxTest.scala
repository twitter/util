package com.twitter.concurrent


import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import com.twitter.util.Return
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TxTest extends WordSpec with ShouldMatchers {
  "Tx.twoParty" should {
    "commit when everything goes dandy" in {
      val (stx, rtx) = Tx.twoParty(123)
      val sf = stx.ack()
      sf.poll shouldEqual None
      val rf = rtx.ack()
      sf.poll shouldEqual Some(Return(Tx.Commit(())))
      rf.poll shouldEqual Some(Return(Tx.Commit(123)))
    }

    "abort when receiver nacks" in {
      val (stx, rtx) = Tx.twoParty(123)
      val sf = stx.ack()
      sf.poll shouldEqual None
      rtx.nack()
      sf.poll shouldEqual Some(Return(Tx.Abort))
    }

    "abort when sender nacks" in {
      val (stx, rtx) = Tx.twoParty(123)
      val rf = rtx.ack()
      rf.poll shouldEqual None
      stx.nack()
      rf.poll shouldEqual Some(Return(Tx.Abort))
    }

    "complain on ack ack" in {
      val (stx, rtx) = Tx.twoParty(123)
      rtx.ack()

      intercept[Exception] {
        rtx.ack()
      } shouldEqual Tx.AlreadyAckd
    }

    "complain on ack nack" in {
      val (stx, rtx) = Tx.twoParty(123)
      rtx.ack()

      intercept[Exception] {
        rtx.nack()
      } shouldEqual Tx.AlreadyAckd
    }

    "complain on nack ack" in {
      val (stx, rtx) = Tx.twoParty(123)
      rtx.nack()

      intercept[Exception] {
        rtx.ack()
      } shouldEqual Tx.AlreadyNackd
    }

    "complain on nack nack" in {
      val (stx, rtx) = Tx.twoParty(123)
      rtx.nack()

      intercept[Exception] {
        rtx.nack()
      } shouldEqual Tx.AlreadyNackd
    }

    "complain when already done" in {
      val (stx, rtx) = Tx.twoParty(123)
      stx.ack()
      rtx.ack()

      intercept[Exception] {
        stx.ack()
      } shouldEqual Tx.AlreadyDone
    }
  }
}
