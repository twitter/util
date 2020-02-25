package com.twitter.util

import org.scalatest.WordSpec

class CancellableTest extends WordSpec {
  "Cancellable" should {
    "test method cancel" in {
      var cancelMethodCalled = false
      class CancellableMethodCancelTest extends Cancellable{
        def isCancelled = false
        def cancel(): Unit = { 
          cancelMethodCalled = true
        }
        def linkTo(other: Cancellable): Unit = {}
      }
      val s = new CancellableMethodCancelTest()
      assert(!cancelMethodCalled)
      s.cancel()
      assert(cancelMethodCalled)
    }
    "test method linkTo" in {
      var linkToMethodCalled = false
      class CancellableMethodLinkToTest extends Cancellable{
        def isCancelled = false
        def cancel(): Unit = {}
        def linkTo(other: Cancellable): Unit = {
          linkToMethodCalled = true
        }
      }
      val s = new CancellableMethodLinkToTest()
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
    "has not cancelled" in {
      var count = 0
      def increment: Unit = count += 1
      val s = new CancellableSink(increment)
      assert(!s.isCancelled)
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
