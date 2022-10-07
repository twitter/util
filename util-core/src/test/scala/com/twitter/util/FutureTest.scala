package com.twitter.util

import com.twitter.conversions.DurationOps._
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.{Future => JFuture}
import java.util.concurrent.atomic.AtomicInteger
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scala.jdk.CollectionConverters._
import scala.runtime.NonLocalReturnControl
import scala.util.Random
import scala.util.control.ControlThrowable
import scala.util.control.NonFatal

private object FutureTest {
  def await[A](f: Future[A], timeout: Duration = 5.seconds): A =
    Await.result(f, timeout)

  sealed trait Result
  case class Ret(num: Int) extends Result
  case class Thr(exn: Exception) extends Result
  case object Nvr extends Result

  def satisfy(p: Promise[Int], result: Result): Unit = result match {
    case Nvr =>
    case Thr(exn) => p() = Throw(exn)
    case Ret(value) => p() = Return(value)
  }

  def explain(left: Result, right: Result, leftFirst: Boolean): String = {
    val leftOrRight = if (leftFirst) "left" else "right"
    s": $left.join($right) where $leftOrRight is satisfied first has an unexpected result"
  }
}

class FutureTest extends AnyFunSuite with MockitoSugar with ScalaCheckDrivenPropertyChecks {
  import FutureTest._

  implicit class FutureMatcher[A](future: Future[A]) {
    def mustProduce(expected: Try[A]): Unit = {
      expected match {
        case Throw(ex) =>
          val t = intercept[Throwable] {
            await(future)
          }
          assert(t == ex)
        case Return(v) =>
          assert(await(future) == v)
      }
    }
  }

  private object FailingTimer extends Timer {
    def scheduleOnce(when: Time)(f: => Unit): TimerTask =
      throw new Exception("schedule called")
    def schedulePeriodically(when: Time, period: Duration)(f: => Unit): TimerTask =
      throw new Exception("schedule called")
    def stop(): Unit = ()
  }

  class HandledMonitor extends Monitor {
    var handled: Throwable = _
    def handle(exc: Throwable): Boolean = {
      handled = exc
      true
    }
  }

  trait TimesHelper {
    val queue = new ConcurrentLinkedQueue[Promise[Unit]]
    var complete = false
    var failure = false
    var ninterrupt = 0
    val iteration: Future[Unit] = Future.times(3) {
      val promise = new Promise[Unit]
      promise.setInterruptHandler { case _ => ninterrupt += 1 }
      queue add promise
      promise
    }
    iteration
      .onSuccess { _ => complete = true }
      .onFailure { _ => failure = true }
    assert(!complete)
    assert(!failure)
  }

  trait WhileDoHelper {
    var i = 0
    val queue = new ConcurrentLinkedQueue[HandledPromise[Unit]]
    var complete = false
    var failure = false
    val iteration: Future[Unit] = Future.whileDo(i < 3) {
      i += 1
      val promise = new HandledPromise[Unit]
      queue.add(promise)
      promise
    }

    iteration
      .onSuccess { _ => complete = true }
      .onFailure { _ => failure = true }
    assert(!complete)
    assert(!failure)
  }

  class TraverseTestSpy() {
    var goWasCalled = false
    var promise: Promise[Int] = Promise[Int]()
    val go: () => Promise[Int] = () => {
      goWasCalled = true
      promise
    }
  }

  trait CollectHelper {
    val p0, p1 = new HandledPromise[Int]
    val f: Future[Seq[Int]] = Future.collect(Seq(p0, p1))
    assert(!f.isDefined)
  }

  trait CollectToTryHelper {
    val p0, p1 = new HandledPromise[Int]
    val f: Future[Seq[Try[Int]]] = Future.collectToTry(Seq(p0, p1))
    assert(!f.isDefined)
  }

  trait SelectHelper {
    var nhandled = 0
    val p0, p1 = new HandledPromise[Int]
    val f: Future[Int] = p0.select(p1)
    assert(!f.isDefined)
  }

  trait PollHelper {
    val p = new Promise[Int]
  }

  class FatalException extends ControlThrowable

  trait MkConst {
    def apply[A](result: Try[A]): Future[A]
    def value[A](a: A): Future[A] = this(Return(a))
    def exception[A](exc: Throwable): Future[A] = this(Throw(exc))
  }

  trait MonitoredHelper {
    val inner = new HandledPromise[Int]
    val exc = new Exception("some exception")
  }

