package com.twitter.app

import com.twitter.app.LoadService.Binding
import com.twitter.conversions.DurationOps._
import com.twitter.util._
import java.util.concurrent.ConcurrentLinkedQueue
import org.scalatest.FunSuite
import scala.language.reflectiveCalls

class TestApp(f: () => Unit) extends App {
  var reason: Option[String] = None
  protected override def exitOnError(reason: String, details: => String): Unit = {
    this.reason = Some(reason)
  }

  def main(): Unit = f()
}

object VeryBadApp extends App {
  var reason: String = throwRuntime()

  protected def throwRuntime(): String = {
    throw new RuntimeException("this is a bad app")
  }

  def main(): Unit = {}
}

class WeNeverClose extends App {
  override def exitOnError(throwable: Throwable): Unit = {
    throwable match {
      case _: CloseException =>
        throw throwable
      case _ =>
        exitOnError("Exception thrown in main on startup")
    }
  }

  private[this] val unClosable = new Closable {
    override def close(deadline: Time): Future[Unit] = Future.never
  }

  closeOnExit(unClosable)

  // and we're never ready
  override def isReady(implicit permit: Awaitable.CanAwait): Boolean = false

  def main(): Unit = {}
}

class WeNeverCloseButWeDoNotCare extends WeNeverClose {
  override protected val suppressGracefulShutdownErrors: Boolean = true
}

trait ErrorOnExitApp extends App {
  override val defaultCloseGracePeriod: Duration = 2.seconds

  override def exitOnError(throwable: Throwable): Unit = {
    throw throwable
  }
}

class AppTest extends FunSuite {
  test("App: make sure system.exit called on exception from main") {
    val test1 = new TestApp(() => throw new RuntimeException("simulate main failing"))

    test1.main(Array())

    assert(test1.reason.contains("Exception thrown in main on startup"))
  }

  test("App: propagate underlying exception from fields in app") {
    intercept[ExceptionInInitializerError] {
      VeryBadApp.main(Array.empty)
    }
  }

  test("App: register on main call, last App wins") {
    val test1 = new TestApp(() => ())
    val test2 = new TestApp(() => ())

    assert(!App.registered.contains(test1))
    assert(!App.registered.contains(test2))

    test1.main(Array.empty)
    assert(App.registered.contains(test1))

    test2.main(Array.empty)
    assert(App.registered.contains(test2))
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
    import scala.jdk.CollectionConverters._

    val q = new ConcurrentLinkedQueue[Int]
    class Test1 extends App {
      onExitLast(q.add(5))
      onExit(q.add(4))
      postmain(q.add(3))
      def main(): Unit = q.add(2)
      premain(q.add(1))
      init(q.add(0))
    }
    new Test1().main(Array.empty)
    assert(q.asScala.toSeq == Seq(0, 1, 2, 3, 4, 5))
  }

  test("loadServiceBinds") {
    trait Iface1
    trait Iface2
    val impl1 = new Iface1 {}
    val impl2a = new Iface2 {}
    val impl2b = new Iface2 {}
    class BindApp extends App {
      override protected[this] val loadServiceBindings: Seq[Binding[_]] = Seq(
        new Binding(classOf[Iface1], impl1),
        new Binding[Iface2](classOf[Iface2], Seq(impl2a, impl2b))
      )

      def main(): Unit = {
        assert(Seq(impl1) == LoadService[Iface1]())
        assert(Seq(impl2a, impl2b) == LoadService[Iface2]())
      }
    }
    new BindApp().main(Array.empty)
  }

  test("App: sequenced exits") {
    val a = new App { def main(): Unit = () }
    val p = new Promise[Unit]
    var n1, n2 = 0
    val c1 = Closable.make { _ =>
      n1 += 1; p
    }
    val c2 = Closable.make { _ =>
      n2 += 1; Future.Done
    }
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
      override lazy val shutdownTimer: Timer = t
      def main(): Unit = ()
    }

