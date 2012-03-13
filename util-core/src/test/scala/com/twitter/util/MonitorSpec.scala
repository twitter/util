package com.twitter.util

import org.specs.Specification
import org.specs.mock.Mockito
import com.twitter.conversions.time._
import java.util.concurrent.ConcurrentLinkedQueue

object MonitorSpec extends Specification with Mockito {
  class MockMonitor extends Monitor {
    def handle(cause: Throwable) = false
  }

  "Monitor#orElse" should {
    val m0, m1, m2 = spy(new MockMonitor)
    Seq(m0, m1, m2) foreach { _.handle(any) returns true }
    val exc = new Exception
    val m = m0 orElse m1 orElse m2

    "stop at first successful handle" in {
      m.handle(exc) must beTrue

      there was one(m0).handle(exc)
      there was no(m1).handle(exc)
      there was no(m2).handle(exc)

      m0.handle(any) returns false

      m.handle(exc) must beTrue
      there were two(m0).handle(exc)
      there was one(m1).handle(exc)
      there was no(m2).handle(exc)
    }

    "fail when no nothing got handled" in {
      Seq(m0, m1, m2) foreach { _.handle(any) returns false }
      m.handle(exc) must beFalse
      Seq(m0, m1, m2) foreach { m => there was one(m).handle(exc) }
    }

    "wrap Monitor exceptions and pass them on" in {
      val rte = new RuntimeException("really bad news")
      m0.handle(any) throws rte
      m.handle(exc) must beTrue
      there was one(m0).handle(exc)
      there was one(m1).handle(MonitorException(exc, rte))
    }
  }

  "Monitor#andThen" should {
    val m0, m1 = spy(new MockMonitor)
    m0.handle(any) returns true
    m1.handle(any) returns true
    val m = m0 andThen m1
    val exc = new Exception

    "run all monitors" in {
      m.handle(exc) must beTrue
      there was one(m0).handle(exc)
      there was one(m1).handle(exc)
    }

    "be succcessful when any underlying monitor is" in {
      m0.handle(any) returns false
      m.handle(exc) must beTrue
      m1.handle(any) returns false
      m.handle(exc) must beFalse
    }

    "wrap Monitor exceptions and pass them on" in {
      val rte = new RuntimeException("really bad news")
      m0.handle(any) throws rte
      m.handle(exc) must beTrue
      there was one(m0).handle(exc)
      there was one(m1).handle(MonitorException(exc, rte))
    }

    "fail if both monitors throw" in {
      val rte = new RuntimeException("really bad news")
      m0.handle(any) throws rte
      m1.handle(any) throws rte
      m.handle(exc) must beFalse
    }
  }

  "Monitor.get, Monitor.set()" should {
    val m = spy(new MockMonitor)
    m.handle(any) returns true

    "maintain current monitor" in Monitor.restoring {
      Monitor.set(m)
      Monitor.get mustBe m
    }
  }

  "Monitor.handle" should {
    val m = spy(new MockMonitor)
    m.handle(any) returns true

    "dispatch to current monitor" in Monitor.restoring {
      val exc = new Exception
      Monitor.set(m)
      Monitor.handle(exc)
      there was one(m).handle(exc)
    }
  }

  "Monitor.restore" should {
    "restore current configuration" in {
      val orig = Monitor.get
      Monitor.restoring {
        Monitor.set(mock[Monitor])
      }
      Monitor.get mustBe orig
    }
  }

  "Monitor.mk" should {
    class E1 extends Exception
    class E2 extends E1
    class F1 extends Exception

    var ran = false
    val m = Monitor.mk {
      case _: E1 =>
        ran = true
        true
    }

    "handle E1" in {
      m.handle(new E1) must beTrue
      ran must beTrue
    }

    "handle E2" in {
      m.handle(new E2) must beTrue
      ran must beTrue
    }

    "not handle F1" in {
      m.handle(new F1) must beFalse
      ran must beFalse
    }
  }
}