  def tests(name: String, const: MkConst): Unit = {
    test(s"object Future ($name) times when everything succeeds") {
      new TimesHelper {
        queue.poll().setDone()
        assert(!complete)
        assert(!failure)

        queue.poll().setDone()
        assert(!complete)
        assert(!failure)

        queue.poll().setDone()
        assert(complete)
        assert(!failure)
      }
    }

    test(s"object Future ($name) times when some succeed and some fail") {
      new TimesHelper {
        queue.poll().setDone()
        assert(!complete)
        assert(!failure)

        queue.poll().setException(new Exception(""))
        assert(!complete)
        assert(failure)
      }
    }

    test(s"object Future ($name) times when interrupted") {
      new TimesHelper {
        assert(ninterrupt == 0)
        iteration.raise(new Exception)
        for (i <- 1 to 3) {
          assert(ninterrupt == i)
          queue.poll().setDone()
        }
      }
    }

    test(s"object Future ($name) when") {
      var i = 0

      await {
        Future.when(false) {
          Future { i += 1 }
        }
      }
      assert(i == 0)

      await {
        Future.when(true) {
          Future { i += 1 }
        }
      }
      assert(i == 1)
    }

    test(s"object Future ($name) whileDo when everything succeeds") {
      new WhileDoHelper {
        queue.poll().setDone()
        assert(!complete)
        assert(!failure)

        queue.poll().setDone()
        assert(!complete)
        assert(!failure)

        queue.poll().setDone()

        assert(complete)
        assert(!failure)
      }
    }

    test(s"object Future ($name) whileDo when some succeed and some fail") {
      new WhileDoHelper {
        queue.poll().setDone()
        assert(!complete)
        assert(!failure)

        queue.poll().setException(new Exception(""))
        assert(!complete)
        assert(failure)
      }
    }

    test(s"object Future ($name) whileDo when interrupted") {
      new WhileDoHelper {
        assert(!queue.asScala.exists(_.handled.isDefined))
        iteration.raise(new Exception)
        assert(queue.asScala.forall(_.handled.isDefined))
      }
    }

    test(s"object Future ($name) proxyTo should reject satisfied promises") {
      val str = "um um excuse me um"
      val p1 = new Promise[String]()
      p1.update(Return(str))

      val p2 = new Promise[String]()
      val ex = intercept[IllegalStateException] { p2.proxyTo(p1) }
      assert(ex.getMessage.contains(str))
    }

    test(s"object Future ($name) proxyTo proxies success") {
      val p1 = new Promise[Int]()
      val p2 = new Promise[Int]()
      p2.proxyTo(p1)
      p2.update(Return(5))
      assert(5 == await(p1))
      assert(5 == await(p2))
    }

    test(s"object Future ($name) proxyTo proxies failure") {
      val p1 = new Promise[Int]()
      val p2 = new Promise[Int]()
      p2.proxyTo(p1)

      val t = new RuntimeException("wurmp")
      p2.update(Throw(t))
      val ex1 = intercept[RuntimeException] { await(p1) }
      assert(ex1.getMessage == t.getMessage)
      val ex2 = intercept[RuntimeException] { await(p2) }
      assert(ex2.getMessage == t.getMessage)
    }

    def batchedTests() = {
      implicit val batchedTimer: MockTimer = new MockTimer
      val batchedResult = Seq(4, 5, 6)

      test(s"object Future ($name) batched should execute after threshold is reached") {
        val m = mock[Any => Future[Seq[Int]]]
        val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
        when(f.compose(any())).thenReturn(m)

        val batcher = Future.batched(3)(f)

        when(m.apply(Iterable(1, 2, 3))).thenReturn(Future.value(batchedResult))
        batcher(1)
        verify(m, never()).apply(any[Seq[Int]])
        batcher(2)
        verify(m, never()).apply(any[Seq[Int]])
        batcher(3)
        verify(m).apply(Iterable(1, 2, 3))
      }

      test(
        s"object Future ($name) batched should execute after bufSizeFraction threshold is reached") {
        val m = mock[Any => Future[Seq[Int]]]
        val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
        when(f.compose(any())).thenReturn(m)

        val batcher = Future.batched(3, sizePercentile = 0.67f)(f)
        when(m.apply(Iterable(1, 2, 3))).thenReturn(Future.value(batchedResult))
        batcher(1)
        verify(m, never()).apply(any[Seq[Int]])
        batcher(2)
        verify(m).apply(Iterable(1, 2))
      }

      test(s"object Future ($name) batched should treat bufSizeFraction return value < 0.0f as 1") {
        val m = mock[Any => Future[Seq[Int]]]
        val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
        when(f.compose(any())).thenReturn(m)

        val batcher = Future.batched(3, sizePercentile = 0.4f)(f)

        when(m.apply(Iterable(1, 2, 3))).thenReturn(Future.value(batchedResult))
        batcher(1)
        verify(m).apply(Iterable(1))
      }

      test(
        s"object Future ($name) batched should treat bufSizeFraction return value > 1.0f should return maxSizeThreshold") {
        val m = mock[Any => Future[Seq[Int]]]
        val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
        when(f.compose(any())).thenReturn(m)

        val batcher = Future.batched(3, sizePercentile = 1.3f)(f)

        when(m.apply(Iterable(1, 2, 3))).thenReturn(Future.value(batchedResult))
        batcher(1)
        verify(m, never()).apply(any[Seq[Int]])
        batcher(2)
        verify(m, never()).apply(any[Seq[Int]])
        batcher(3)
        verify(m).apply(Iterable(1, 2, 3))
      }

      test(s"object Future ($name) batched should execute after time threshold") {
        val m = mock[Any => Future[Seq[Int]]]
        val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
        when(f.compose(any())).thenReturn(m)

        val batcher = Future.batched(3, 3.seconds)(f)

        Time.withCurrentTimeFrozen { control =>
          when(m(Iterable(1))).thenReturn(Future.value(Seq(4)))
          batcher(1)
          verify(m, never()).apply(any[Seq[Int]])

          control.advance(1.second)
          batchedTimer.tick()
          verify(m, never()).apply(any[Seq[Int]])

          control.advance(1.second)
          batchedTimer.tick()
          verify(m, never()).apply(any[Seq[Int]])

          control.advance(1.second)
          batchedTimer.tick()
          verify(m).apply(Iterable(1))
        }
      }

      test(s"object Future ($name) batched should only execute once if both are reached") {
        val m = mock[Any => Future[Seq[Int]]]
        val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
        when(f.compose(any())).thenReturn(m)

        val batcher = Future.batched(3)(f)

        Time.withCurrentTimeFrozen { control =>
          when(m(Iterable(1, 2, 3))).thenReturn(Future.value(batchedResult))
          batcher(1)
          batcher(2)
          batcher(3)
          control.advance(10.seconds)
          batchedTimer.tick()

          verify(m).apply(Iterable(1, 2, 3))
        }
      }

      test(s"object Future ($name) batched should execute when flushBatch is called") {
        val m = mock[Any => Future[Seq[Int]]]
        val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
        when(f.compose(any())).thenReturn(m)

        val batcher = Future.batched(4)(f)

        batcher(1)
        batcher(2)
        batcher(3)
        batcher.flushBatch()

        verify(m).apply(Iterable(1, 2, 3))
      }

      test(
        s"object Future ($name) batched should only execute for remaining items when flushBatch is called after size threshold is reached") {
        val m = mock[Any => Future[Seq[Int]]]
        val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
        when(f.compose(any())).thenReturn(m)

        val batcher = Future.batched(4)(f)

        batcher(1)
        batcher(2)
        batcher(3)
        batcher(4)
        batcher(5)
        verify(m, times(1)).apply(Iterable(1, 2, 3, 4))

        batcher.flushBatch()
        verify(m, times(1)).apply(Iterable(5))
      }

      test(
        s"object Future ($name) batched should only execute once when time threshold is reached after flushBatch is called") {
        val m = mock[Any => Future[Seq[Int]]]
        val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
        when(f.compose(any())).thenReturn(m)

        val batcher = Future.batched(4, 3.seconds)(f)

        Time.withCurrentTimeFrozen { control =>
          batcher(1)
          batcher(2)
          batcher(3)
          batcher.flushBatch()
          control.advance(10.seconds)
          batchedTimer.tick()

          verify(m, times(1)).apply(Iterable(1, 2, 3))
        }
      }

      test(
        s"object Future ($name) batched should only execute once when time threshold is reached before flushBatch is called") {
        val m = mock[Any => Future[Seq[Int]]]
        val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
        when(f.compose(any())).thenReturn(m)

        val batcher = Future.batched(4, 3.seconds)(f)

        Time.withCurrentTimeFrozen { control =>
          batcher(1)
          batcher(2)
          batcher(3)
          control.advance(10.seconds)
          batchedTimer.tick()
          batcher.flushBatch()

          verify(m, times(1)).apply(Iterable(1, 2, 3))
        }
      }

      test(s"object Future ($name) batched should propagates results") {
        val m = mock[Any => Future[Seq[Int]]]
        val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
        when(f.compose(any())).thenReturn(m)

        val batcher = Future.batched(3)(f)

        Time.withCurrentTimeFrozen { _ =>
          when(m(Iterable(1, 2, 3))).thenReturn(Future.value(batchedResult))
          val res1 = batcher(1)
          assert(!res1.isDefined)
          val res2 = batcher(2)
          assert(!res2.isDefined)
          val res3 = batcher(3)
          assert(res1.isDefined)
          assert(res2.isDefined)
          assert(res3.isDefined)

          assert(await(res1) == 4)
          assert(await(res2) == 5)
          assert(await(res3) == 6)

          verify(m).apply(Iterable(1, 2, 3))
        }
      }

      test(s"object Future ($name) batched should not block other batches") {
        val m = mock[Any => Future[Seq[Int]]]
        val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
        when(f.compose(any())).thenReturn(m)

        val batcher = Future.batched(3)(f)

        Time.withCurrentTimeFrozen { _ =>
          val blocker = new Promise[Unit]
          val thread = new Thread {
            override def run(): Unit = {
              when(m(batchedResult)).thenReturn(Future.value(Seq(7, 8, 9)))
              batcher(4)
              batcher(5)
              batcher(6)
              verify(m).apply(batchedResult)
              blocker.setValue(())
            }
          }

          when(m(Seq(1, 2, 3))).thenAnswer {
            new Answer[Future[Seq[Int]]] {
              def answer(invocation: InvocationOnMock): Future[Seq[Int]] = {
                thread.start()
                await(blocker)
                Future.value(batchedResult)
              }
            }
          }

          batcher(1)
          batcher(2)
          batcher(3)
          verify(m).apply(Iterable(1, 2, 3))
        }
      }

      test(s"object Future ($name) batched should swallow exceptions") {
        val m = mock[Any => Future[Seq[Int]]]
        val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
        when(f.compose(any())).thenReturn(m)

        val batcher = Future.batched(3)(f)

        when(m(Iterable(1, 2, 3))).thenAnswer {
          new Answer[Unit] {
            def answer(invocation: InvocationOnMock): Unit = {
              throw new Exception
            }
          }
        }

        batcher(1)
        batcher(2)
        batcher(3) // Success here implies no exception was thrown.
      }
    }

    batchedTests()

    test(
      s"object Future ($name) interruptible should properly ignore the underlying future on interruption") {
      val p = Promise[Unit]()
      val i = p.interruptible()
      val e = new Exception
      i.raise(e)
      p.setDone()
      assert(p.poll.contains(Return(())))
      assert(i.poll.contains(Throw(e)))
    }

    test(s"object Future ($name) interruptible should respect the underlying future") {
      val p = Promise[Unit]()
      val i = p.interruptible()
      p.setDone()
      assert(p.poll.contains(Return(())))
      assert(i.poll.contains(Return(())))
    }

    test(s"object Future ($name) interruptible should do nothing for const") {
      val f = const.value(())
      val i = f.interruptible()
      i.raise(new Exception())
      assert(f.poll.contains(Return(())))
      assert(i.poll.contains(Return(())))
    }

    test(s"object Future ($name) transverseSequentially should execute futures in order") {
      val first = new TraverseTestSpy()
      val second = new TraverseTestSpy()
      val events = Seq(first.go, second.go)

      val results = Future.traverseSequentially(events)(f => f())

      // At this point, none of the promises
      // have been fufilled, so only the first function
      // should have been called
      assert(first.goWasCalled)
      assert(!second.goWasCalled)

      // once the first promise completes, the next
      // function in the sequence should be executed
      first.promise.setValue(1)
      assert(second.goWasCalled)

      // finally, the second promise is fufilled so
      // we can Await on and check the results
      second.promise.setValue(2)
      assert(await(results) == Seq(1, 2))
    }

    test(
      s"object Future ($name) transverseSequentially should return with exception when the first future throws") {
      val first = new TraverseTestSpy()
      val second = new TraverseTestSpy()
      val events = Seq(first.go, second.go)
      val results = Future.traverseSequentially(events)(f => f())

      first.promise.setException(new Exception)

      intercept[Exception] { await(results) }

      // Since first returned an exception, second should
      // never have been called
      assert(!second.goWasCalled)
    }

    test(s"object Future ($name) collect should only return when both futures complete") {
      new CollectHelper {
        p0() = Return(1)
        assert(!f.isDefined)
        p1() = Return(2)
        assert(f.isDefined)
        assert(await(f) == Seq(1, 2))
      }
    }

    test(s"object Future ($name) collect should return with exception if the first future throws") {
      new CollectHelper {
        p0() = Throw(new Exception)
        intercept[Exception] { await(f) }
      }
    }

    test(
      s"object Future ($name) collect should return with exception if the second future throws") {
      new CollectHelper {
        p0() = Return(1)
        assert(!f.isDefined)
        p1() = Throw(new Exception)
        intercept[Exception] { await(f) }
      }
    }

    test(s"object Future ($name) collect should propagate interrupts") {
      new CollectHelper {
        val ps = Seq(p0, p1)
        assert(ps.count(_.handled.isDefined) == 0)
        f.raise(new Exception)
        assert(ps.count(_.handled.isDefined) == 2)
      }
    }

    test(s"object Future ($name) collect should accept maps of futures") {
      val map = Map(
        "1" -> Future.value("1"),
        "2" -> Future.value("2")
      )

      assert(await(Future.collect(map)) == Map("1" -> "1", "2" -> "2"))
    }

    test(s"object Future ($name) collect should work correctly if the given map is empty") {
      val map = Map.empty[String, Future[String]]
      assert(await(Future.collect(map)).isEmpty)
    }

    test(
      s"object Future ($name) collect should return future exception if one of the map values is future exception") {
      val map = Map(
        "1" -> Future.value("1"),
        "2" -> Future.exception(new Exception)
      )

      intercept[Exception] {
        await(Future.collect(map))
      }
    }

    test(s"object Future ($name) collectToTry should only return when both futures complete") {
      new CollectToTryHelper {
        p0() = Return(1)
        assert(!f.isDefined)
        p1() = Return(2)
        assert(f.isDefined)
        assert(await(f) == Seq(Return(1), Return(2)))
      }
    }

    test(
      s"object Future ($name) collectToTry should be undefined if the first future throws and the second is undefined") {
      new CollectToTryHelper {
        p0() = Throw(new Exception)
        assert(!f.isDefined)
      }
    }

    test(
      s"object Future ($name) collectToTry should return both results if the first is defined second future throws") {
      new CollectToTryHelper {
        val ex = new Exception
        p0() = Return(1)
        assert(!f.isDefined)
        p1() = Throw(ex)
        assert(await(f) == Seq(Return(1), Throw(ex)))
      }
    }

    test(s"object Future ($name) collectToTry should propagate interrupts") {
      new CollectToTryHelper {
        val ps = Seq(p0, p1)
        assert(ps.count(_.handled.isDefined) == 0)
        f.raise(new Exception)
        assert(ps.count(_.handled.isDefined) == 2)
      }
    }

    test(s"object Future ($name) should propagate locals, restoring original context") {
      val local = new Local[Int]
      val f = const.value(111)

      var ran = 0
      local() = 1010

      f.ensure {
        assert(local().contains(1010))
        local() = 1212
        f.ensure {
          assert(local().contains(1212))
          local() = 1313
          ran += 1
        }
        assert(local().contains(1212))
        ran += 1
      }

      assert(local().contains(1010))
      assert(ran == 2)
    }

    test(s"object Future ($name) should delay execution") {
      val f = const.value(111)

      var count = 0
      f.onSuccess { _ =>
        assert(count == 0)
        f.ensure {
          assert(count == 1)
          count += 1
        }

        assert(count == 0)
        count += 1
      }

      assert(count == 2)
    }

    test(s"object Future ($name) should are monitored") {
      val inner = const.value(123)
      val exc = new Exception("a raw exception")

      val f = Future.monitored {
        inner.ensure { throw exc }
      }

      assert(f.poll.contains(Throw(exc)))
    }

    test(s"Future ($name) select should select the first [result] to complete") {
      new SelectHelper {
        p0() = Return(1)
        p1() = Return(2)
        assert(await(f) == 1)
      }
    }

    test(s"Future ($name) select should select the first [exception] to complete") {
      new SelectHelper {
        p0() = Throw(new Exception)
        p1() = Return(2)
        intercept[Exception] { await(f) }
      }
    }

    test(s"Future ($name) select should propagate interrupts") {
      new SelectHelper {
        val ps = Seq(p0, p1)
        assert(!ps.exists(_.handled.isDefined))
        f.raise(new Exception)
        assert(ps.forall(_.handled.isDefined))
      }
    }

    def testJoin(
      label: String,
      joiner: ((Future[Int], Future[Int]) => Future[(Int, Int)])
    ): Unit = {
      test(s"Future ($name) join($label) should propagate interrupts") {
        trait JoinHelper {
          val p0 = new HandledPromise[Int]
          val p1 = new HandledPromise[Int]
          val f = joiner(p0, p1)
          assert(!f.isDefined)
        }

        for {
          left <- Seq(Ret(1), Thr(new Exception), Nvr)
          right <- Seq(Ret(2), Thr(new Exception), Nvr)
          leftFirst <- Seq(true, false)
        } {
          new JoinHelper {
            if (leftFirst) {
              satisfy(p0, left)
              satisfy(p1, right)
            } else {
              satisfy(p1, right)
              satisfy(p0, left)
            }
            (left, right) match {
              // Two Throws are special because leftFirst determines the
              // exception (e0 or e1). Otherwise, every other case with a
              // Throw will result in just one exception.
              case (Thr(e0), Thr(e1)) =>
                val actual = intercept[Exception] { await(f) }
                assert(actual == (if (leftFirst) e0 else e1), explain(left, right, leftFirst))
              case (_, Thr(exn)) =>
                val actual = intercept[Exception] { await(f) }
                assert(actual == exn, explain(left, right, leftFirst))
              case (Thr(exn), _) =>
                val actual = intercept[Exception] { await(f) }
                assert(actual == exn, explain(left, right, leftFirst))
              case (Nvr, Ret(_)) | (Ret(_), Nvr) | (Nvr, Nvr) => assert(!f.isDefined)
              case (Ret(a), Ret(b)) =>
                val expected: (Int, Int) = (a, b)
                val result = await(f)
                val isEqual = result == expected
                assert(isEqual, explain(left, right, leftFirst))
            }
          }
        }

        new JoinHelper {
          assert(p0.handled.isEmpty)
          assert(p1.handled.isEmpty)
          val exc = new Exception
          f.raise(exc)
          assert(p0.handled.contains(exc))
          assert(p1.handled.contains(exc))
        }
      }
    }

    testJoin("f join g", _ join _)
    testJoin("Future.join(f, g)", Future.join(_, _))

    def testJavaFuture(methodName: String, fn: Future[Int] => JFuture[_ <: Int]): Unit = {
      test(
        s"Future ($name) $methodName should return the same thing as our Future when initialized") {
        val f = const.value(1)
        val jf = fn(f)
        assert(await(f) == jf.get())
        assert(f.isDefined)
        assert(jf.isDone)
        assert(!jf.isCancelled)
      }

      test(
        s"Future ($name) $methodName should return the same thing as our Future when set later") {
        val f = new Promise[Int]
        val jf = fn(f)
        f.setValue(1)
        assert(await(f) == jf.get())
        assert(f.isDefined)
        assert(jf.isDone)
        assert(!jf.isCancelled)
      }

      test(s"Future ($name) $methodName java future should throw an exception when failed later") {
        val f = new Promise[Int]
        val jf = fn(f)
        val e = new RuntimeException()
        f.setException(e)
        val actual = intercept[ExecutionException] { jf.get() }
        val cause = intercept[RuntimeException] { throw actual.getCause }
        assert(cause == e)
      }

      test(s"Future ($name) $methodName java future should throw an exception when failed early") {
        val f = new Promise[Int]
        val e = new RuntimeException()
        f.setException(e)
        val jf = fn(f)
        val actual = intercept[ExecutionException] { jf.get() }
        val cause = intercept[RuntimeException] { throw actual.getCause }
        assert(cause == e)
      }

      test(s"Future ($name) $methodName should interrupt Future when cancelled") {
        val f = new HandledPromise[Int]
        val jf = fn(f)
        assert(f.handled.isEmpty)
        jf.cancel(true)
        intercept[java.util.concurrent.CancellationException] {
          throw f.handled.get
        }
      }
    }

    testJavaFuture("toJavaFuture", { (f: Future[Int]) => f.toJavaFuture })
    testJavaFuture("toCompletableFuture", { (f: Future[Int]) => f.toCompletableFuture })

    test(s"Future ($name) monitored should catch raw exceptions (direct)") {
      new MonitoredHelper {
        val f = Future.monitored {
          throw exc
          inner
        }
        assert(f.poll.contains(Throw(exc)))
      }
    }

    test(
      s"Future ($name) monitored should catch raw exceptions (indirect), interrupting computation") {
      new MonitoredHelper {
        val inner1 = new Promise[Int]
        var ran = false
        val f: Future[Int] = Future.monitored {
          inner1
            .ensure {
              // Note that these are sequenced so that interrupts
              // will be delivered before inner's handler is cleared.
              ran = true
              try {
                inner.update(Return(1))
              } catch {
                case _: Throwable => fail()
              }
            }
            .ensure {
              throw exc
            }
          inner
        }
        assert(!ran)
        assert(f.poll.isEmpty)
        assert(inner.handled.isEmpty)
        inner1.update(Return(1))
        assert(ran)
        assert(inner.isDefined)
        assert(f.poll.contains(Throw(exc)))

        assert(inner.handled.contains(exc))
      }
    }

    test(s"Future ($name) monitored should link") {
      new MonitoredHelper {
        val f: Future[Int] = Future.monitored { inner }
        assert(inner.handled.isEmpty)
        f.raise(exc)
        assert(inner.handled.contains(exc))
      }
    }

    // we only know this works as expected when running with JDK 8
    if (System.getProperty("java.version").startsWith("1.8"))
      test(s"Future ($name) monitored should not leak the underlying promise after completion") {
        new MonitoredHelper {
          val inner1 = new Promise[String]
          val inner2 = new Promise[String]
          val f: Future[String] = Future.monitored { inner2.ensure(()); inner1 }
          val s: String = "." * 1024 * 10
          val sSize: Long = ObjectSizeCalculator.getObjectSize(s)
          inner1.setValue(s)
          val inner2Size: Long = ObjectSizeCalculator.getObjectSize(inner2)
          assert(inner2Size < sSize)
        }
      }

    test(
      s"Promise ($name) should apply when we're inside of a respond block (without deadlocking)") {
      val f = Future(1)
      var didRun = false
      f.foreach { _ =>
        f mustProduce Return(1)
        didRun = true
      }
      assert(didRun)
    }

    test(s"Promise ($name) should map when it's all chill") {
      val f = Future(1).map { x => x + 1 }
      assert(await(f) == 2)
    }

    test(s"Promise ($name) should map when there's a problem in the passed in function") {
      val e = new Exception
      val f = Future(1).map { x =>
        throw e
        x + 1
      }
      val actual = intercept[Exception] {
        await(f)
      }
      assert(actual == e)
    }

    test(s"Promise ($name) should transform values") {
      val e = new Exception("rdrr")
      const
        .value(1)
        .transform {
          case Return(v) => const.value(v + 1)
          case Throw(_) => const.value(0)
        }
        .mustProduce(Return(2))
    }

    test(s"Promise ($name) should transform exceptions") {
      val e = new Exception("rdrr")

      const
        .exception(e)
        .transform {
          case Return(_) => const.value(1)
          case Throw(_) => const.value(0)
        }
        .mustProduce(Return(0))
    }

    test(s"Promise ($name) should transform exceptions thrown during transformation") {
      val e = new Exception("rdrr")

      const
        .value(1)
        .transform {
          case Return(_) => const.value(throw e)
          case Throw(_) => const.value(0)
        }
        .mustProduce(Throw(e))
    }

    test(s"Promise ($name) should transform non local returns executed during transformation") {
      val e = new Exception("rdrr")

      def ret(): String = {
        val f = const.value(1).transform {
          case Return(_) =>
            val fn = { () => return "OK" }
            fn()
            Future.value(ret())
          case Throw(_) => const.value(0)
        }
        assert(f.poll.isDefined)
        val e = intercept[FutureNonLocalReturnControl] {
          f.poll.get.get()
        }

        val g = e.getCause match {
          case t: NonLocalReturnControl[_] => t.asInstanceOf[NonLocalReturnControl[String]]
          case _ =>
            fail()
        }
        assert(g.value == "OK")
        "bleh"
      }
      ret()
    }

    test(s"Promise ($name) should transform fatal exceptions thrown during transformation") {
      val e = new FatalException()

      val actual = intercept[FatalException] {
        const.value(1).transform {
          case Return(_) => const.value(throw e)
          case Throw(_) => const.value(0)
        }
      }
      assert(actual == e)
    }

    test(s"Promise ($name) transform monitors fatal exceptions") {
      val e = new Exception("rdrr")

      val m = new HandledMonitor()
      val exc = new FatalException()

      assert(m.handled == null)

      val actual = intercept[FatalException] {
        Monitor.using(m) {
          const.value(1).transform { _ => throw exc }
        }
      }

      assert(actual == exc)
      assert(m.handled == exc)
    }

    test(s"Promise ($name) transformedBy flatMap") {
      const
        .value(1)
        .transformedBy(new FutureTransformer[Int, Int] {
          override def flatMap(value: Int): Future[Int] = const.value(value + 1)
          override def rescue(t: Throwable): Future[Int] = const.value(0)
        })
        .mustProduce(Return(2))
    }

    test(s"Promise ($name) transformedBy rescue") {
      val e = new Exception("rdrr")
      const
        .exception(e)
        .transformedBy(new FutureTransformer[Int, Int] {
          override def flatMap(value: Int): Future[Int] = const.value(value + 1)
          override def rescue(t: Throwable): Future[Int] = const.value(0)
        })
        .mustProduce(Return(0))
    }

    test(s"Promise ($name) transformedBy exceptions thrown during transformation") {
      val e = new Exception("rdrr")
      const
        .value(1)
        .transformedBy(new FutureTransformer[Int, Int] {
          override def flatMap(value: Int): Future[Int] = throw e
          override def rescue(t: Throwable): Future[Int] = const.value(0)
        })
        .mustProduce(Throw(e))
    }

    test(s"Promise ($name) transformedBy map") {
      const
        .value(1)
        .transformedBy(new FutureTransformer[Int, Int] {
          override def map(value: Int): Int = value + 1
          override def handle(t: Throwable): Int = 0
        })
        .mustProduce(Return(2))
    }

    test(s"Promise ($name) transformedBy handle") {
      val e = new Exception("rdrr")
      const
        .exception(e)
        .transformedBy(new FutureTransformer[Int, Int] {
          override def map(value: Int): Int = value + 1
          override def handle(t: Throwable): Int = 0
        })
        .mustProduce(Return(0))
    }

    def testSequence(
      which: String,
      seqop: (Future[Unit], () => Future[Unit]) => Future[Unit]
    ): Unit = {
      test(
        s"Promise ($name) $which successes: interruption of the produced future, before the antecedent Future completes, propagates back to the antecedent") {
        val f1, f2 = new HandledPromise[Unit]
        val f3 = seqop(f1, () => f2)
        assert(f1.handled.isEmpty)
        assert(f2.handled.isEmpty)
        f3.raise(new Exception)
        assert(f1.handled.isDefined)
        assert(f2.handled.isEmpty)
        f1() = Return.Unit
        assert(f2.handled.isDefined)
      }

      test(
        s"Promise ($name) $which successes: interruption of the produced future, after the antecedent Future completes, does not propagate back to the antecedent") {
        val f1, f2 = new HandledPromise[Unit]
        val f3 = seqop(f1, () => f2)
        assert(f1.handled.isEmpty)
        assert(f2.handled.isEmpty)
        f1() = Return.Unit
        f3.raise(new Exception)
        assert(f1.handled.isEmpty)
        assert(f2.handled.isDefined)
      }

      test(
        s"Promise ($name) $which successes: interruption of the produced future, forward through chains") {
        val f1, f2 = new Promise[Unit]
        val exc = new Exception
        val f3 = new Promise[Unit]
        var didInterrupt = false
        f3.setInterruptHandler {
          case `exc` => didInterrupt = true
        }
        val f4 = seqop(f1, () => seqop(f2, () => f3))
        f4.raise(exc)
        assert(!didInterrupt)
        f1.setDone()
        assert(!didInterrupt)
        f2.setDone()
        assert(didInterrupt)
      }

      test(s"Promise ($name) $which failures shouldapply") {
        val e = new Exception
        val g = seqop(Future[Unit](throw e), () => Future.Done)
        val actual = intercept[Exception] { await(g) }
        assert(actual == e)
      }

      test(s"Promise ($name) $which failures shouldrespond") {
        val e = new Exception
        val g = seqop(Future[Unit](throw e), () => Future.Done)
        g mustProduce Throw(e)
      }

      test(
        s"Promise ($name) $which failures should when there is an exception in the passed in function") {
        val e = new Exception
        val f = seqop(Future.Done, () => throw e)
        val actual = intercept[Exception] { await(f) }
        assert(actual == e)
      }
    }

    testSequence(
      "flatMap",
      (a, next) => a.flatMap { _ => next() }
    )
    testSequence("before", (a, next) => a.before { next() })

    test(s"Promise ($name) flatMap (values) apply") {
      val f = Future(1).flatMap { x => Future(x + 1) }
      assert(await(f) == 2)
    }

    test(s"Promise ($name) flatMap (values) respond") {
      val f = Future(1).flatMap { x => Future(x + 1) }
      f mustProduce Return(2)
    }

    test(s"Promise ($name) flatten successes") {
      val f = Future(Future(1))
      f.flatten mustProduce Return(1)
    }

    test(s"Promise ($name) flatten shallow failures") {
      val e = new Exception
      val f: Future[Future[Int]] = const.exception(e)
      f.flatten mustProduce Throw(e)
    }

    test(s"Promise ($name) flatten deep failures") {
      val e = new Exception
      val f: Future[Future[Int]] = const.value(const.exception(e))
      f.flatten mustProduce Throw(e)
    }

    test(s"Promise ($name) flatten interruption") {
      val f1 = new HandledPromise[Future[Int]]
      val f2 = new HandledPromise[Int]
      val f = f1.flatten
      assert(f1.handled.isEmpty)
      assert(f2.handled.isEmpty)
      f.raise(new Exception)
      f1.handled match {
        case Some(_) =>
        case None => fail()
      }
      assert(f2.handled.isEmpty)
      f1() = Return(f2)
      f2.handled match {
        case Some(_) =>
        case None => fail()
      }
    }

    test(s"Promise ($name) rescue successses apply") {
      val f = Future(1).rescue { case _ => Future(2) }
      assert(await(f) == 1)
    }

    test(s"Promise ($name) rescue successes: respond") {
      val f = Future(1).rescue { case _ => Future(2) }
      f mustProduce Return(1)
    }

    test(s"Promise ($name) rescue failures: apply") {
      val e = new Exception
      val g = Future[Int](throw e).rescue { case _ => Future(2) }
      assert(await(g) == 2)
    }

    test(s"Promise ($name) rescue failures: respond") {
      val e = new Exception
      val g = Future[Int](throw e).rescue { case _ => Future(2) }
      g mustProduce Return(2)
    }

    test(s"Promise ($name) rescue failures: when the error handler errors") {
      val e = new Exception
      val g = Future[Int](throw e).rescue { case x => throw x; Future(2) }
      val actual = intercept[Exception] { await(g) }
      assert(actual == e)
    }

    test(
      s"Promise ($name) rescue interruption of the produced future before the antecedent Future completes, propagates back to the antecedent") {
      val f1, f2 = new HandledPromise[Int]
      val f = f1.rescue { case _ => f2 }
      assert(f1.handled.isEmpty)
      assert(f2.handled.isEmpty)
      f.raise(new Exception)
      f1.handled match {
        case Some(_) =>
        case None => fail()
      }
      assert(f2.handled.isEmpty)
      f1() = Throw(new Exception)
      f2.handled match {
        case Some(_) =>
        case None => fail()
      }
    }

    test(
      s"Promise ($name) rescue interruption of the produced future after the antecedent Future completes, does not propagate back to the antecedent") {
      val f1, f2 = new HandledPromise[Int]
      val f = f1.rescue { case _ => f2 }
      assert(f1.handled.isEmpty)
      assert(f2.handled.isEmpty)
      f1() = Throw(new Exception)
      f.raise(new Exception)
      assert(f1.handled.isEmpty)
      f2.handled match {
        case Some(_) =>
        case None => fail()
      }
    }

    test(s"Promise ($name) foreach") {
      var wasCalledWith: Option[Int] = None
      val f = Future(1)
      f.foreach { i => wasCalledWith = Some(i) }
      assert(wasCalledWith.contains(1))
    }

    test(s"Promise ($name) respond when the result has arrived") {
      var wasCalledWith: Option[Int] = None
      val f = Future(1)
      f.respond {
        case Return(i) => wasCalledWith = Some(i)
        case Throw(e) => fail(e.toString)
      }
      assert(wasCalledWith.contains(1))
    }

    test(s"Promise ($name) respond when the result has not yet arrived it buffers computations") {
      var wasCalledWith: Option[Int] = None
      val f = new Promise[Int]
      f.foreach { i => wasCalledWith = Some(i) }
      assert(wasCalledWith.isEmpty)
      f() = Return(1)
      assert(wasCalledWith.contains(1))
    }

    test(s"Promise ($name) respond runs callbacks just once and in order (lifo)") {
      var i, j, k, h = 0
      val p = new Promise[Int]

      p.ensure {
          i = i + j + k + h + 1
        }
        .ensure {
          j = i + j + k + h + 1
        }
        .ensure {
          k = i + j + k + h + 1
        }
        .ensure {
          h = i + j + k + h + 1
        }

      assert(i == 0)
      assert(j == 0)
      assert(k == 0)
      assert(h == 0)

      p.setValue(1)
      assert(i == 8)
      assert(j == 4)
      assert(k == 2)
      assert(h == 1)
    }

    test(s"Promise ($name) respond monitor exceptions") {
      val m = new HandledMonitor()
      val exc = new Exception

      assert(m.handled == null)

      Monitor.using(m) {
        const.value(1).ensure { throw exc }
      }

      assert(m.handled == exc)
    }

    test(s"Promise ($name) willEqual") {
      assert(await(const.value(1).willEqual(const.value(1)), 1.second))
    }

    test(s"Promise ($name) Future() handles exceptions") {
      val e = new Exception
      val f = Future[Int] { throw e }
      val actual = intercept[Exception] { await(f) }
      assert(actual == e)
    }

    test(s"Promise ($name) propagate locals") {
      val local = new Local[Int]
      val promise0 = new Promise[Unit]
      val promise1 = new Promise[Unit]

      local() = 1010

      val both = promise0.flatMap { _ =>
        val local0 = local()
        promise1.map { _ =>
          val local1 = local()
          (local0, local1)
        }
      }

      local() = 123
      promise0() = Return.Unit
      local() = 321
      promise1() = Return.Unit

      assert(both.isDefined)
      assert(await(both) == ((Some(1010), Some(1010))))
    }

    test(s"Promise ($name) propagate locals across threads") {
      val local = new Local[Int]
      val promise = new Promise[Option[Int]]

      local() = 123
      val done = promise.map { otherValue => (otherValue, local()) }

      val t = new Thread {
        override def run(): Unit = {
          local() = 1010
          promise() = Return(local())
        }
      }

      t.run()
      t.join()

      assert(done.isDefined)
      assert(await(done) == ((Some(1010), Some(123))))
    }

    test(s"Promise ($name) poll when waiting") {
      new PollHelper {
        assert(p.poll.isEmpty)
      }
    }

    test(s"Promise ($name) poll when succeeding") {
      new PollHelper {
        p.setValue(1)
        assert(p.poll.contains(Return(1)))
      }
    }

    test(s"Promise ($name) poll when failing") {
      new PollHelper {
        val e = new Exception
        p.setException(e)
        assert(p.poll.contains(Throw(e)))
      }
    }

    val uw = (p: Promise[Int], d: Duration, t: Timer) => {
      p.within(d)(t)
    }

    val ub = (p: Promise[Int], d: Duration, t: Timer) => {
      p.by(d.fromNow)(t)
    }

    Seq(("within", uw), ("by", ub)).foreach {
      case (label, use) =>
        test(s"Promise ($name) $label when we run out of time") {
          implicit val timer: Timer = new JavaTimer
          val p = new HandledPromise[Int]
          intercept[TimeoutException] { await(use(p, 50.milliseconds, timer)) }
          timer.stop()
          assert(p.handled.isEmpty)
        }

        test(s"Promise ($name) $label when everything is chill") {
          implicit val timer: Timer = new JavaTimer
          val p = new Promise[Int]
          p.setValue(1)
          assert(await(use(p, 50.milliseconds, timer)) == 1)
          timer.stop()
        }

        test(s"Promise ($name) $label when timeout is forever") {
          // We manage to throw an exception inside
          // the scala compiler if we use MockTimer
          // here. Sigh.
          implicit val timer: Timer = FailingTimer
          val p = new Promise[Int]
          assert(use(p, Duration.Top, timer) == p)
        }

        test(s"Promise ($name) $label when future already satisfied") {
          implicit val timer: Timer = new NullTimer
          val p = new Promise[Int]
          p.setValue(3)
          assert(use(p, 1.minute, timer) == p)
        }

        test(s"Promise ($name) $label interruption") {
          Time.withCurrentTimeFrozen { _ =>
            implicit val timer: Timer = new MockTimer
            val p = new HandledPromise[Int]
            val f = use(p, 50.milliseconds, timer)
            assert(p.handled.isEmpty)
            f.raise(new Exception)
            p.handled match {
              case Some(_) =>
              case None => fail()
            }
          }
        }
    }

    test(s"Promise ($name) raiseWithin when we run out of time") {
      implicit val timer: Timer = new JavaTimer
      val p = new HandledPromise[Int]
      intercept[TimeoutException] {
        await(p.raiseWithin(50.milliseconds))
      }
      timer.stop()
      p.handled match {
        case Some(_) =>
        case None => fail()
      }
    }

    test(s"Promise ($name) raiseWithin when we run out of time, throw our stuff") {
      implicit val timer: Timer = new JavaTimer
      class SkyFallException extends Exception("let the skyfall")
      val skyFall = new SkyFallException
      val p = new HandledPromise[Int]
      intercept[SkyFallException] {
        await(p.raiseWithin(50.milliseconds, skyFall))
      }
      timer.stop()
      p.handled match {
        case Some(_) =>
        case None => fail()
      }
      assert(p.handled.contains(skyFall))
    }

    test(
      s"Promise ($name) raiseWithin when we are within timeout, but inner throws TimeoutException, we don't raise") {
      implicit val timer: Timer = new JavaTimer
      class SkyFallException extends Exception("let the skyfall")
      val skyFall = new SkyFallException
      val p = new HandledPromise[Int]
      intercept[TimeoutException] {
        await(
          p.within(20.milliseconds).raiseWithin(50.milliseconds, skyFall)
        )
      }
      timer.stop()
      assert(p.handled.isEmpty)
    }

    test(s"Promise ($name) raiseWithin when everything is chill") {
      implicit val timer: Timer = new JavaTimer
      val p = new Promise[Int]
      p.setValue(1)
      assert(await(p.raiseWithin(50.milliseconds)) == 1)
      timer.stop()
    }

    test(s"Promise ($name) raiseWithin when timeout is forever") {
      // We manage to throw an exception inside
      // the scala compiler if we use MockTimer
      // here. Sigh.
      implicit val timer: Timer = FailingTimer
      val p = new Promise[Int]
      assert(p.raiseWithin(Duration.Top) == p)
    }

    test(s"Promise ($name) raiseWithin when future already satisfied") {
      implicit val timer: Timer = new NullTimer
      val p = new Promise[Int]
      p.setValue(3)
      assert(p.raiseWithin(1.minute) == p)
    }

    test(s"Promise ($name) raiseWithin interruption") {
      Time.withCurrentTimeFrozen { _ =>
        implicit val timer: Timer = new MockTimer
        val p = new HandledPromise[Int]
        val f = p.raiseWithin(50.milliseconds)
        assert(p.handled.isEmpty)
        f.raise(new Exception)
        p.handled match {
          case Some(_) =>
          case None => fail()
        }
      }
    }

    test(s"Promise ($name) masked should do unconditional interruption") {
      val p = new HandledPromise[Unit]
      val f = p.masked
      f.raise(new Exception())
      assert(p.handled.isEmpty)
    }

    test(s"Promise ($name) masked should do conditional interruption") {
      val p = new HandledPromise[Unit]
      val f1 = p.mask {
        case _: TimeoutException => true
      }
      val f2 = p.mask {
        case _: TimeoutException => true
      }
      f1.raise(new TimeoutException("bang!"))
      assert(p.handled.isEmpty)
      f2.raise(new Exception())
      assert(p.handled.isDefined)
    }

    test(s"Promise ($name) liftToTry success") {
      val p = const(Return(3))
      assert(await(p.liftToTry) == Return(3))
    }

    test(s"Promise ($name) liftToTry failure") {
      val ex = new Exception()
      val p = const(Throw(ex))
      assert(await(p.liftToTry) == Throw(ex))
    }

    test(s"Promise ($name) liftToTry propagates interrupt") {
      val p = new HandledPromise[Unit]
      p.liftToTry.raise(new Exception())
      assert(p.handled.isDefined)
    }

    test(s"Promise ($name) lowerFromTry success") {
      val f = const(Return(Return(3)))
      assert(await(f.lowerFromTry) == 3)
    }

    test(s"Promise ($name) lowerFromTry failure") {
      val ex = new Exception()
      val p = const(Return(Throw(ex)))
      val ex1 = intercept[Exception] { await(p.lowerFromTry) }
      assert(ex == ex1)
    }

    test(s"Promise ($name) lowerFromTry propagates interrupt") {
      val p = new HandledPromise[Try[Unit]]
      p.lowerFromTry.raise(new Exception())
      assert(p.handled.isDefined)
    }

    test(s"FutureTask ($name) should return result") {
      val task = new FutureTask("hello")
      task.run()
      assert(await(task) == "hello")
    }

    test(s"FutureTask ($name) should throw result") {
      val task = new FutureTask[String](throw new IllegalStateException)
      task.run()
      intercept[IllegalStateException] {
        await(task)
      }
    }
  }