    Time.withCurrentTimeFrozen { ctl =>
      var n1, n2 = 0
      val c1 = Closable.make { _ =>
        n1 += 1; Future.never
      }
      val c2 = Closable.make { _ =>
        n2 += 1; Future.never
      }
      a.closeOnExitLast(c2)
      a.closeOnExit(c1)
      val f = a.close(Time.now + 1.second)

      assert(n1 == 1)
      // c2 hasn't been closed yet
      assert(n2 == 0)
      assert(!f.isDefined)

      ctl.advance(2.seconds)
      t.tick()
      t.tick()

      assert(n2 == 1)
      assert(f.isDefined)
    }
  }

  test(
    "App: sequenced exits respect deadline when closeOnExit and closeOnExitLast called after close"
  ) {
    val t = new MockTimer
    val a = new App {
      override lazy val shutdownTimer: Timer = t
      def main(): Unit = ()
    }

    Time.withCurrentTimeFrozen { ctl =>
      var n1, n2, n3, n4 = 0
      val c1 = Closable.make { _ =>
        n1 += 1; Future.never
      } // Exit
      val c2 = Closable.make { _ =>
        n2 += 1; Future.Done
      } // ExitLast
      val c3 = Closable.make { _ =>
        n3 += 1; Future.Done
      } // Late Exit
      val c4 = Closable.make { _ =>
        n4 += 1; Future.Done
      } // Late ExitLast
      a.closeOnExitLast(c2)
      a.closeOnExit(c1)
      val f = a.close(Time.now + 1.second)

      assert(n1 == 1)
      assert(n2 == 0)
      assert(n3 == 0)
      assert(n4 == 0)
      assert(!f.isDefined)

      a.closeOnExitLast(c4)
      a.closeOnExit(c3)

      assert(n1 == 1)
      assert(n2 == 0)
      assert(n3 == 1)
      assert(n4 == 0)
      assert(!f.isDefined)

      ctl.advance(2.seconds)
      t.tick()
      t.tick()

      assert(n1 == 1)
      assert(n2 == 1)
      assert(n3 == 1)
      assert(n4 == 1)
      assert(f.isDefined)
    }
  }

  test("App: late closeOnExitLast closes stalled second phase") {
    val t = new MockTimer
    val a = new App {
      override lazy val shutdownTimer: Timer = t
      def main(): Unit = ()
    }

    Time.withCurrentTimeFrozen { ctl =>
      var n1, n2, n3, n4 = 0
      val c1 = Closable.make { _ =>
        n1 += 1; Future.never
      } // Exit
      val c2 = Closable.make { _ =>
        n2 += 1; Future.never
      } // ExitLast
      val c3 = Closable.make { _ =>
        n3 += 1; Future.never
      } // Late Exit
      val c4 = Closable.make { _ =>
        n4 += 1; Future.never
      } // Late ExitLast
      a.closeOnExitLast(c2)
      a.closeOnExit(c1)
      val f = a.close(Time.now + 1.second)

      assert(n1 == 1)
      assert(n2 == 0)
      assert(n3 == 0)
      assert(n4 == 0)
      assert(!f.isDefined)

      a.closeOnExitLast(c4)
      a.closeOnExit(c3)

      assert(n1 == 1)
      assert(n2 == 0)
      assert(n3 == 1)
      assert(n4 == 0)
      assert(!f.isDefined)

      ctl.advance(2.seconds)
      t.tick()
      t.tick()

      assert(n1 == 1)
      assert(n2 == 1)
      assert(n3 == 1)
      assert(n4 == 1)
      assert(f.isDefined)
    }
  }

  test("App: closes all closables that were added by closeOnExit") {
    val app = new TestApp(() => ())

    var closed = false
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

    var closed = false
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

  test("App: exit functions properly capture non-fatal exceptions") {
    val app = new ErrorOnExitApp {
      def main(): Unit = {
        onExit {
          throw new Exception("FORCED ON EXIT")
        }

        closeOnExit(Closable.make { _ =>
          throw new Exception("FORCED CLOSE ON EXIT")
        })

        closeOnExitLast(Closable.make { _ =>
          throw new Exception("FORCED CLOSE ON EXIT LAST")
        })
      }
    }

    val e = intercept[CloseException] {
      app.main(Array.empty)
    }

    assert(e.getSuppressed.length == 3)
  }

  test("App: fatal exceptions escape exit functions") {
    // first fatal (InterruptedException) kills the app during close
    val app = new ErrorOnExitApp {
      def main(): Unit = {
        closeOnExit(Closable.make { _ =>
          throw new InterruptedException("FORCED CLOSE ON EXIT")
        })
      }
    }

    intercept[InterruptedException] {
      app.main(Array.empty)
    }
  }

  test("App: exit functions properly capture mix of non-fatal and fatal exceptions") {
    val app = new ErrorOnExitApp {
      def main(): Unit = {
        onExit {
          throw new Exception("FORCED ON EXIT")
        }

        closeOnExit(Closable.make { _ =>
          throw new Exception("FORCED CLOSE ON EXIT")
        })

        closeOnExitLast(Closable.make { _ =>
          throw new InterruptedException("FORCED CLOSE ON EXIT LAST")
        })
      }
    }

    val e = intercept[Throwable] {
      app.main(Array.empty)
    }

    assert(e.getClass == classOf[InterruptedException])
  }

  test("applications can disable graceful shutdown failures from bubbling up") {
    val app = new App {
      final override def suppressGracefulShutdownErrors = true
      final def main(): Unit = {
        closeOnExit(Closable.make(_ => Future.exception(new Exception("failed to close"))))
      }
    }

    app.main(Array.empty)
  }

  test("never closing exits with exception") {
    val t = new MockTimer
    val app = new WeNeverClose {
      override lazy val shutdownTimer: Timer = t
    }

    Time.withTimeAt(Time.epoch) { _ =>
      val exc = intercept[CloseException] {
        app.main(Array.empty)
      }

      // the CloseException should be suppressing a com.twitter.util.TimeoutException
      assert(exc.getSuppressed.length == 1)
      assert(exc.getSuppressed.head.getClass == classOf[com.twitter.util.TimeoutException])
    }
  }

  test("never closing exits with a timeout exception") {
    val t = new MockTimer
    val app = new WeNeverCloseButWeDoNotCare {
      override lazy val shutdownTimer: Timer = t
    }

    Time.withTimeAt(Time.epoch) { _ =>
      val exc = intercept[CloseException] {
        app.main(Array.empty)
      }

      // the CloseException should be suppressing a com.twitter.util.TimeoutException
      assert(exc.getSuppressed.length == 1)
      assert(exc.getSuppressed.head.getClass == classOf[com.twitter.util.TimeoutException])
    }
  }
}
