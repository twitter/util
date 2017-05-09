package com.twitter.app

import com.twitter.conversions.time._
import com.twitter.util.{Closable, Future, MockTimer, Promise, Time}
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

class TestApp(f: () => Unit) extends App {
  var reason: Option[String] = None
  protected override def exitOnError(reason: String) = {
    this.reason = Some(reason)
  }

  def main() = f()
}

object VeryBadApp extends App {
  var reason: String = throwRuntime()

  protected def throwRuntime(): String = {
    throw new RuntimeException("this is a bad app")
  }

  def main() = {
  }
}

@RunWith(classOf[JUnitRunner])
class AppTest extends FunSuite {
  test("App: make sure system.exit called on exception from main") {
    val test1 = new TestApp(() => throw new RuntimeException("simulate main failing"))

    test1.main(Array())

    assert(test1.reason == Some("Exception thrown in main on startup"))
  }

  test("App: propagate underlying exception from fields in app") {
    intercept[ExceptionInInitializerError] {
      VeryBadApp.main(Array.empty)
    }
  }

  test("App: register on main call, last App wins") {
    val test1 = new TestApp(() => ())
    val test2 = new TestApp(() => ())

    assert(App.registered != Some(test1))
    assert(App.registered != Some(test2))

    test1.main(Array.empty)
    assert(App.registered == Some(test1))

    test2.main(Array.empty)
    assert(App.registered == Some(test2))
  }

  test("App: pass in bad args and expect usage") {
    val test1 = new TestApp(() => ())

    test1.main(Array("-environment=staging", "-environment=staging"))
    val theReason: String = test1.reason.getOrElse {
      fail("There should have been a usage printed and was not")
    }

    assert(theReason.contains("""Error parsing flag "environment""""))
  }

  test("App: order of hooks") {
    val q = new ConcurrentLinkedQueue[Int]
    class Test1 extends App {
      onExit(q.add(4))
      postmain(q.add(3))
      def main() = q.add(2)
      premain(q.add(1))
      init(q.add(0))
    }
    new Test1().main(Array.empty)
    assert(q.toArray.toSeq == Seq(0, 1, 2, 3, 4))
  }

  test("App: sequenced exits") {
    val a = new App { def main() = () }
    val p = new Promise[Unit]
    var n1, n2 = 0
    val c1 = Closable.make{_ => n1 += 1; p}
    val c2 = Closable.make{_ => n2 += 1; Future.Done}
    a.closeOnExitLast(c2)
    a.closeOnExit(c1)
    val f = a.close()

    assert(n1 == 1)
    // c2 hasn't been closed yet
    assert(n2 == 0)
    assert(!f.isDefined)

    p.setDone
    assert(n2 == 1)
    assert(f.isDefined)
  }

  test("App: sequenced exits respect deadline") {
    val t = new MockTimer
    val a = new App {
      override lazy val shutdownTimer = t
      def main() = ()
    }

    Time.withCurrentTimeFrozen { ctl =>
      var n1, n2 = 0
      val c1 = Closable.make { _ => n1 += 1; Future.never }
      val c2 = Closable.make { _ => n2 += 1; Future.Done }
      a.closeOnExitLast(c2)
      a.closeOnExit(c1)
      val f = a.close(Time.now + 1.second)

      assert(n1 == 1)
      // c2 hasn't been closed yet
      assert(n2 == 0)
      assert(!f.isDefined)

      ctl.advance(2.seconds)
      t.tick()

      assert(n2 == 1)
      assert(f.isDefined)
    }
  }

  test("App: closes all closables that were added by closeOnExit") {
    val app = new TestApp(() => ())

    @volatile var closed = false
    val closable = Closable.make { _ =>
      closed = true
      Future.Done
    }

    app.closeOnExit(closable)

    assert(!closed)
    app.main(Array.empty)
    assert(closed)
  }

  test("App: closes all closables that were added by closeOnExit after app exited") {
    val app = new TestApp(() => ())

    @volatile var closed = false
    val closable = Closable.make { _ =>
      closed = true
      Future.Done
    }

    assert(!closed)
    app.main(Array.empty)
    assert(!closed)

    app.closeOnExit(closable)
    assert(closed)
  }
}