  tests(
    "ConstFuture",
    new MkConst {
      def apply[A](r: Try[A]): Future[A] = Future.const(r)
    })
  tests(
    "Promise",
    new MkConst {
      def apply[A](r: Try[A]): Future[A] = new Promise(r)
    })
  tests(
    "Future.fromCompletableFuture",
    new MkConst {
      def apply[A](r: Try[A]): Future[A] =
        Future.fromCompletableFuture(new Promise(r).toCompletableFuture[A])
    }
  )

  test("Future.apply should fail on NLRC") {
    def ok(): String = {
      val f = Future(return "OK")
      val t = intercept[FutureNonLocalReturnControl] {
        f.poll.get.get()
      }
      val nlrc = intercept[NonLocalReturnControl[String]] {
        throw t.getCause
      }
      assert(nlrc.value == "OK")
      "NOK"
    }
    assert(ok() == "NOK")
  }

  test("Future.None should always be defined") {
    assert(Future.None.isDefined)
  }
  test("Future.None should always be definedbut still None") {
    assert(await(Future.None).isEmpty)
  }

  test("Future.True should always be defined") {
    assert(Future.True.isDefined)
  }
  test("Future.True should always be definedbut still True") {
    assert(await(Future.True))
  }

  test("Future.False should always be defined") {
    assert(Future.False.isDefined)
  }
  test("Future.False should always be defined but still False") {
    assert(!await(Future.False))
  }

