package com.twitter.concurrent

import com.twitter.util.Return
import org.scalatest.funsuite.AnyFunSuite

class TxTest extends AnyFunSuite {
  test("Tx.twoParty should commit when everything goes dandy") {
    val (stx, rtx) = Tx.twoParty(123)
    val sf = stx.ack()
    assert(sf.poll == None)
    val rf = rtx.ack()
    assert(sf.poll == Some(Return(Tx.Commit(()))))
    assert(rf.poll == Some(Return(Tx.Commit(123))))
  }

  test("Tx.twoParty should abort when receiver nacks") {
    val (stx, rtx) = Tx.twoParty(123)
    val sf = stx.ack()
    assert(sf.poll == None)
    rtx.nack()
    assert(sf.poll == Some(Return(Tx.Abort)))
  }

  test("Tx.twoParty should abort when sender nacks") {
    val (stx, rtx) = Tx.twoParty(123)
    val rf = rtx.ack()
    assert(rf.poll == None)
    stx.nack()
    assert(rf.poll == Some(Return(Tx.Abort)))
  }

  test("Tx.twoParty should complain on ack ack") {
    val (stx, rtx) = Tx.twoParty(123)
    rtx.ack()

    assert(intercept[Exception] {
      rtx.ack()
    } == Tx.AlreadyAckd)
  }

  test("Tx.twoParty should complain on ack nack") {
    val (stx, rtx) = Tx.twoParty(123)
    rtx.ack()

    assert(intercept[Exception] {
      rtx.nack()
    } == Tx.AlreadyAckd)
  }

  test("Tx.twoParty should complain on nack ack") {
    val (stx, rtx) = Tx.twoParty(123)
    rtx.nack()

    assert(intercept[Exception] {
      rtx.ack()
    } == Tx.AlreadyNackd)
  }

  test("Tx.twoParty should complain on nack nack") {
    val (stx, rtx) = Tx.twoParty(123)
    rtx.nack()

    assert(intercept[Exception] {
      rtx.nack()
    } == Tx.AlreadyNackd)
  }

  test("Tx.twoParty should complain when already done") {
    val (stx, rtx) = Tx.twoParty(123)
    stx.ack()
    rtx.ack()

    assert(intercept[Exception] {
      stx.ack()
    } == Tx.AlreadyDone)
  }
}
