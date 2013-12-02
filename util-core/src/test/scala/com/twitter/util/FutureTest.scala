package com.twitter.util

import com.twitter.common.objectsize.ObjectSizeCalculator
import com.twitter.conversions.time._
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.runner.RunWith

import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import scala.collection.JavaConverters._
import scala.util.control.ControlThrowable

@RunWith(classOf[JUnitRunner])
class FutureTest extends WordSpec {
  implicit def futureMatcher[A](future: Future[A]) = new {
    def mustProduce(expected: Try[A]) {
      expected match {
        case Throw(ex) => {
          val t = intercept[Throwable] {
            Await.result(future, 1.second)
          }
          assert(t === ex)
        }
        case Return(v) => {
          assert(Await.result(future, 1.second) === v)
        }
      }
    }
  }

  class FatalException extends ControlThrowable

  trait MkConst {
    def apply[A](result: Try[A]): Future[A]
    def value[A](a: A): Future[A] = this(Return(a))
    def exception[A](exc: Throwable): Future[A] = this(Throw(exc))
  }

  class HandledPromise[A] extends Promise[A] {
    @volatile var _handled: Option[Throwable] = None
    def handled: Option[Throwable] = _handled
    setInterruptHandler { case e => _handled = Some(e) }
  }

  def test(name: String, const: MkConst) {
    "object Future (%s)".format(name) when {
      "times" should {
        trait TimesHelper {
          val queue = new ConcurrentLinkedQueue[Promise[Unit]]
          var complete = false
          var failure = false
          var ninterrupt = 0
          val iteration = Future.times(3) {
            val promise = new Promise[Unit]
            promise.setInterruptHandler { case _ => ninterrupt += 1 }
            queue add promise
            promise
          }
          iteration onSuccess { _ =>
            complete = true
          } onFailure { f =>
            failure = true
          }
          assert(complete === false)
          assert(failure === false)
        }

        "when everything succeeds" in {
          new TimesHelper {
            queue.poll().setValue(())
            assert(complete === false)
            assert(failure === false)

            queue.poll().setValue(())
            assert(complete === false)
            assert(failure === false)

            queue.poll().setValue(())
            assert(complete === true)
            assert(failure === false)
          }
        }

        "when some succeed and some fail" in {
          new TimesHelper {
            queue.poll().setValue(())
            assert(complete === false)
            assert(failure === false)

            queue.poll().setException(new Exception(""))
            assert(complete === false)
            assert(failure === true)
          }
        }

        "when interrupted" in {
          new TimesHelper {
            assert(ninterrupt === 0)
            iteration.raise(new Exception)
            for (i <- 1 to 3) {
              assert(ninterrupt === i)
              queue.poll().setValue(())
            }
          }
        }
      }

      "when" in {
        var i = 0

        Await.result {
          Future.when(false) {
            Future { i += 1 }
          }
        }
        assert(i === 0)

        Await.result {
          Future.when(true) {
            Future { i += 1 }
          }
        }
        assert(i === 1)
      }

      "whileDo" should {
        trait WhileDoHelper {
          var i = 0
          val queue = new ConcurrentLinkedQueue[HandledPromise[Unit]]
          var complete = false
          var failure = false
          val iteration = Future.whileDo(i < 3) {
            i += 1
            val promise = new HandledPromise[Unit]
            queue add promise
            promise
          }

          iteration onSuccess { _ =>
            complete = true
          } onFailure { f =>
            failure = true
          }
          assert(complete === false)
          assert(failure === false)
        }

        "when everything succeeds" in {
          new WhileDoHelper {
            queue.poll().setValue(())
            assert(complete === false)
            assert(failure === false)

            queue.poll().setValue(())
            assert(complete === false)
            assert(failure === false)

            queue.poll().setValue(())

            assert(complete === true)
            assert(failure === false)
          }
        }

        "when some succeed and some fail" in {
          new WhileDoHelper {
            queue.poll().setValue(())
            assert(complete === false)
            assert(failure === false)

            queue.poll().setException(new Exception(""))
            assert(complete === false)
            assert(failure === true)
          }
        }

        "when interrupted" in {
          new WhileDoHelper {
            assert((queue.asScala exists (_.handled.isDefined)) === false)
            iteration.raise(new Exception)
            assert((queue.asScala forall ( _.handled.isDefined)) === true)
          }
        }
      }

      "collect" should {
        trait CollectHelper {
          val p0, p1 = new HandledPromise[Int]
          val f = Future.collect(Seq(p0, p1))
          assert(f.isDefined === false)
        }

        "only return when both futures complete" in {
          new CollectHelper {
            p0() = Return(1)
            assert(f.isDefined === false)
            p1() = Return(2)
            assert(f.isDefined === true)
            assert(Await.result(f) === Seq(1, 2))
          }
        }

        "return with exception if the first future throws" in {
          new CollectHelper {
            p0() = Throw(new Exception)
            intercept[Exception] { Await.result(f) }
          }
        }

        "return with exception if the second future throws" in {
          new CollectHelper {
            p0() = Return(1)
            assert(f.isDefined === false)
            p1() = Throw(new Exception)
            intercept[Exception] { Await.result(f) }
          }
        }

        "propagate interrupts" in {
          new CollectHelper {
            val ps = Seq(p0, p1)
            assert((ps.count(_.handled.isDefined)) === 0)
            f.raise(new Exception)
            assert((ps.count(_.handled.isDefined)) === 2)
          }
        }
      }

      "collectToTry" should {

        trait CollectToTryHelper {
          val p0, p1 = new HandledPromise[Int]
          val f = Future.collectToTry(Seq(p0, p1))
          assert(!f.isDefined)
        }

        "only return when both futures complete" in {
          new CollectToTryHelper {
            p0() = Return(1)
            assert(!f.isDefined)
            p1() = Return(2)
            assert(f.isDefined)
            assert(Await.result(f) === Seq(Return(1), Return(2)))
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
            assert(Await.result(f) === Seq(Return(1), Throw(ex)))
          }
        }

        "propagate interrupts" in {
          new CollectToTryHelper {
            val ps = Seq(p0, p1)
            assert(ps.count(_.handled.isDefined) === 0)
            f.raise(new Exception)
            assert(ps.count(_.handled.isDefined) === 2)
          }
        }
      }

      "select" should {
        "return the first result" which {
          def tryBothForIndex(i: Int) = {
            "success (%d)".format(i) in {
              val fs = (0 until 10 map { _ => new Promise[Int] }) toArray
              val f = Future.select(fs)
              assert(f.isDefined === false)
              fs(i)() = Return(1)
              assert(f.isDefined === true)
              assert(Await.result(f) match {
                case (Return(1), rest) =>
                  assert(rest.size === 9)
                  val elems = fs.slice(0, i) ++ fs.slice(i + 1, 10)
                  assert(rest.size === elems.size)
                  assert(rest.diff(elems).isEmpty)
                  true
              })
            }

            "failure (%d)".format(i) in {
              val fs = (0 until 10 map { _ => new Promise[Int] }) toArray
              val f = Future.select(fs)
              assert(f.isDefined === false)
              val e = new Exception("sad panda")
              fs(i)() = Throw(e)
              assert(f.isDefined === true)
              assert(Await.result(f) match {
                case (Throw(e), rest) =>
                  assert(rest.size === 9)
                  val elems = fs.slice(0, i) ++ fs.slice(i + 1, 10)
                  assert(rest.size === elems.size)
                  assert(elems.diff(rest).isEmpty)
                  true
              })
            }
          }

          // Ensure this works for all indices:
          0 until 10 foreach { tryBothForIndex(_) }
        }

        "fail if we attempt to select an empty future sequence" in {
          val f = Future.select(Seq())
          assert(f.isDefined === true)
          val e = new IllegalArgumentException("empty future list!")
          val actual = intercept[IllegalArgumentException] { Await.result(f) }
          assert(actual.getMessage === e.getMessage)
        }

        "propagate interrupts" in {
          val fs = for (_ <- 0 until 10 toArray) yield new HandledPromise[Int]
          Future.select(fs).raise(new Exception)
          assert((fs forall (_.handled.isDefined)) === true)
        }
      }

      "selectIndex" should {
        "return the first result" when {
          def tryBothForIndex(i: Int) = {
            "success (%d)".format(i) in {
              val fs = Seq.fill(10) { new Promise[Int] } toArray
              val fPos = Future.selectIndex(fs)
              assert(!fPos.isDefined)
              fs(i).setValue(1)
              assert(fPos.isDefined)
              assert(Await.result(fPos) === i)
            }

            "failure (%d)".format(i) in {
              val fs = Seq.fill(10) { new Promise[Int] } toArray
              val fPos = Future.selectIndex(fs)
              assert(!fPos.isDefined)
              val e = new Exception("sad panda")
              fs(i).setException(e)
              assert(fPos.isDefined)
              assert(Await.result(fPos) === i)
            }
          }

          // Ensure this works for all indices:
          0 until 10 foreach { tryBothForIndex(_) }
        }

        "fail if we attempt to select an empty future sequence" in {
          val f = Future.selectIndex(IndexedSeq())
          assert(f.isDefined)
          val e = intercept[IllegalArgumentException] {
            Await.result(f)
          }
          val expected = "empty future list"
          assert(e.getMessage === expected)
        }

        "propagate interrupts" in {
          val fs = Array.fill(10) { new HandledPromise[Int] }
          Future.selectIndex(fs).raise(new Exception)
          assert(fs forall (_.handled.isDefined))
        }
      }

      "propagate locals, restoring original context" in {
        val local = new Local[Int]
        val f = const.value(111)

        var ran = 0
        local() = 1010

        f ensure {
          assert(local() === Some(1010))
          local() = 1212
          f ensure {
            assert(local() === Some(1212))
            local() = 1313
            ran += 1
          }
          assert(local() === Some(1212))
          ran += 1
        }

        assert(local() === Some(1010))
        assert(ran === 2)
      }

      "delay execution" in {
        val f = const.value(111)

        var count = 0
        f onSuccess { _ =>
          assert(count === 0)
          f ensure {
            assert(count === 1)
            count += 1
          }

          assert(count === 0)
          count += 1
        }

        assert(count === 2)
      }

      "are monitored" in {
        val inner = const.value(123)
        val exc = new Exception("a raw exception")

        val f = Future.monitored {
          inner ensure { throw exc }
        }

        assert(f.poll === Some(Throw(exc)))
      }
    }

    "Future (%s)".format(name) should {
      "select" which {
        trait SelectHelper {
          var nhandled = 0
          val p0, p1 = new HandledPromise[Int]
          val f = p0 select p1
          assert(f.isDefined === false)
        }

        "select the first [result] to complete" in {
          new SelectHelper {
            p0() = Return(1)
            p1() = Return(2)
            assert(Await.result(f) === 1)
          }
        }

        "select the first [exception] to complete" in {
          new SelectHelper {
            p0() = Throw(new Exception)
            p1() = Return(2)
            intercept[Exception] { Await.result(f) }
          }
        }

        "propagate interrupts" in {
          new SelectHelper {
            val ps = Seq(p0, p1)
            assert((ps exists (_.handled.isDefined)) === false)
            f.raise(new Exception)
            assert((ps forall (_.handled.isDefined)) === true)
          }
        }
      }

      def testJoin(label: String, joiner: ((Future[Int], Future[Int]) => Future[(Int, Int)])) {
        "join(%s)".format(label) should {
          trait JoinHelper {
            val p0 = new HandledPromise[Int]
            val p1 = new HandledPromise[Int]
            val f = joiner(p0, p1)
            assert(f.isDefined === false)
          }

          "only return when both futures complete" in {
            new JoinHelper {
              p0() = Return(1)
              assert(f.isDefined === false)
              p1() = Return(2)
              assert(Await.result(f) === (1, 2))
            }
          }

          "return with exception if the first future throws" in {
            new JoinHelper {
              p0() = Throw(new Exception)
              intercept[Exception] { Await.result(f) }
            }
          }

          "return with exception if the second future throws" in {
            new JoinHelper {
              p0() = Return(1)
              assert(f.isDefined === false)
              p1() = Throw(new Exception)
              intercept[Exception] { Await.result(f) }
            }
          }

          "propagate interrupts" in {
            new JoinHelper {
              assert(p0.handled === None)
              assert(p1.handled === None)
              val exc = new Exception
              f.raise(exc)
              assert(p0.handled === Some(exc))
              assert(p1.handled === Some(exc))
            }
          }
        }
      }

      testJoin("f join g", _ join _)
      testJoin("Future.join(f, g)", Future.join(_, _))

      "toJavaFuture" should {
        "return the same thing as our Future when initialized" which {
          val f = const.value(1)
          val jf = f.toJavaFuture
          assert(Await.result(f) === jf.get())
          "must both be done" in {
            assert(f.isDefined === true)
            assert(jf.isDone === true)
            assert(jf.isCancelled === false)
          }
        }

        "return the same thing as our Future when set later" which {
          val f = new Promise[Int]
          val jf = f.toJavaFuture
          f.setValue(1)
          assert(Await.result(f) === jf.get())
          "must both be done" in {
            assert(f.isDefined === true)
            assert(jf.isDone === true)
            assert(jf.isCancelled === false)
          }
        }

        "java future should throw an exception" in {
          val f = new Promise[Int]
          val jf = f.toJavaFuture
          val e = new RuntimeException()
          f.setException(e)
          val actual = intercept[RuntimeException] { jf.get() }
          assert(actual === e)
        }

        "interrupt Future when cancelled" in {
          val f = new HandledPromise[Int]
          val jf = f.toJavaFuture
          assert(f.handled === None)
          jf.cancel(true)
          assert(f.handled match {
            case Some(e: java.util.concurrent.CancellationException) => true
            case _ => false
          })
        }
      }

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
            assert(f.poll === Some(Throw(exc)))
          }
        }