  test("Future.never must be undefined") {
    assert(!Future.never.isDefined)
    assert(Future.never.poll.isEmpty)
  }

  test("Future.never should always time out") {
    intercept[TimeoutException] { Await.ready(Future.never, 0.milliseconds) }
  }

  test("Future.onFailure with Function1") {
    val nonfatal = Future.exception(new RuntimeException())
    val fatal = Future.exception(new FatalException())
    val counter = new AtomicInteger()
    val f: Throwable => Unit = _ => counter.incrementAndGet()
    nonfatal.onFailure(f)
    assert(counter.get() == 1)
    fatal.onFailure(f)
    assert(counter.get() == 2)
  }

  test("Future.onFailure with PartialFunction") {
    val nonfatal = Future.exception(new RuntimeException())
    val fatal = Future.exception(new FatalException())
    val monitor = new HandledMonitor()
    Monitor.using(monitor) {
      val counter = new AtomicInteger()
      nonfatal.onFailure { case NonFatal(_) => counter.incrementAndGet() }
      assert(counter.get() == 1)
      assert(monitor.handled == null)

      // this will throw a MatchError and propagated to the monitor
      fatal.onFailure { case NonFatal(_) => counter.incrementAndGet() }
      assert(counter.get() == 1)
      assert(monitor.handled.getClass == classOf[MatchError])
    }
  }

