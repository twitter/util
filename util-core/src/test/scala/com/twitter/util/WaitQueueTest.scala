package com.twitter.util

import org.scalacheck.Gen
import org.scalatest.FunSuite
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class WaitQueueTest extends FunSuite with GeneratorDrivenPropertyChecks {

  private class EmptyK[A] extends Promise.K[A] {
    protected[util] def depth: Short = 0
    def apply(ta: Try[A]): Unit = ()
  }

  def genWaitQueueOf(n: Int): Promise.WaitQueue[Unit] =
    if (n == 0) Promise.WaitQueue.empty[Unit]
    else Promise.WaitQueue(new EmptyK[Unit], genWaitQueueOf(n - 1))

  def genWaitQueue: Gen[Promise.WaitQueue[Unit]] =
    for {
      size <- Gen.choose(0, 100)
    } yield genWaitQueueOf(size)

  test("size") {
    forAll(Gen.choose(0, 100)) { size =>
      assert(genWaitQueueOf(size).size == size)
    }
  }

  test("contains") {
    forAll(genWaitQueue) { wq =>
      val k = new EmptyK[Unit]
      assert(!wq.contains(k))
      assert(Promise.WaitQueue(k, wq).contains(k))
    }
  }

  test("remove") {
    def eql(a: Promise.WaitQueue[Unit], b: Promise.WaitQueue[Unit]): Boolean =
      (a eq b) || (
        (a ne Promise.WaitQueue.Empty) && b.contains(a.first) && eql(a.rest, b.remove(a.first))
      )

    forAll(genWaitQueue) { wq =>
      val k = new EmptyK[Unit]
      assert(eql(wq.remove(k), wq))
      assert(eql(Promise.WaitQueue(k, wq).remove(k), wq))
    }
  }
}
