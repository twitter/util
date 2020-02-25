package com.twitter.util

import org.scalatest.WordSpec

class CancellableTest extends WordSpec {
  "Cancellable" should {
    "cancel test" in {
      Boolean cancelMethodCalled = false
      Boolean linkToMethodCalled = false
      val s = new CancellableObjectTest extends Cancellable {
        def cancel(): Unit = { 
          cancelMethodCalled = true
        }
        def linkTo(other: CancellableObjectTest): Unit = {
          linkToMethodCalled = true
        }
      }
      assert(!s.isCancelled)
      assert(!cancelMethodCalled)
      s.cancel()
      assert(cancelMethodCalled)
      assert(!linkToMethodCalled)
      s.linkTo(s)
      assert(linkToMethodCalled)
    }
  }
  "CancellableSink" should {
    "cancel once" in {
      var count = 0
      def increment: Unit = count += 1
      val s = new CancellableSink(increment)
      s.cancel()
      assert(count == 1)
      s.cancel()
      assert(count == 1)
    }
    "confirm cancelled" in {
      var count = 0
      def increment: Unit = count += 1
      val s = new CancellableSink(increment)
      s.cancel()
      assert(s.isCancelled)
    }
    "linking not supported" in {
      var count = 1
      def multiply: Unit = count *= 2
      val s1 = new CancellableSink(multiply)
      val s2 = new CancellableSink(multiply)
      assertThrows[Exception] {
        s1.linkTo(s2)
      }
      assertThrows[Exception] {
        s2.linkTo(s1)
      }
    }
  }
}