  test("Future.sleep should Satisfy after the given amount of time") {
    Time.withCurrentTimeFrozen { tc =>
      implicit val timer: MockTimer = new MockTimer

      val f = Future.sleep(10.seconds)
      assert(!f.isDefined)
      tc.advance(5.seconds)
      timer.tick()
      assert(!f.isDefined)
      tc.advance(5.seconds)
      timer.tick()
      assert(f.isDefined)
      await(f)
    }
  }

  test("Future.sleep should Be interruptible") {
    implicit val timer: MockTimer = new MockTimer

    // sleep and grab the task that's created
    val f = Future.sleep(1.second)(timer)
    val task = timer.tasks(0)
    // then raise a known exception
    val e = new Exception("expected")
    f.raise(e)

    // we were immediately satisfied with the exception and the task was canceled
    f mustProduce Throw(e)
    assert(task.isCancelled)
  }

  test("Future.sleep should Return Future.Done for durations <= 0") {
    implicit val timer: MockTimer = new MockTimer
    assert(Future.sleep(Duration.Zero) eq Future.Done)
    assert(Future.sleep((-10).seconds) eq Future.Done)
    assert(timer.tasks.isEmpty)
  }

  test("Future.sleep should Return Future.never for Duration.Top") {
    implicit val timer: MockTimer = new MockTimer
    assert(Future.sleep(Duration.Top) eq Future.never)
    assert(timer.tasks.isEmpty)
  }

