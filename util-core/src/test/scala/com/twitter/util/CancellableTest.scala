package com.twitter.util

import org.scalatest.funsuite.AnyFunSuite

class CancellableTest extends AnyFunSuite {

  test("Cancellable.nil is always cancelled") {
    assert(!Cancellable.nil.isCancelled)
  }

  test("Cancellable.nil cancel method") {
    Cancellable.nil.cancel()
    assert(!Cancellable.nil.isCancelled)
  }

  test("Cancellable.nil linkTo method") {
    Cancellable.nil.linkTo(Cancellable.nil)
    assert(!Cancellable.nil.isCancelled)
  }

  test("CancellableSink cancel normal case") {
    var count = 2
    def square(): Unit = count *= count
    val s = new CancellableSink(square())
    assert(count == 2)
    s.cancel()
    assert(count == 4)
  }

  test("CancellableSink cancel once") {
    var count = 0
    def increment(): Unit = count += 1
    val s = new CancellableSink(increment())
    s.cancel()
    assert(count == 1)
    s.cancel()
    assert(count == 1)
  }

  test("CancellableSink has not cancelled") {
    var count = 0
    def increment(): Unit = count += 1
    val s = new CancellableSink(increment())
    assert(!s.isCancelled)
    assert(count == 0)
  }

  test("CancellableSink confirm cancelled") {
    var count = 0
    def increment(): Unit = count += 1
    val s = new CancellableSink(increment())
    s.cancel()
    assert(s.isCancelled)
    assert(count == 1)
  }

  test("CancellableSink not support linking") {
    var count = 1
    def multiply(): Unit = count *= 2
    val s1 = new CancellableSink(multiply())
    val s2 = new CancellableSink(multiply())
    assertThrows[Exception] {
      s1.linkTo(s2)
    }
    assertThrows[Exception] {
      s2.linkTo(s1)
    }
  }

}
