package com.twitter.util

import org.scalatest.WordSpec

class CancellableTest extends WordSpec {
  "Cancellable" should {
    "variable isCancelled in object" in {
      assert(!Cancellable.nil.isCancelled)
    }
    "method cancel in object" in {
      Cancellable.nil.cancel()
      assert(!Cancellable.nil.isCancelled)
    }
    "method linkTo in object" in {
      Cancellable.nil.linkTo(Cancellable.nil)
      assert(!Cancellable.nil.isCancelled)
    }
  }
  "CancellableSink" should {
    "cancel normal case" in {
      var count = 2
      def square: Unit = count *= count
      val s = new CancellableSink(square)
      assert(count == 2)
      s.cancel()
      assert(count == 4)
    }
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
    "not support linking" in {
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