  test("Future.select should return the first result") {
    val genLen = Gen.choose(1, 10)
    forAll(genLen, arbitrary[Boolean]) { (n, fail) =>
      val ps = List.fill(n)(new Promise[Int]())
      assert(ps.map(_.waitqLength).sum == 0)
      val f = Future.select(ps)
      val i = Random.nextInt(ps.length)
      val e = new Exception("sad panda")
      val t = if (fail) Throw(e) else Return(i)
      ps(i).update(t)
      assert(f.isDefined)
      val (ft, fps) = await(f)
      assert(ft == t)
      assert(fps.toSet == (ps.toSet - ps(i)))
    }
  }

  test("Future.select should not accumulate listeners when losing or") {
    val p = new Promise[Unit]
    val q = new Promise[Unit]
    p.or(q)
    assert(p.waitqLength == 1)
    q.setDone()
    assert(p.waitqLength == 0)
  }

  test("Future.select should not accumulate listeners when losing select") {
    val p = new Promise[Unit]
    val q = new Promise[Unit]
    Future.select(Seq(p, q))
    assert(p.waitqLength == 1)
    q.setDone()
    assert(p.waitqLength == 0)
  }

  test("Future.select should not accumulate listeners if not selected") {
    val genLen = Gen.choose(1, 10)
    forAll(genLen, arbitrary[Boolean]) { (n, fail) =>
      val ps = List.fill(n)(new Promise[Int]())
      assert(ps.map(_.waitqLength).sum == 0)
      val f = Future.select(ps)
      assert(ps.map(_.waitqLength).sum == n)
      val i = Random.nextInt(ps.length)
      val e = new Exception("sad panda")
      val t = if (fail) Throw(e) else Return(i)
      f.respond { _ => () }
      assert(ps.map(_.waitqLength).sum == n)
      ps(i).update(t)
      assert(ps.map(_.waitqLength).sum == 0)
    }
  }

