package com.twitter.logging

import org.scalatest.funsuite.AnyFunSuite

class HasLogLevelTest extends AnyFunSuite {

  private class WithLogLevel(val logLevel: Level, cause: Throwable = null)
      extends Exception(cause)
      with HasLogLevel

  test("unapply returns None when there are no HasLogLevels") {
    assert(HasLogLevel.unapply(new RuntimeException()).isEmpty)
  }

  test("unapply on a HasLogLevel") {
    val ex = new WithLogLevel(Level.CRITICAL)
    val extracted = HasLogLevel.unapply(ex)
    assert(extracted.contains(ex.logLevel))
  }

  test("unapply returns the first HasLogLevel") {
    val ex1 = new WithLogLevel(Level.CRITICAL)
    val ex2 = new WithLogLevel(Level.ALL, ex1)
    val ex3 = new RuntimeException(ex2)
    val ex4 = new RuntimeException(ex3)
    val extracted = HasLogLevel.unapply(ex4)
    assert(extracted.contains(ex2.logLevel))
  }

  test("unapply returns the first HasLogLevel even when others are more severe") {
    val fatal = new WithLogLevel(Level.FATAL)
    val trace = new WithLogLevel(Level.TRACE, fatal)
    val extracted = HasLogLevel.unapply(trace)
    assert(extracted.contains(trace.logLevel))
  }

}