        "catch raw exceptions (indirect), interrupting computation" in {
          new MonitoredHelper {
            val inner1 = new Promise[Int]
            var ran = false
            val f = Future.monitored {
              inner1 ensure {
                throw exc
              } ensure {
                // Note that these are sequenced so that interrupts
                // will be delivered before inner's handler is cleared.
                ran = true
                try {
                  inner.update(Return(1))
                } catch {
                  case e: Throwable => assert(true === false)
                }
              }
              inner
            }
            assert(ran === false)
            assert(f.poll === None)
            assert(inner.handled === None)
            inner1.update(Return(1))
            assert(ran === true)
            assert(inner.isDefined === true)
            assert(f.poll === Some(Throw(exc)))

            assert(inner.handled === Some(exc))
          }
        }

        "link" in {
          new MonitoredHelper {
            val f = Future.monitored { inner }
            assert(inner.handled === None)
            f.raise(exc)
            assert(inner.handled === Some(exc))
          }
        }

        "doesn't leak the underlying promise after completion" in {
          new MonitoredHelper {
            val inner1 = new Promise[String]
            val inner2 = new Promise[String]
            val f = Future.monitored { inner2.ensure(()); inner1 }
            val s = "."*1024
            val sSize = ObjectSizeCalculator.getObjectSize(s)
            inner1.setValue("."*1024)
            val inner2Size = ObjectSizeCalculator.getObjectSize(inner2)
            assert(inner2Size < sSize)
          }
        }
      }

      "get(deprecated)" in {
        val e = new Exception
        val v = 123
        assert(Future.exception[Int](e).get(0.seconds) === Throw(e))
        assert(Future.value(v).get(0.seconds) === Return(v))

        // Including fatal ones:
        val e2 = new java.lang.IllegalAccessError
        assert(Future.exception[Int](e2).get(0.seconds)  === Throw(e2))

        implicit val timer = new JavaTimer
        val p = new Promise[Int]
        val r = p.get(50.milliseconds)
        intercept[TimeoutException]{ r() }
        timer.stop()
      }
    }

    "Promise (%s)".format(name) should {
      "apply" which {
        "when we're inside of a respond block (without deadlocking)" in {
          val f = Future(1)
          var didRun = false
          f foreach { _ =>
            f mustProduce Return(1)
            didRun = true
          }
          assert(didRun === true)
        }
      }

      "map" which {
        "when it's all chill" in {
          val f = Future(1) map { x => x + 1 }
          assert(Await.result(f) === 2)
        }

        "when there's a problem in the passed in function" in {
          val e = new Exception
          val f = Future(1) map { x =>
            throw e
            x + 1
          }
          val actual = intercept[Exception] {
            Await.result(f)
          }
          assert(actual === e)
        }
      }

      "transform" should {
        val e = new Exception("rdrr")

        "values" in {
          const.value(1).transform {
            case Return(v) => const.value(v + 1)
            case Throw(t) => const.value(0)
          } mustProduce(Return(2))
        }

        "exceptions" in {
          const.exception(e).transform {
            case Return(_) => const.value(1)
            case Throw(t) => const.value(0)
          } mustProduce(Return(0))
        }

        "exceptions thrown during transformation" in {
          const.value(1).transform {
            case Return(v) => const.value(throw e)
            case Throw(t) => const.value(0)
          } mustProduce(Throw(e))
        }

        "fatal exceptions thrown during transformation" in {
          val e = new FatalException()

          val actual = intercept[FatalException] {
            const.value(1).transform {
              case Return(v) => const.value(throw e)
              case Throw(t) => const.value(0)
            }
          }
          assert(actual === e)
        }
      }

      "transformedBy" should {
        val e = new Exception("rdrr")

        "flatMap" in {
          const.value(1).transformedBy(new FutureTransformer[Int, Int] {
            override def flatMap(value: Int) = const.value(value + 1)
            override def rescue(t: Throwable) = const.value(0)
          }) mustProduce(Return(2))
        }

        "rescue" in {
          const.exception(e).transformedBy(new FutureTransformer[Int, Int] {
            override def flatMap(value: Int) = const.value(value + 1)
            override def rescue(t: Throwable) = const.value(0)
          }) mustProduce(Return(0))
        }

        "exceptions thrown during transformation" in {
          const.value(1).transformedBy(new FutureTransformer[Int, Int] {
            override def flatMap(value: Int) = throw e
            override def rescue(t: Throwable) = const.value(0)
          }) mustProduce(Throw(e))
        }

        "map" in {
          const.value(1).transformedBy(new FutureTransformer[Int, Int] {
            override def map(value: Int) = value + 1
            override def handle(t: Throwable) = 0
          }) mustProduce(Return(2))
        }

        "handle" in {
          const.exception(e).transformedBy(new FutureTransformer[Int, Int] {
            override def map(value: Int) = value + 1
            override def handle(t: Throwable) = 0
          }) mustProduce(Return(0))
        }
      }

      "flatMap" when {
        "successes" should {
          val f = Future(1) flatMap { x => Future(x + 1) }

          "apply" which {
            assert(Await.result(f) === 2)
          }

          "respond" which {
            f mustProduce Return(2)
          }

          "interruption of the produced future" which {
            "before the antecedent Future completes, propagates back to the antecedent" in {
              val f1, f2 = new HandledPromise[Int]
              val f = f1 flatMap { _ => f2 }
              assert(f1.handled === None)
              assert(f2.handled === None)
              f.raise(new Exception)
              assert(f1.handled.isDefined)
              f1() = Return(2)
              assert(f2.handled.isDefined)
            }

            "after the antecedent Future completes, does not propagate back to the antecedent" in {
              val f1, f2 = new HandledPromise[Int]
              val f = f1 flatMap { _ => f2 }
              assert(f1.handled === None)
              assert(f2.handled === None)
              f1() = Return(2)
              f.raise(new Exception)
              assert(f1.handled === None)
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
              val f = f1 flatMap { _ => f2 flatMap { _ => f3 } }
              f.raise(exc)
              assert(didInterrupt === false)
              f1.setValue(())
              assert(didInterrupt === false)
              f2.setValue(())
              assert(didInterrupt === true)
            }
          }
        }

        "failures" should {
          val e = new Exception
          val g = Future[Int](throw e) flatMap { x => Future(x + 1) }

          "apply" in {
            val actual = intercept[Exception] { Await.result(g) }
            assert(actual === e)
          }

          "respond" in {
            g mustProduce Throw(e)
          }

          "when there is an exception in the passed in function" in {
            val e = new Exception
            val f = Future(1).flatMap[Int] { x =>
              throw e
            }
            val actual = intercept[Exception] { Await.result(f) }
            assert(actual === e)
          }
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
          val f: Future[Future[Int]] = const.exception(e)
          f.flatten mustProduce Throw(e)
        }

        "interruption" in {
          val f1 = new HandledPromise[Future[Int]]
          val f2 = new HandledPromise[Int]
          val f = f1.flatten
          assert(f1.handled === None)
          assert(f2.handled === None)
          f.raise(new Exception)
          f1.handled match {
            case Some(_) =>
            case None => assert(false === true)
          }
          assert(f2.handled === None)
          f1() = Return(f2)
          f2.handled  match {
            case Some(_) =>
            case None => assert(false === true)
          }
        }
      }

      "rescue" should {
        val e = new Exception

        "successes" which {
          val f = Future(1) rescue { case e => Future(2) }

          "apply" in {
            assert(Await.result(f) === 1)
          }

          "respond" in {
            f mustProduce Return(1)
          }
        }

        "failures" which {
          val g = Future[Int](throw e) rescue { case e => Future(2) }

          "apply" in {
            assert(Await.result(g) === 2)
          }

          "respond" in {
            g mustProduce Return(2)
          }

          "when the error handler errors" in {
            val g = Future[Int](throw e) rescue { case e => throw e; Future(2) }
            val actual = intercept[Exception] { Await.result(g) }
            assert(actual === e)
          }
        }

        "interruption of the produced future" which {
          "before the antecedent Future completes, propagates back to the antecedent" in {
            val f1, f2 = new HandledPromise[Int]
            val f = f1 rescue { case _ => f2 }
            assert(f1.handled === None)
            assert(f2.handled === None)
            f.raise(new Exception)
            f1.handled  match {
            case Some(_) =>
            case None => assert(false === true)
          }
            assert(f2.handled === None)
            f1() = Throw(new Exception)
            f2.handled  match {
            case Some(_) =>
            case None => assert(false === true)
          }
          }

          "after the antecedent Future completes, does not propagate back to the antecedent" in {
            val f1, f2 = new HandledPromise[Int]
            val f = f1 rescue { case _ => f2 }
            assert(f1.handled === None)
            assert(f2.handled === None)
            f1() = Throw(new Exception)
            f.raise(new Exception)
            assert(f1.handled === None)
            f2.handled  match {
            case Some(_) =>
            case None => assert(false === true)
          }
          }
        }
      }

      "foreach" in {
        var wasCalledWith: Option[Int] = None
        val f = Future(1)
        f foreach { i =>
          wasCalledWith = Some(i)
        }
        assert(wasCalledWith === Some(1))
      }

      "respond" should {
        "when the result has arrived" in {
          var wasCalledWith: Option[Int] = None
          val f = Future(1)
          f respond {
            case Return(i) => wasCalledWith = Some(i)
            case Throw(e) => fail(e.toString)
          }
          assert(wasCalledWith === Some(1))
        }

        "when the result has not yet arrived it buffers computations" in {
          var wasCalledWith: Option[Int] = None
          val f = new Promise[Int]
          f foreach { i =>
            wasCalledWith = Some(i)
          }
          assert(wasCalledWith === None)
          f()= Return(1)
          assert(wasCalledWith === Some(1))
        }

        "runs callbacks just once and in order" in {
          var i,j,k,h = 0
          val p = new Promise[Int]
          p ensure {
            i = i+j+k+h+1
          } ensure {
            j = i+j+k+h+1
          } ensure {
            k = i+j+k+h+1
          } ensure {
            h = i+j+k+h+1
          }

          assert(i === 0)
          assert(j === 0)
          assert(k === 0)
          assert(h === 0)

          p.setValue(1)
          assert(i === 1)
          assert(j === 2)
          assert(k === 4)
          assert(h === 8)
        }

        "monitor exceptions" in {
          val m = new Monitor {
            var handled = null: Throwable
            def handle(exc: Throwable) = {
              handled = exc
              true
            }
          }
          val exc = new Exception
          val p = new Promise[Int]

          m {
            p ensure { throw exc }
          }

          assert(m.handled === null)
          p.update(Return(1))
          assert(m.handled === exc)
        }
      }

      "willEqual" in {
        assert(Await.result((const.value(1) willEqual(const.value(1))), 1.second) === true)
      }

      "Future() handles exceptions" in {
        val e = new Exception
        val f = Future[Int] { throw e }
        val actual = intercept[Exception] { Await.result(f) }
        assert(actual === e)
      }

      "propagate locals" in {
        val local = new Local[Int]
        val promise0 = new Promise[Unit]
        val promise1 = new Promise[Unit]

        local() = 1010

        val both = promise0 flatMap { _ =>
          val local0 = local()
          promise1 map { _ =>
            val local1 = local()
            (local0, local1)
          }
        }

        local() = 123
        promise0() = Return.Unit
        local() = 321
        promise1() = Return.Unit

        assert(both.isDefined === true)
        assert(Await.result(both) === (Some(1010), Some(1010)))
      }

      "propagate locals across threads" in {
        val local = new Local[Int]
        val promise = new Promise[Option[Int]]

        local() = 123
        val done = promise map { otherValue => (otherValue, local()) }

        val t = new Thread {
          override def run() {
            local() = 1010
            promise() = Return(local())
          }
        }

        t.run()
        t.join()

        assert(done.isDefined === true)
        assert(Await.result(done) === (Some(1010), Some(123)))
      }

      "poll" should {
        trait PollHelper {
          val p = new Promise[Int]
        }
        "when waiting" in {
          new PollHelper {
            assert(p.poll === None)
          }
        }

        "when succeeding" in {
          new PollHelper {
            p.setValue(1)
            assert(p.poll === Some(Return(1)))
          }
        }

        "when failing" in {
          new PollHelper {
            val e = new Exception
            p.setException(e)
            assert(p.poll === Some(Throw(e)))
          }
        }
      }

      "within" should {
        "when we run out of time" in {
          implicit val timer = new JavaTimer
          val p = new HandledPromise[Int]
          intercept[TimeoutException] { Await.result(p.within(50.milliseconds)) }
          timer.stop()
          assert(p.handled === None)
        }

        "when everything is chill" in {
          implicit val timer = new JavaTimer
          val p = new Promise[Int]
          p.setValue(1)
          assert(Await.result(p.within(50.milliseconds)) === 1)
          timer.stop()
        }

        "when timeout is forever" in {
          // We manage to throw an exception inside
          // the scala compiler if we use MockTimer
          // here. Sigh.
          implicit val timer = new Timer {
            def schedule(when: Time)(f: => Unit): TimerTask =
              throw new Exception("schedule called")
            def schedule(when: Time, period: Duration)(f: => Unit): TimerTask =
              throw new Exception("schedule called")
            def stop() = ()
          }
          val p = new Promise[Int]
          assert(p.within(Duration.Top) === p)
        }

        "interruption" in Time.withCurrentTimeFrozen { tc =>
          implicit val timer = new MockTimer
          val p = new HandledPromise[Int]
          val f = p.within(50.milliseconds)
          assert(p.handled === None)
          f.raise(new Exception)
          p.handled  match {
            case Some(_) =>
            case None => assert(false === true)
          }
        }
      }

      "raiseWithin" should {
        "when we run out of time" in {
          implicit val timer = new JavaTimer
          val p = new HandledPromise[Int]
          intercept[TimeoutException] {
            Await.result(p.raiseWithin(50.milliseconds))
          }
          timer.stop()
          p.handled  match {
            case Some(_) =>
            case None => assert(false === true)
          }
        }

        "when we run out of time, throw our stuff" in {
          implicit val timer = new JavaTimer
          class SkyFallException extends Exception("let the skyfall")
          val skyFall = new SkyFallException
          val p = new HandledPromise[Int]
          intercept[SkyFallException] {
            Await.result(p.raiseWithin(50.milliseconds, skyFall))
          }
          timer.stop()
          p.handled  match {
            case Some(_) =>
            case None => assert(false === true)
          }
          assert(p.handled === Some(skyFall))
        }

        "when we are within timeout, but inner throws TimeoutException, we don't raise" in {
          implicit val timer = new JavaTimer
          class SkyFallException extends Exception("let the skyfall")
          val skyFall = new SkyFallException
          val p = new HandledPromise[Int]
          intercept[TimeoutException] {
            Await.result(
              p.within(20.milliseconds).raiseWithin(50.milliseconds, skyFall)
            )
          }
          timer.stop()
          assert(p.handled === None)
        }

        "when everything is chill" in {
          implicit val timer = new JavaTimer
          val p = new Promise[Int]
          p.setValue(1)
          assert(Await.result(p.raiseWithin(50.milliseconds)) === 1)
          timer.stop()
        }

        "when timeout is forever" in {
          // We manage to throw an exception inside
          // the scala compiler if we use MockTimer
          // here. Sigh.
          implicit val timer = new Timer {
            def schedule(when: Time)(f: => Unit): TimerTask =
              throw new Exception("schedule called")
            def schedule(when: Time, period: Duration)(f: => Unit): TimerTask =
              throw new Exception("schedule called")
            def stop() = ()
          }
          val p = new Promise[Int]
          assert(p.raiseWithin(Duration.Top) === p)
        }

        "interruption" in Time.withCurrentTimeFrozen { tc =>
          implicit val timer = new MockTimer
          val p = new HandledPromise[Int]
          val f = p.raiseWithin(50.milliseconds)
          assert(p.handled === None)
          f.raise(new Exception)
          p.handled  match {
            case Some(_) =>
            case None => assert(false === true)
          }
        }
      }

      "masked" should {
        "unconditional interruption" in {
          val p = new HandledPromise[Unit]
          val f = p.masked
          f.raise(new Exception())
          assert(p.handled === None)
        }

        "conditional interruption" in {
          val p = new HandledPromise[Unit]
          val f1 = p.mask {
            case _: TimeoutException => true
          }
          val f2 = p.mask {
            case _: TimeoutException => true
          }
          f1.raise(new TimeoutException("bang!"))
          assert(p.handled === None)
          f2.raise(new Exception())
          assert(p.handled.isDefined)
        }
      }

      "liftToTry" should {
        "success" in {
          val p = Future.value(3)
          assert(Await.result(p.liftToTry) === Return(3))
        }

        "failure" in {
          val ex = new Exception()
          val p = Future.exception(ex)
          assert(Await.result(p.liftToTry) === Throw(ex))
        }

        "propagates interrupt" in {
          val p = new HandledPromise[Unit]
          p.liftToTry.raise(new Exception())
          assert(p.handled.isDefined)
        }
      }
    }

    "FutureTask (%s)".format(name) should {
      "return result" in {
        val task = new FutureTask("hello")
        task.run()
        assert(Await.result(task) === "hello")
      }

      "throw result" in {
        val task = new FutureTask[String](throw new IllegalStateException)
        task.run()
        intercept[IllegalStateException] {
          Await.result(task)
        }
      }
    }
  }

  test("ConstFuture", new MkConst { def apply[A](r: Try[A]) = Future.const(r) })
  test("Promise", new MkConst { def apply[A](r: Try[A]) = new Promise(r) })

  "Future.None" should {
    "always be defined" in {
      assert(Future.None.isDefined === true)
    }
    "but still None" in {
      assert(Await.result(Future.None) === None)
    }
  }

  "Future.True" should {
    "always be defined" in {
      assert(Future.True.isDefined === true)
    }
    "but still True" in {
      assert(Await.result(Future.True) === true)
    }
  }

  "Future.False" should {
    "always be defined" in {
      assert(Future.False.isDefined === true)
    }
    "but still False" in {
      assert(Await.result(Future.False) === false)
    }
  }

  "Future.never" should {
    "must be undefined" in {
      assert(Future.never.isDefined === false)
      assert(Future.never.poll === None)
    }

    "always time out" in {
      intercept[TimeoutException] { Await.ready(Future.never, 0.milliseconds) }
    }
  }

  // TODO(John Sirois):  Kill this mvn test hack when pants takes over.
  "Java" should {
    "work" in {
      val test = new FutureCompilationTest()
      test.testFutureCastMap()
      test.testFutureCastFlatMap()
      test.testTransformedBy()
      assert(true === true)
    }
  }
}