  test("Future.select should fail if we attempt to select an empty future sequence") {
    val f = Future.select(Nil)
    assert(f.isDefined)
    val e = new IllegalArgumentException("empty future list")
    val actual = intercept[IllegalArgumentException] { await(f) }
    assert(actual.getMessage == e.getMessage)
  }

  test("Future.select should propagate interrupts") {
    val fs = (0 until 10).map(_ => new HandledPromise[Int])
    Future.select(fs).raise(new Exception)
    assert(fs.forall(_.handled.isDefined))
  }

  // These "Future.selectIndex" tests are almost a carbon copy of the "Future.select" tests, they
  // should evolve in-sync.
  test("Future.selectIndex should return the first result") {
    val genLen = Gen.choose(1, 10)

    forAll(genLen, arbitrary[Boolean]) { (n, fail) =>
      val ps = IndexedSeq.fill(n)(new Promise[Int]())
      assert(ps.map(_.waitqLength).sum == 0)
      val f = Future.selectIndex(ps)
      val i = Random.nextInt(ps.length)
      val e = new Exception("sad panda")
      val t = if (fail) Throw(e) else Return(i)
      ps(i).update(t)
      assert(f.isDefined)
      assert(await(f) == i)
    }
  }

  test("Future.selectIndex should not accumulate listeners when losing select") {
    val p = new Promise[Unit]
    val q = new Promise[Unit]
    Future.selectIndex(IndexedSeq(p, q))
    assert(p.waitqLength == 1)
    q.setDone()
    assert(p.waitqLength == 0)
  }

