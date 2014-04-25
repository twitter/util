package com.twitter.util


import org.mockito.Mockito._
import org.mockito.Matchers._
import org.scalatest.{WordSpec, Matchers}
import org.scalatest.mock.MockitoSugar
import com.twitter.conversions.time._
import java.util.concurrent.ConcurrentLinkedQueue

object MonitorSpec {
  class MockMonitor extends Monitor {
    def handle(cause: Throwable) = false
  }
}

class MonitorSpec extends WordSpec with Matchers with MockitoSugar {
  import MonitorSpec._

  "Monitor#orElse" should {
    class MonitorOrElseHelper {
      val m0, m1, m2 = spy(new MockMonitor)
      Seq(m0, m1, m2) foreach { m => when(m.handle(any[Throwable])).thenReturn(true) }
      val exc = new Exception
      val m = m0 orElse m1 orElse m2
    }

    "stop at first successful handle" in {
      val h = new MonitorOrElseHelper
      import h._

      m.handle(exc) shouldBe true

      verify(m0).handle(exc)
      verify(m1, never()).handle(exc)
      verify(m2, never()).handle(exc)

      when(m0.handle(any[Throwable])).thenReturn(false)

      m.handle(exc) shouldBe true
      verify(m0, times(2)).handle(exc)
      verify(m1).handle(exc)
      verify(m2, never()).handle(exc)
    }

    "fail when no nothing got handled" in {
      val h = new MonitorOrElseHelper
      import h._

      Seq(m0, m1, m2) foreach { m => when(m.handle(any[Throwable]))thenReturn(false) }
      m.handle(exc) shouldBe false
      Seq(m0, m1, m2) foreach { m => verify(m).handle(exc) }
    }

    "wrap Monitor exceptions and pass them on" in {
      val h = new MonitorOrElseHelper
      import h._

      val rte = new RuntimeException("really bad news")
      when(m0.handle(any[Throwable])).thenThrow(rte)

      m.handle(exc) shouldBe true
      verify(m0).handle(exc)
      verify(m1).handle(MonitorException(exc, rte))
    }
  }

  "Monitor#andThen" should  {
    class MonitorAndThenHelper {
      val m0, m1 = spy(new MockMonitor)
      when(m0.handle(any[Throwable])).thenReturn(true)
      when(m1.handle(any[Throwable])).thenReturn(true)
      val m = m0 andThen m1
      val exc = new Exception
    }

    "run all monitors" in {
      val h = new MonitorAndThenHelper
      import h._

      when(m0.handle(any[Throwable])).thenReturn(true)
      when(m1.handle(any[Throwable])).thenReturn(true)

      m.handle(exc) shouldBe true
      verify(m0).handle(exc)
      verify(m1).handle(exc)
    }

    "be succcessful when any underlying monitor is" in {
      val h = new MonitorAndThenHelper
      import h._

      when(m0.handle(any[Throwable])).thenReturn(false)
      m.handle(exc) shouldBe true
      when(m1.handle(any[Throwable])).thenReturn(false)
      m.handle(exc) shouldBe false
    }

    "wrap Monitor exceptions and pass them on" in {
      val h = new MonitorAndThenHelper
      import h._

      val rte = new RuntimeException("really bad news")
      when(m0.handle(any[Throwable])).thenThrow(rte)

      m.handle(exc) shouldBe true
      verify(m0).handle(exc)
      verify(m1).handle(MonitorException(exc, rte))
    }

    "fail if both monitors throw" in {
      val h = new MonitorAndThenHelper
      import h._

      val rte = new RuntimeException("really bad news")
      when(m0.handle(any[Throwable])).thenThrow(rte)
      when(m1.handle(any[Throwable])).thenThrow(rte)

      m.handle(exc) shouldBe false
    }
  }

  "Monitor.get, Monitor.set()" should  {
    val m = spy(new MockMonitor)

    "maintain current monitor" in Monitor.restoring {
      when(m.handle(any[Throwable])).thenReturn(true)
      Monitor.set(m)
      Monitor.get shouldBe m
    }
  }

  "Monitor.handle" should  {
    val m = spy(new MockMonitor)

    "dispatch to current monitor" in Monitor.restoring {
      when(m.handle(any[Throwable])).thenReturn(true)
      val exc = new Exception
      Monitor.set(m)
      Monitor.handle(exc)
      verify(m).handle(exc)
    }
  }

  "Monitor.restore" should  {
    "restore current configuration" in {
      val orig = Monitor.get
      Monitor.restoring {
        Monitor.set(mock[Monitor])
      }
      Monitor.get shouldBe orig
    }
  }

  "Monitor.mk" should  {
    class MonitorMkHelper {
      class E1 extends Exception
      class E2 extends E1
      class F1 extends Exception

      var ran = false
      val m = Monitor.mk {
        case _: E1 =>
          ran = true
          true
      }
    }

    "handle E1" in {
      val h = new MonitorMkHelper
      import h._

      m.handle(new E1) shouldBe true
      ran shouldBe true
    }

    "handle E2" in {
      val h = new MonitorMkHelper
      import h._

      m.handle(new E2) shouldBe true
      ran shouldBe true
    }

    "not handle F1" in {
      val h = new MonitorMkHelper
      import h._

      m.handle(new F1) shouldBe false
      ran shouldBe false
    }
  }
}
