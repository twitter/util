package com.twitter.util

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.FunSuite
import org.scalatestplus.mockito.MockitoSugar

object MonitorTest {
  class MockMonitor extends Monitor {
    def handle(cause: Throwable): Boolean = false
  }
}

class MonitorTest extends FunSuite with MockitoSugar {
  import MonitorTest._

  class MonitorOrElseHelper {
    val m0, m1, m2 = spy(new MockMonitor())
    Seq(m0, m1, m2).foreach { m => when(m.handle(any[Throwable])).thenReturn(true) }
    val exc = new Exception
    val m = m0.orElse(m1).orElse(m2)
  }

  test("Monitor#orElse should stop at first successful handle") {
    val h = new MonitorOrElseHelper
    import h._

    assert(m.handle(exc))

    verify(m0).handle(exc)
    verify(m1, never()).handle(exc)
    verify(m2, never()).handle(exc)

    when(m0.handle(any[Throwable])).thenReturn(false)

    assert(m.handle(exc))
    verify(m0, times(2)).handle(exc)
    verify(m1).handle(exc)
    verify(m2, never()).handle(exc)
  }

  test("Monitor#orElse should fail when no nothing got handled") {
    val h = new MonitorOrElseHelper
    import h._

    Seq(m0, m1, m2) foreach { m => when(m.handle(any[Throwable])).thenReturn(false) }
    assert(!m.handle(exc))
    Seq(m0, m1, m2) foreach { m => verify(m).handle(exc) }
  }

  test("Monitor#orElse should wrap Monitor exceptions and pass them on") {
    val h = new MonitorOrElseHelper
    import h._

    val rte = new RuntimeException("really bad news")
    when(m0.handle(any[Throwable])).thenThrow(rte)

    assert(m.handle(exc))
    verify(m0).handle(exc)
    verify(m1).handle(MonitorException(exc, rte))
  }

  class MonitorAndThenHelper {
    val m0, m1 = spy(new MockMonitor)
    when(m0.handle(any[Throwable])).thenReturn(true)
    when(m1.handle(any[Throwable])).thenReturn(true)
    val m = m0.andThen(m1)
    val exc = new Exception
  }

  test("Monitor#andThen should run all monitors") {
    val h = new MonitorAndThenHelper
    import h._

    when(m0.handle(any[Throwable])).thenReturn(true)
    when(m1.handle(any[Throwable])).thenReturn(true)

    assert(m.handle(exc))
    verify(m0).handle(exc)
    verify(m1).handle(exc)
  }

  test("Monitor#andThen should be succcessful when any underlying monitor is") {
    val h = new MonitorAndThenHelper
    import h._

    when(m0.handle(any[Throwable])).thenReturn(false)
    assert(m.handle(exc))
    when(m1.handle(any[Throwable])).thenReturn(false)
    assert(!m.handle(exc))
  }

  test("Monitor#andThen should wrap Monitor exceptions and pass them on") {
    val h = new MonitorAndThenHelper
    import h._

    val rte = new RuntimeException("really bad news")
    when(m0.handle(any[Throwable])).thenThrow(rte)

    assert(m.handle(exc))
    verify(m0).handle(exc)
    verify(m1).handle(MonitorException(exc, rte))
  }

  test("Monitor#andThen should fail if both monitors throw") {
    val h = new MonitorAndThenHelper
    import h._

    val rte = new RuntimeException("really bad news")
    when(m0.handle(any[Throwable])).thenThrow(rte)
    when(m1.handle(any[Throwable])).thenThrow(rte)

    assert(!m.handle(exc))
  }

  test("Monitor.get, Monitor.set() should maintain current monitor") {
    val m = new MockMonitor
    Monitor.restoring {
      Monitor.set(m)
      assert(Monitor.get == m)
    }
  }

  test("Monitor.getOption, Monitor.setOption should maintain current monitor") {
    Monitor.restoring {
      val mon = new Monitor {
        def handle(cause: Throwable): Boolean = true
      }
      Monitor.setOption(Some(mon))
      assert(Monitor.getOption.contains(mon))
      assert(mon == Monitor.get)
    }
  }

  test("Monitor.getOption, Monitor.setOption should setOption of None restores the default") {
    Monitor.restoring {
      val mon = new Monitor {
        def handle(cause: Throwable): Boolean = true
      }
      Monitor.setOption(Some(mon))
      assert(Monitor.getOption.contains(mon))

      Monitor.setOption(None)
      assert(Monitor.getOption.isEmpty)
      assert(NullMonitor eq Monitor.get)
    }
  }

  test("Monitor.handle should dispatch to current monitor") {
    val m = spy(new MockMonitor)
    Monitor.restoring {
      when(m.handle(any[Throwable])).thenReturn(true)
      val exc = new Exception
      Monitor.set(m)
      Monitor.handle(exc)
      verify(m).handle(exc)
    }
  }

  test("Monitor.restore should restore current configuration") {
    val orig = Monitor.get
    Monitor.restoring {
      Monitor.set(mock[Monitor])
    }
    assert(Monitor.get == orig)
  }

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

  test("Monitor.mk should handle E1") {
    val h = new MonitorMkHelper
    import h._

    assert(m.handle(new E1))
    assert(ran)
  }

  test("Monitor.mk should handle E2") {
    val h = new MonitorMkHelper
    import h._

    assert(m.handle(new E2))
    assert(ran)
  }

  test("Monitor.mk should handle F1") {
    val h = new MonitorMkHelper
    import h._

    assert(!m.handle(new F1))
    assert(!ran)
  }
}