  test("Future.selectIndex should not accumulate listeners if not selected") {
    val genLen = Gen.choose(1, 10)
    forAll(genLen, arbitrary[Boolean]) { (n, fail) =>
      val ps = IndexedSeq.fill(n)(new Promise[Int]())
      assert(ps.map(_.waitqLength).sum == 0)
      val f = Future.selectIndex(ps)
      assert(ps.map(_.waitqLength).sum == n)
      val i = Random.nextInt(ps.length)
      val e = new Exception("sad panda")
      val t = if (fail) Throw(e) else Return(i)
      f.respond { _ => () }
      assert(ps.map(_.waitqLength).sum == n)
      ps(i).update(t)
      assert(ps.map(_.waitqLength).sum == 0)
    }
  }

  test("Future.selectIndex should fail if we attempt to select an empty future sequence") {
    val f = Future.selectIndex(IndexedSeq.empty)
    assert(f.isDefined)
    val e = new IllegalArgumentException("empty future list")
    val actual = intercept[IllegalArgumentException] { await(f) }
    assert(actual.getMessage == e.getMessage)
  }

  test("Future.selectIndex should propagate interrupts") {
    val fs = IndexedSeq.fill(10)(new HandledPromise[Int]())
    Future.selectIndex(fs).raise(new Exception)
    assert(fs.forall(_.handled.isDefined))
  }

  test("Future.each should iterate until an exception is thrown") {
    val exc = new Exception("done")
    var next: Future[Int] = Future.value(10)
    val done = Future.each(next) {
      case 0 => next = Future.exception(exc)
      case n => next = Future.value(n - 1)
    }

    assert(done.poll.contains(Throw(exc)))
  }

  test("Future.each should evaluate next one time per iteration") {
    var i, j = 0
    def next(): Future[Int] =
      if (i == 10) Future.exception(new Exception)
      else {
        i += 1
        Future.value(i)
      }

    Future.each(next()) { i =>
      j += 1
      assert(i == j)
    }
  }

  test("Future.each should terminate if the body throws an exception") {
    val exc = new Exception("body exception")
    var i = 0
    def next(): Future[Int] = Future.value({ i += 1; i })
    val done = Future.each(next()) {
      case 10 => throw exc
      case _ =>
    }

    assert(done.poll.contains(Throw(exc)))
    assert(i == 10)
  }

  test("Future.each should terminate when 'next' throws") {
    val exc = new Exception
    def next(): Future[Int] = throw exc
    val done = Future.each(next()) { _ => throw exc }

    assert(done.poll.contains(Throw(Future.NextThrewException(exc))))
  }
}
