package com.twitter.util

import com.twitter.conversions.DurationOps._
import java.util.concurrent.{ConcurrentLinkedQueue, ExecutionException, Future => JFuture}
import java.util.concurrent.atomic.AtomicInteger
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, times, verify, when}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalacheck.{Arbitrary, Gen}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scala.jdk.CollectionConverters._
import scala.runtime.NonLocalReturnControl
import scala.util.Random
import scala.util.control.ControlThrowable
import scala.util.control.NonFatal
import org.scalatest.wordspec.AnyWordSpec

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

class FutureTest extends AnyWordSpec with MockitoSugar with ScalaCheckDrivenPropertyChecks {
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

  class FatalException extends ControlThrowable

  trait MkConst {
    def apply[A](result: Try[A]): Future[A]
    def value[A](a: A): Future[A] = this(Return(a))
    def exception[A](exc: Throwable): Future[A] = this(Throw(exc))
  }

  def test(name: String, const: MkConst): Unit = {
    s"object Future ($name)" when {
      "times" should {
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

        "when everything succeeds" in {
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

        "when some succeed and some fail" in {
          new TimesHelper {
            queue.poll().setDone()
            assert(!complete)
            assert(!failure)

            queue.poll().setException(new Exception(""))
            assert(!complete)
            assert(failure)
          }
        }

        "when interrupted" in {
          new TimesHelper {
            assert(ninterrupt == 0)
            iteration.raise(new Exception)
            for (i <- 1 to 3) {
              assert(ninterrupt == i)
              queue.poll().setDone()
            }
          }
        }
      }

      "when" in {
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

      "whileDo" should {
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

        "when everything succeeds" in {
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

        "when some succeed and some fail" in {
          new WhileDoHelper {
            queue.poll().setDone()
            assert(!complete)
            assert(!failure)

            queue.poll().setException(new Exception(""))
            assert(!complete)
            assert(failure)
          }
        }

        "when interrupted" in {
          new WhileDoHelper {
            assert(!queue.asScala.exists(_.handled.isDefined))
            iteration.raise(new Exception)
            assert(queue.asScala.forall(_.handled.isDefined))
          }
        }
      }

      "proxyTo" should {
        "reject satisfied promises" in {
          val str = "um um excuse me um"
          val p1 = new Promise[String]()
          p1.update(Return(str))

          val p2 = new Promise[String]()
          val ex = intercept[IllegalStateException] { p2.proxyTo(p1) }
          assert(ex.getMessage.contains(str))
        }

        "proxies success" in {
          val p1 = new Promise[Int]()
          val p2 = new Promise[Int]()
          p2.proxyTo(p1)
          p2.update(Return(5))
          assert(5 == await(p1))
          assert(5 == await(p2))
        }

        "proxies failure" in {
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
      }

      "batched" should {
        implicit val timer: MockTimer = new MockTimer
        val result = Seq(4, 5, 6)

        "execute after threshold is reached" in {
          val m = mock[Any => Future[Seq[Int]]]
          val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
          when(f.compose(any())).thenReturn(m)

          val batcher = Future.batched(3)(f)

          when(m.apply(Iterable(1, 2, 3))).thenReturn(Future.value(result))
          batcher(1)
          verify(m, never()).apply(any[Seq[Int]])
          batcher(2)
          verify(m, never()).apply(any[Seq[Int]])
          batcher(3)
          verify(m).apply(Iterable(1, 2, 3))
        }

        "execute after bufSizeFraction threshold is reached" in {
          val m = mock[Any => Future[Seq[Int]]]
          val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
          when(f.compose(any())).thenReturn(m)

          val batcher = Future.batched(3, sizePercentile = 0.67f)(f)
          when(m.apply(Iterable(1, 2, 3))).thenReturn(Future.value(result))
          batcher(1)
          verify(m, never()).apply(any[Seq[Int]])
          batcher(2)
          verify(m).apply(Iterable(1, 2))
        }

        "treat bufSizeFraction return value < 0.0f as 1" in {
          val m = mock[Any => Future[Seq[Int]]]
          val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
          when(f.compose(any())).thenReturn(m)

          val batcher = Future.batched(3, sizePercentile = 0.4f)(f)

          when(m.apply(Iterable(1, 2, 3))).thenReturn(Future.value(result))
          batcher(1)
          verify(m).apply(Iterable(1))
        }

        "treat bufSizeFraction return value > 1.0f should return maxSizeThreshold" in {
          val m = mock[Any => Future[Seq[Int]]]
          val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
          when(f.compose(any())).thenReturn(m)

          val batcher = Future.batched(3, sizePercentile = 1.3f)(f)

          when(m.apply(Iterable(1, 2, 3))).thenReturn(Future.value(result))
          batcher(1)
          verify(m, never()).apply(any[Seq[Int]])
          batcher(2)
          verify(m, never()).apply(any[Seq[Int]])
          batcher(3)
          verify(m).apply(Iterable(1, 2, 3))
        }

        "execute after time threshold" in {
          val m = mock[Any => Future[Seq[Int]]]
          val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
          when(f.compose(any())).thenReturn(m)

          val batcher = Future.batched(3, 3.seconds)(f)

          Time.withCurrentTimeFrozen { control =>
            when(m(Iterable(1))).thenReturn(Future.value(Seq(4)))
            batcher(1)
            verify(m, never()).apply(any[Seq[Int]])

            control.advance(1.second)
            timer.tick()
            verify(m, never()).apply(any[Seq[Int]])

            control.advance(1.second)
            timer.tick()
            verify(m, never()).apply(any[Seq[Int]])

            control.advance(1.second)
            timer.tick()
            verify(m).apply(Iterable(1))
          }
        }

        "only execute once if both are reached" in {
          val m = mock[Any => Future[Seq[Int]]]
          val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
          when(f.compose(any())).thenReturn(m)

          val batcher = Future.batched(3)(f)

          Time.withCurrentTimeFrozen { control =>
            when(m(Iterable(1, 2, 3))).thenReturn(Future.value(result))
            batcher(1)
            batcher(2)
            batcher(3)
            control.advance(10.seconds)
            timer.tick()

            verify(m).apply(Iterable(1, 2, 3))
          }
        }

        "execute when flushBatch is called" in {
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

        "only execute for remaining items when flushBatch is called after size threshold is reached" in {
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

        "only execute once when time threshold is reached after flushBatch is called" in {
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
            timer.tick()

            verify(m, times(1)).apply(Iterable(1, 2, 3))
          }
        }

        "only execute once when time threshold is reached before flushBatch is called" in {
          val m = mock[Any => Future[Seq[Int]]]
          val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
          when(f.compose(any())).thenReturn(m)

          val batcher = Future.batched(4, 3.seconds)(f)

          Time.withCurrentTimeFrozen { control =>
            batcher(1)
            batcher(2)
            batcher(3)
            control.advance(10.seconds)
            timer.tick()
            batcher.flushBatch()

            verify(m, times(1)).apply(Iterable(1, 2, 3))
          }
        }

        "propagates results" in {
          val m = mock[Any => Future[Seq[Int]]]
          val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
          when(f.compose(any())).thenReturn(m)

          val batcher = Future.batched(3)(f)

          Time.withCurrentTimeFrozen { _ =>
            when(m(Iterable(1, 2, 3))).thenReturn(Future.value(result))
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

        "not block other batches" in {
          val m = mock[Any => Future[Seq[Int]]]
          val f = mock[scala.collection.Seq[Int] => Future[Seq[Int]]]
          when(f.compose(any())).thenReturn(m)

          val batcher = Future.batched(3)(f)

          Time.withCurrentTimeFrozen { _ =>
            val blocker = new Promise[Unit]
            val thread = new Thread {
              override def run(): Unit = {
                when(m(result)).thenReturn(Future.value(Seq(7, 8, 9)))
                batcher(4)
                batcher(5)
                batcher(6)
                verify(m).apply(result)
                blocker.setValue(())
              }
            }

            when(m(Seq(1, 2, 3))).thenAnswer {
              new Answer[Future[Seq[Int]]] {
                def answer(invocation: InvocationOnMock): Future[Seq[Int]] = {
                  thread.start()
                  await(blocker)
                  Future.value(result)
                }
              }
            }

            batcher(1)
            batcher(2)
            batcher(3)
            verify(m).apply(Iterable(1, 2, 3))
          }
        }

        "swallow exceptions" in {
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

      "interruptible" should {
        "properly ignore the underlying future on interruption" in {
          val p = Promise[Unit]()
          val i = p.interruptible()
          val e = new Exception
          i.raise(e)
          p.setDone()
          assert(p.poll.contains(Return(())))
          assert(i.poll.contains(Throw(e)))
        }

        "respect the underlying future" in {
          val p = Promise[Unit]()
          val i = p.interruptible()
          p.setDone()
          assert(p.poll.contains(Return(())))
          assert(i.poll.contains(Return(())))
        }

        "do nothing for const" in {
          val f = const.value(())
          val i = f.interruptible()
          i.raise(new Exception())
          assert(f.poll.contains(Return(())))
          assert(i.poll.contains(Return(())))
        }
      }

      "traverseSequentially" should {
        class TraverseTestSpy() {
          var goWasCalled = false
          var promise: Promise[Int] = Promise[Int]()
          val go: () => Promise[Int] = () => {
            goWasCalled = true
            promise
          }
        }

        "execute futures in order" in {
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

        "return with exception when the first future throws" in {
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
      }

      "collect" should {
        trait CollectHelper {
          val p0, p1 = new HandledPromise[Int]
          val f: Future[Seq[Int]] = Future.collect(Seq(p0, p1))
          assert(!f.isDefined)
        }

        "only return when both futures complete" in {
          new CollectHelper {
            p0() = Return(1)
            assert(!f.isDefined)
            p1() = Return(2)
            assert(f.isDefined)
            assert(await(f) == Seq(1, 2))
          }
        }

        "return with exception if the first future throws" in {
          new CollectHelper {
            p0() = Throw(new Exception)
            intercept[Exception] { await(f) }
          }
        }

        "return with exception if the second future throws" in {
          new CollectHelper {
            p0() = Return(1)
            assert(!f.isDefined)
            p1() = Throw(new Exception)
            intercept[Exception] { await(f) }
          }
        }

        "propagate interrupts" in {
          new CollectHelper {
            val ps = Seq(p0, p1)
            assert(ps.count(_.handled.isDefined) == 0)
            f.raise(new Exception)
            assert(ps.count(_.handled.isDefined) == 2)
          }
        }

        "accept maps of futures" in {
          val map = Map(
            "1" -> Future.value("1"),
            "2" -> Future.value("2")
          )

          assert(await(Future.collect(map)) == Map("1" -> "1", "2" -> "2"))
        }

        "work correctly if the given map is empty" in {
          val map = Map.empty[String, Future[String]]
          assert(await(Future.collect(map)).isEmpty)
        }

        "return future exception if one of the map values is future exception" in {
          val map = Map(
            "1" -> Future.value("1"),
            "2" -> Future.exception(new Exception)
          )

          intercept[Exception] {
            await(Future.collect(map))
          }
        }
      }

      "collectToTry" should {

        trait CollectToTryHelper {
          val p0, p1 = new HandledPromise[Int]
          val f: Future[Seq[Try[Int]]] = Future.collectToTry(Seq(p0, p1))
          assert(!f.isDefined)
        }

        "only return when both futures complete" in {
          new CollectToTryHelper {
            p0() = Return(1)
            assert(!f.isDefined)
            p1() = Return(2)
            assert(f.isDefined)
            assert(await(f) == Seq(Return(1), Return(2)))
          }
        }

        "be undefined if the first future throws and the second is undefined" in {
          new CollectToTryHelper {
            p0() = Throw(new Exception)
            assert(!f.isDefined)
          }
        }

        "return both results if the first is defined second future throws" in {
          new CollectToTryHelper {
            val ex = new Exception
            p0() = Return(1)
            assert(!f.isDefined)
            p1() = Throw(ex)
            assert(await(f) == Seq(Return(1), Throw(ex)))
          }
        }

        "propagate interrupts" in {
          new CollectToTryHelper {
            val ps = Seq(p0, p1)
            assert(ps.count(_.handled.isDefined) == 0)
            f.raise(new Exception)
            assert(ps.count(_.handled.isDefined) == 2)
          }
        }
      }

      "propagate locals, restoring original context" in {
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

      "delay execution" in {
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

      "are monitored" in {
        val inner = const.value(123)
        val exc = new Exception("a raw exception")

        val f = Future.monitored {
          inner.ensure { throw exc }
        }

        assert(f.poll.contains(Throw(exc)))
      }
    }

    s"Future ($name)" should {
      "select" which {
        trait SelectHelper {
          var nhandled = 0
          val p0, p1 = new HandledPromise[Int]
          val f: Future[Int] = p0.select(p1)
          assert(!f.isDefined)
        }

        "select the first [result] to complete" in {
          new SelectHelper {
            p0() = Return(1)
            p1() = Return(2)
            assert(await(f) == 1)
          }
        }

        "select the first [exception] to complete" in {
          new SelectHelper {
            p0() = Throw(new Exception)
            p1() = Return(2)
            intercept[Exception] { await(f) }
          }
        }

        "propagate interrupts" in {
          new SelectHelper {
            val ps = Seq(p0, p1)
            assert(!ps.exists(_.handled.isDefined))
            f.raise(new Exception)
            assert(ps.forall(_.handled.isDefined))
          }
        }
      }

      def testJoin(
        label: String,
        joiner: ((Future[Int], Future[Int]) => Future[(Int, Int)])
      ): Unit = {
        s"join($label)" should {
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

          "propagate interrupts" in {
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
      }

      testJoin("f join g", _ join _)
      testJoin("Future.join(f, g)", Future.join(_, _))

      def testJavaFuture(methodName: String, fn: Future[Int] => JFuture[_ <: Int]): Unit = {
        methodName should {
          "return the same thing as our Future when initialized" which {
            val f = const.value(1)
            val jf = fn(f)
            assert(await(f) == jf.get())
            "must both be done" in {
              assert(f.isDefined)
              assert(jf.isDone)
              assert(!jf.isCancelled)
            }
          }

          "return the same thing as our Future when set later" which {
            val f = new Promise[Int]
            val jf = fn(f)
            f.setValue(1)
            assert(await(f) == jf.get())
            "must both be done" in {
              assert(f.isDefined)
              assert(jf.isDone)
              assert(!jf.isCancelled)
            }
          }

          "java future should throw an exception" in {
            val f = new Promise[Int]
            val jf = fn(f)
            val e = new RuntimeException()
            f.setException(e)
            val actual = intercept[ExecutionException] { jf.get() }
            val cause = intercept[RuntimeException] { throw actual.getCause }
            assert(cause == e)
          }

          "interrupt Future when cancelled" in {
            val f = new HandledPromise[Int]
            val jf = fn(f)
            assert(f.handled.isEmpty)
            jf.cancel(true)
            intercept[java.util.concurrent.CancellationException] {
              throw f.handled.get
            }
          }
        }
      }

      testJavaFuture("toJavaFuture", { (f: Future[Int]) => f.toJavaFuture })
      testJavaFuture("toCompletableFuture", { (f: Future[Int]) => f.toCompletableFuture })

      "monitored" should {
        trait MonitoredHelper {
          val inner = new HandledPromise[Int]
          val exc = new Exception("some exception")
        }

        "catch raw exceptions (direct)" in {
          new MonitoredHelper {
            val f = Future.monitored {
              throw exc
              inner
            }
            assert(f.poll.contains(Throw(exc)))
          }
        }

        "catch raw exceptions (indirect), interrupting computation" in {
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

        "link" in {
          new MonitoredHelper {
            val f: Future[Int] = Future.monitored { inner }
            assert(inner.handled.isEmpty)
            f.raise(exc)
            assert(inner.handled.contains(exc))
          }
        }

        // we only know this works as expected when running with JDK 8
        if (System.getProperty("java.version").startsWith("1.8"))
          "doesn't leak the underlying promise after completion" in {
            new MonitoredHelper {
              val inner1 = new Promise[String]
              val inner2 = new Promise[String]
              val f: Future[String] = Future.monitored { inner2.ensure(()); inner1 }
              val s: String = "." * 1024
              val sSize: Long = ObjectSizeCalculator.getObjectSize(s)
              inner1.setValue(s)
              val inner2Size: Long = ObjectSizeCalculator.getObjectSize(inner2)
              assert(inner2Size < sSize)
            }
          }
      }
    }

    s"Promise ($name)" should {
      "apply" which {
        "when we're inside of a respond block (without deadlocking)" in {
          val f = Future(1)
          var didRun = false
          f.foreach { _ =>
            f mustProduce Return(1)
            didRun = true
          }
          assert(didRun)
        }
      }

      "map" which {
        "when it's all chill" in {
          val f = Future(1).map { x => x + 1 }
          assert(await(f) == 2)
        }

        "when there's a problem in the passed in function" in {
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
      }

      "transform" should {
        val e = new Exception("rdrr")

        "values" in {
          const
            .value(1)
            .transform {
              case Return(v) => const.value(v + 1)
              case Throw(_) => const.value(0)
            }
            .mustProduce(Return(2))
        }

        "exceptions" in {
          const
            .exception(e)
            .transform {
              case Return(_) => const.value(1)
              case Throw(_) => const.value(0)
            }
            .mustProduce(Return(0))
        }

        "exceptions thrown during transformation" in {
          const
            .value(1)
            .transform {
              case Return(_) => const.value(throw e)
              case Throw(_) => const.value(0)
            }
            .mustProduce(Throw(e))
        }

        "non local returns executed during transformation" in {
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

        "fatal exceptions thrown during transformation" in {
          val e = new FatalException()

          val actual = intercept[FatalException] {
            const.value(1).transform {
              case Return(_) => const.value(throw e)
              case Throw(_) => const.value(0)
            }
          }
          assert(actual == e)
        }

        "monitors fatal exceptions" in {
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
      }

      "transformedBy" should {
        val e = new Exception("rdrr")

        "flatMap" in {
          const
            .value(1)
            .transformedBy(new FutureTransformer[Int, Int] {
              override def flatMap(value: Int): Future[Int] = const.value(value + 1)
              override def rescue(t: Throwable): Future[Int] = const.value(0)
            })
            .mustProduce(Return(2))
        }

        "rescue" in {
          const
            .exception(e)
            .transformedBy(new FutureTransformer[Int, Int] {
              override def flatMap(value: Int): Future[Int] = const.value(value + 1)
              override def rescue(t: Throwable): Future[Int] = const.value(0)
            })
            .mustProduce(Return(0))
        }

        "exceptions thrown during transformation" in {
          const
            .value(1)
            .transformedBy(new FutureTransformer[Int, Int] {
              override def flatMap(value: Int): Future[Int] = throw e
              override def rescue(t: Throwable): Future[Int] = const.value(0)
            })
            .mustProduce(Throw(e))
        }

        "map" in {
          const
            .value(1)
            .transformedBy(new FutureTransformer[Int, Int] {
              override def map(value: Int): Int = value + 1
              override def handle(t: Throwable): Int = 0
            })
            .mustProduce(Return(2))
        }

        "handle" in {
          const
            .exception(e)
            .transformedBy(new FutureTransformer[Int, Int] {
              override def map(value: Int): Int = value + 1
              override def handle(t: Throwable): Int = 0
            })
            .mustProduce(Return(0))
        }
      }

      def testSequence(
        which: String,
        seqop: (Future[Unit], () => Future[Unit]) => Future[Unit]
      ): Unit = {
        which when {
          "successes" should {
            "interruption of the produced future" which {
              "before the antecedent Future completes, propagates back to the antecedent" in {
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

              "after the antecedent Future completes, does not propagate back to the antecedent" in {
                val f1, f2 = new HandledPromise[Unit]
                val f3 = seqop(f1, () => f2)
                assert(f1.handled.isEmpty)
                assert(f2.handled.isEmpty)
                f1() = Return.Unit
                f3.raise(new Exception)
                assert(f1.handled.isEmpty)
                assert(f2.handled.isDefined)
              }

              "forward through chains" in {
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
            }
          }

          "failures" should {
            val e = new Exception
            val g = seqop(Future[Unit](throw e), () => Future.Done)

            "apply" in {
              val actual = intercept[Exception] { await(g) }
              assert(actual == e)
            }

            "respond" in {
              g mustProduce Throw(e)
            }

            "when there is an exception in the passed in function" in {
              val e = new Exception
              val f = seqop(Future.Done, () => throw e)
              val actual = intercept[Exception] { await(f) }
              assert(actual == e)
            }
          }
        }
      }

      testSequence(
        "flatMap",
        (a, next) => a.flatMap { _ => next() }
      )
      testSequence("before", (a, next) => a.before { next() })

      "flatMap (values)" should {
        val f = Future(1).flatMap { x => Future(x + 1) }

        "apply" in {
          assert(await(f) == 2)
        }

        "respond" in {
          f mustProduce Return(2)
        }
      }

      "flatten" should {
        "successes" in {
          val f = Future(Future(1))
          f.flatten mustProduce Return(1)
        }

        "shallow failures" in {
          val e = new Exception
          val f: Future[Future[Int]] = const.exception(e)
          f.flatten mustProduce Throw(e)
        }

        "deep failures" in {
          val e = new Exception
          val f: Future[Future[Int]] = const.value(const.exception(e))
          f.flatten mustProduce Throw(e)
        }

        "interruption" in {
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
      }

      "rescue" should {
        val e = new Exception

        "successes" which {
          val f = Future(1).rescue { case _ => Future(2) }

          "apply" in {
            assert(await(f) == 1)
          }

          "respond" in {
            f mustProduce Return(1)
          }
        }

        "failures" which {
          val g = Future[Int](throw e).rescue { case _ => Future(2) }

          "apply" in {
            assert(await(g) == 2)
          }

          "respond" in {
            g mustProduce Return(2)
          }

          "when the error handler errors" in {
            val g = Future[Int](throw e).rescue { case x => throw x; Future(2) }
            val actual = intercept[Exception] { await(g) }
            assert(actual == e)
          }
        }

        "interruption of the produced future" which {
          "before the antecedent Future completes, propagates back to the antecedent" in {
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

          "after the antecedent Future completes, does not propagate back to the antecedent" in {
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
        }
      }

      "foreach" in {
        var wasCalledWith: Option[Int] = None
        val f = Future(1)
        f.foreach { i => wasCalledWith = Some(i) }
        assert(wasCalledWith.contains(1))
      }

      "respond" should {
        "when the result has arrived" in {
          var wasCalledWith: Option[Int] = None
          val f = Future(1)
          f.respond {
            case Return(i) => wasCalledWith = Some(i)
            case Throw(e) => fail(e.toString)
          }
          assert(wasCalledWith.contains(1))
        }

        "when the result has not yet arrived it buffers computations" in {
          var wasCalledWith: Option[Int] = None
          val f = new Promise[Int]
          f.foreach { i => wasCalledWith = Some(i) }
          assert(wasCalledWith.isEmpty)
          f() = Return(1)
          assert(wasCalledWith.contains(1))
        }

        "runs callbacks just once and in order (lifo)" in {
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

        "monitor exceptions" in {
          val m = new HandledMonitor()
          val exc = new Exception

          assert(m.handled == null)

          Monitor.using(m) {
            const.value(1).ensure { throw exc }
          }

          assert(m.handled == exc)
        }
      }

      "willEqual" in {
        assert(await(const.value(1).willEqual(const.value(1)), 1.second))
      }

      "Future() handles exceptions" in {
        val e = new Exception
        val f = Future[Int] { throw e }
        val actual = intercept[Exception] { await(f) }
        assert(actual == e)
      }

      "propagate locals" in {
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

      "propagate locals across threads" in {
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

      "poll" should {
        trait PollHelper {
          val p = new Promise[Int]
        }
        "when waiting" in {
          new PollHelper {
            assert(p.poll.isEmpty)
          }
        }

        "when succeeding" in {
          new PollHelper {
            p.setValue(1)
            assert(p.poll.contains(Return(1)))
          }
        }

        "when failing" in {
          new PollHelper {
            val e = new Exception
            p.setException(e)
            assert(p.poll.contains(Throw(e)))
          }
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
          label should {
            "when we run out of time" in {
              implicit val timer: Timer = new JavaTimer
              val p = new HandledPromise[Int]
              intercept[TimeoutException] { await(use(p, 50.milliseconds, timer)) }
              timer.stop()
              assert(p.handled.isEmpty)
            }

            "when everything is chill" in {
              implicit val timer: Timer = new JavaTimer
              val p = new Promise[Int]
              p.setValue(1)
              assert(await(use(p, 50.milliseconds, timer)) == 1)
              timer.stop()
            }

            "when timeout is forever" in {
              // We manage to throw an exception inside
              // the scala compiler if we use MockTimer
              // here. Sigh.
              implicit val timer: Timer = FailingTimer
              val p = new Promise[Int]
              assert(use(p, Duration.Top, timer) == p)
            }

            "when future already satisfied" in {
              implicit val timer: Timer = new NullTimer
              val p = new Promise[Int]
              p.setValue(3)
              assert(use(p, 1.minute, timer) == p)
            }

            "interruption" in Time.withCurrentTimeFrozen { _ =>
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

      "raiseWithin" should {
        "when we run out of time" in {
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

        "when we run out of time, throw our stuff" in {
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

        "when we are within timeout, but inner throws TimeoutException, we don't raise" in {
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

        "when everything is chill" in {
          implicit val timer: Timer = new JavaTimer
          val p = new Promise[Int]
          p.setValue(1)
          assert(await(p.raiseWithin(50.milliseconds)) == 1)
          timer.stop()
        }

        "when timeout is forever" in {
          // We manage to throw an exception inside
          // the scala compiler if we use MockTimer
          // here. Sigh.
          implicit val timer: Timer = FailingTimer
          val p = new Promise[Int]
          assert(p.raiseWithin(Duration.Top) == p)
        }

        "when future already satisfied" in {
          implicit val timer: Timer = new NullTimer
          val p = new Promise[Int]
          p.setValue(3)
          assert(p.raiseWithin(1.minute) == p)
        }

        "interruption" in Time.withCurrentTimeFrozen { _ =>
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

      "masked" should {
        "do unconditional interruption" in {
          val p = new HandledPromise[Unit]
          val f = p.masked
          f.raise(new Exception())
          assert(p.handled.isEmpty)
        }

        "do conditional interruption" in {
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
      }

      "liftToTry" should {
        "success" in {
          val p = const(Return(3))
          assert(await(p.liftToTry) == Return(3))
        }

        "failure" in {
          val ex = new Exception()
          val p = const(Throw(ex))
          assert(await(p.liftToTry) == Throw(ex))
        }

        "propagates interrupt" in {
          val p = new HandledPromise[Unit]
          p.liftToTry.raise(new Exception())
          assert(p.handled.isDefined)
        }
      }

      "lowerFromTry" should {
        "success" in {
          val f = const(Return(Return(3)))
          assert(await(f.lowerFromTry) == 3)
        }

        "failure" in {
          val ex = new Exception()
          val p = const(Return(Throw(ex)))
          val ex1 = intercept[Exception] { await(p.lowerFromTry) }
          assert(ex == ex1)
        }

        "propagates interrupt" in {
          val p = new HandledPromise[Try[Unit]]
          p.lowerFromTry.raise(new Exception())
          assert(p.handled.isDefined)
        }
      }
    }

    s"FutureTask ($name)" should {
      "return result" in {
        val task = new FutureTask("hello")
        task.run()
        assert(await(task) == "hello")
      }

      "throw result" in {
        val task = new FutureTask[String](throw new IllegalStateException)
        task.run()
        intercept[IllegalStateException] {
          await(task)
        }
      }
    }
  }

  test(
    "ConstFuture",
    new MkConst {
      def apply[A](r: Try[A]): Future[A] = Future.const(r)
    })
  test(
    "Promise",
    new MkConst {
      def apply[A](r: Try[A]): Future[A] = new Promise(r)
    })

  "Future.apply" should {
    "fail on NLRC" in {
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
  }

  "Future.None" should {
    "always be defined" in {
      assert(Future.None.isDefined)
    }
    "but still None" in {
      assert(await(Future.None).isEmpty)
    }
  }

  "Future.True" should {
    "always be defined" in {
      assert(Future.True.isDefined)
    }
    "but still True" in {
      assert(await(Future.True))
    }
  }

  "Future.False" should {
    "always be defined" in {
      assert(Future.False.isDefined)
    }
    "but still False" in {
      assert(!await(Future.False))
    }
  }

  "Future.never" should {
    "must be undefined" in {
      assert(!Future.never.isDefined)
      assert(Future.never.poll.isEmpty)
    }

    "always time out" in {
      intercept[TimeoutException] { Await.ready(Future.never, 0.milliseconds) }
    }
  }

  "Future.onFailure" should {
    val nonfatal = Future.exception(new RuntimeException())
    val fatal = Future.exception(new FatalException())

    "with Function1" in {
      val counter = new AtomicInteger()
      val f: Throwable => Unit = _ => counter.incrementAndGet()
      nonfatal.onFailure(f)
      assert(counter.get() == 1)
      fatal.onFailure(f)
      assert(counter.get() == 2)
    }

    "with PartialFunction" in {
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
  }

  "Future.sleep" should {
    "Satisfy after the given amount of time" in Time.withCurrentTimeFrozen { tc =>
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

    "Be interruptible" in {
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

    "Return Future.Done for durations <= 0" in {
      implicit val timer: MockTimer = new MockTimer
      assert(Future.sleep(Duration.Zero) eq Future.Done)
      assert(Future.sleep((-10).seconds) eq Future.Done)
      assert(timer.tasks.isEmpty)
    }

    "Return Future.never for Duration.Top" in {
      implicit val timer: MockTimer = new MockTimer
      assert(Future.sleep(Duration.Top) eq Future.never)
      assert(timer.tasks.isEmpty)
    }
  }

  "Future.select" should {
    import Arbitrary.arbitrary
    val genLen = Gen.choose(1, 10)

    "return the first result" in {
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

    "not accumulate listeners when losing or" in {
      val p = new Promise[Unit]
      val q = new Promise[Unit]
      p.or(q)
      assert(p.waitqLength == 1)
      q.setDone()
      assert(p.waitqLength == 0)
    }

    "not accumulate listeners when losing select" in {
      val p = new Promise[Unit]
      val q = new Promise[Unit]
      Future.select(Seq(p, q))
      assert(p.waitqLength == 1)
      q.setDone()
      assert(p.waitqLength == 0)
    }

    "not accumulate listeners if not selected" in {
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

    "fail if we attempt to select an empty future sequence" in {
      val f = Future.select(Nil)
      assert(f.isDefined)
      val e = new IllegalArgumentException("empty future list")
      val actual = intercept[IllegalArgumentException] { await(f) }
      assert(actual.getMessage == e.getMessage)
    }

    "propagate interrupts" in {
      val fs = (0 until 10).map(_ => new HandledPromise[Int])
      Future.select(fs).raise(new Exception)
      assert(fs.forall(_.handled.isDefined))
    }
  }

  // These tests are almost a carbon copy of the "Future.select" tests, they
  // should evolve in-sync.
  "Future.selectIndex" should {
    import Arbitrary.arbitrary
    val genLen = Gen.choose(1, 10)

    "return the first result" in {
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

    "not accumulate listeners when losing select" in {
      val p = new Promise[Unit]
      val q = new Promise[Unit]
      Future.selectIndex(IndexedSeq(p, q))
      assert(p.waitqLength == 1)
      q.setDone()
      assert(p.waitqLength == 0)
    }

    "not accumulate listeners if not selected" in {
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

    "fail if we attempt to select an empty future sequence" in {
      val f = Future.selectIndex(IndexedSeq.empty)
      assert(f.isDefined)
      val e = new IllegalArgumentException("empty future list")
      val actual = intercept[IllegalArgumentException] { await(f) }
      assert(actual.getMessage == e.getMessage)
    }

    "propagate interrupts" in {
      val fs = IndexedSeq.fill(10)(new HandledPromise[Int]())
      Future.selectIndex(fs).raise(new Exception)
      assert(fs.forall(_.handled.isDefined))
    }
  }

  "Future.each" should {
    "iterate until an exception is thrown" in {
      val exc = new Exception("done")
      var next: Future[Int] = Future.value(10)
      val done = Future.each(next) {
        case 0 => next = Future.exception(exc)
        case n => next = Future.value(n - 1)
      }

      assert(done.poll.contains(Throw(exc)))
    }

    "evaluate next one time per iteration" in {
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

    "terminate if the body throws an exception" in {
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

    "terminate when 'next' throws" in {
      val exc = new Exception
      def next(): Future[Int] = throw exc
      val done = Future.each(next()) { _ => throw exc }

      assert(done.poll.contains(Throw(Future.NextThrewException(exc))))
    }
  }
}
