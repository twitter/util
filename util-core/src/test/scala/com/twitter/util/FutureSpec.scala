package com.twitter.util

import org.specs.Specification
import org.specs.mock.Mockito
import com.twitter.conversions.time._
import java.util.concurrent.ConcurrentLinkedQueue

class FutureSpec extends Specification with Mockito {
  implicit def futureMatcher[A](future: Future[A]) = new {
    def mustProduce(expected: Try[A]) {
      future.get(1.second) mustEqual expected
    }
  }

  trait MkConst {
    def apply[A](result: Try[A]): Future[A]
    def value[A](a: A): Future[A] = this(Return(a))
    def exception[A](exc: Throwable): Future[A] = this(Throw(exc))
  }

  def test(name: String, const: MkConst) {
    "object Future (%s)".format(name) should {
      "times" in {
        val queue = new ConcurrentLinkedQueue[Promise[Unit]]
        var complete = false
        var failure = false
        val iteration = Future.times(3) {
          val promise = new Promise[Unit]
          queue add promise
          promise
        }
        iteration onSuccess { _ =>
          complete = true
        } onFailure { f =>
          failure = true
        }
        complete mustBe false
        failure mustBe false

        "when everything succeeds" in {
          queue.poll().setValue(())
          complete mustBe false
          failure mustBe false

          queue.poll().setValue(())
          complete mustBe false
          failure mustBe false

          queue.poll().setValue(())
          complete mustBe true
          failure mustBe false
        }

        "when some succeed and some fail" in {
          queue.poll().setValue(())
          complete mustBe false
          failure mustBe false

          queue.poll().setException(new Exception(""))
          complete mustBe false
          failure mustBe true
        }

        "when cancelled" in {
          iteration.cancel()
          0 until 3 foreach { _ =>
            val f = queue.poll()
            f.isCancelled must beTrue
            f.setValue(())
          }
        }
      }

      "whileDo" in {
        var i = 0
        val queue = new ConcurrentLinkedQueue[Promise[Unit]]
        var complete = false
        var failure = false
        val iteration = Future.whileDo(i < 3) {
          i += 1
          val promise = new Promise[Unit]
          queue add promise
          promise
        }

        iteration onSuccess { _ =>
          complete = true
        } onFailure { f =>
          failure = true
        }
        complete mustBe false
        failure mustBe false

        "when everything succeeds" in {
          queue.poll().setValue(())
          complete mustBe false
          failure mustBe false

          queue.poll().setValue(())
          complete mustBe false
          failure mustBe false

          queue.poll().setValue(())

          complete mustBe true
          failure mustBe false
        }

        "when some succeed and some fail" in {
          queue.poll().setValue(())
          complete mustBe false
          failure mustBe false

          queue.poll().setException(new Exception(""))
          complete mustBe false
          failure mustBe true
        }

        "when cancelled" in {
          iteration.cancel()
          0 until 3 foreach { i =>
            val f = queue.poll()
            f.isCancelled must beTrue
            f.setValue(())
          }
        }
      }

      "collect" in {
        val p0 = new Promise[Int]
        val p1 = new Promise[Int]
        val f = Future.collect(Seq(p0, p1))
        f.isDefined must beFalse

        "only return when both futures complete" in {
          p0() = Return(1)
          f.isDefined must beFalse
          p1() = Return(2)
          f.isDefined must beTrue
          f() must be_==(Seq(1, 2))
        }

        "return with exception if the first future throws" in {
          p0() = Throw(new Exception)
          f() must throwA[Exception]
        }

        "return with exception if the second future throws" in {
          p0() = Return(1)
          f.isDefined must beFalse
          p1() = Throw(new Exception)
          f() must throwA[Exception]
        }

        "propagate cancellation" in {
          p0.isCancelled must beFalse
          p1.isCancelled must beFalse
          f.cancel()
          p0.isCancelled must beTrue
          p1.isCancelled must beTrue
        }
      }

      "select" in {
        "return the first result" in {
          def tryBothForIndex(i: Int) = {
            "success (%d)".format(i) in {
              val fs = (0 until 10 map { _ => new Promise[Int] }) toArray
              val f = Future.select(fs)
              f.isDefined must beFalse
              fs(i)() = Return(1)
              f.isDefined must beTrue
              f() must beLike {
                case (Return(1), rest) =>
                  rest must haveSize(9)
                  val elems = fs.slice(0, i) ++ fs.slice(i + 1, 10)
                  rest must haveTheSameElementsAs(elems)
                  true
              }
            }

            "failure (%d)".format(i) in {
              val fs = (0 until 10 map { _ => new Promise[Int] }) toArray
              val f = Future.select(fs)
              f.isDefined must beFalse
              val e = new Exception("sad panda")
              fs(i)() = Throw(e)
              f.isDefined must beTrue
              f() must beLike {
                case (Throw(e), rest) =>
                  rest must haveSize(9)
                  val elems = fs.slice(0, i) ++ fs.slice(i + 1, 10)
                  rest must haveTheSameElementsAs(elems)
                  true
              }
            }
          }

          // Ensure this works for all indices:
          0 until 10 foreach { tryBothForIndex(_) }
        }

        "fail if we attempt to select an empty future sequence" in {
          val f = Future.select(Seq())
          f.isDefined must beTrue
          f() must throwA(new IllegalArgumentException("empty future list!"))
        }

        "propagate cancellation" in {
          val fs = (0 until 10 map { _ => new Promise[Int] }) toArray;
          Future.select(fs).cancel()
          fs foreach { f =>
            f.isCancelled must beTrue
          }
        }
      }

      "propagate locals, restoring original context" in {
        val local = new Local[Int]
        val f = const.value(111)

        var ran = 0
        local() = 1010

        f ensure {
          local() must be_==(Some(1010))
          local() = 1212
          f ensure {
            local() must be_==(Some(1212))
            local() = 1313
            ran += 1
          }
          local() must be_==(Some(1212))
          ran += 1
        }

        local() must be_==(Some(1010))
        ran must be_==(2)
      }

      "delay execution" in {
        val f = const.value(111)

        var count = 0
        f onSuccess { _ =>
          count must be_==(0)
          f ensure {
            count must be_==(1)
            count += 1
          }

          count must be_==(0)
          count += 1
        }

        count must be_==(2)
      }

      "are monitored" in {
        val inner = const.value(123)
        val exc = new Exception("a raw exception")

        val f = Future.monitored {
          inner ensure { throw exc }
        }

        f.poll must beSome(Throw(exc))
      }
    }

    "Future (%s)".format(name) should {
      "select" in {
        val p0 = new Promise[Int]
        val p1 = new Promise[Int]
        val f = p0 select p1
        f.isDefined must beFalse

        "select the first [result] to complete" in {
          p0() = Return(1)
          p1() = Return(2)
          f() must be_==(1)
        }

        "select the first [exception] to complete" in {
          p0() = Throw(new Exception)
          p1() = Return(2)
          f() must throwA[Exception]
        }

        "propagate cancellation" in {
          p0.isCancelled must beFalse
          p1.isCancelled must beFalse
          f.cancel()
          p0.isCancelled must beTrue
          p1.isCancelled must beTrue
        }
      }

      "join" in {
        val p0 = new Promise[Int]
        val p1 = new Promise[Int]
        val f = p0 join p1
        f.isDefined must beFalse

        "only return when both futures complete" in {
          p0() = Return(1)
          f.isDefined must beFalse
          p1() = Return(2)
          f() must be_==(1, 2)
        }

        "return with exception if the first future throws" in {
          p0() = Throw(new Exception)
          f() must throwA[Exception]
        }

        "return with exception if the second future throws" in {
          p0() = Return(1)
          f.isDefined must beFalse
          p1() = Throw(new Exception)
          f() must throwA[Exception]
        }

        "propagate cancellation" in {
          p0.isCancelled must beFalse
          p1.isCancelled must beFalse
          f.cancel()
          p0.isCancelled must beTrue
          p1.isCancelled must beTrue
        }
      }

      "toJavaFuture" in {
        "return the same thing as our Future when initialized" in {
          val f = const.value(1)
          val jf = f.toJavaFuture
          f.get() mustBe jf.get()
          "must both be done" in {
            f.isDefined must beTrue
            jf.isDone must beTrue
            f.isCancelled must beFalse
            jf.isCancelled must beFalse
          }
        }

        "return the same thing as our Future when set later" in {
          val f = new Promise[Int]
          val jf = f.toJavaFuture
          f.setValue(1)
          f.get() mustBe jf.get()
          "must both be done" in {
            f.isDefined must beTrue
            jf.isDone must beTrue
            f.isCancelled must beFalse
            jf.isCancelled must beFalse
          }
        }

        "cancel when the java future is cancelled" in {
          val f = new Promise[Int]
          val jf = f.toJavaFuture
          f.isDefined mustBe false
          jf.isDone mustBe false
          jf.cancel(true) mustBe true
          f.isCancelled mustBe true
          jf.isCancelled mustBe true
        }

        "cancel when the twitter future is cancelled" in {
          val f = new Promise[Int]
          val jf = f.toJavaFuture
          f.isDefined mustBe false
          jf.isDone mustBe false
          f.cancel()
          f.isCancelled mustBe true
          jf.isCancelled mustBe true
        }

        "java future should throw an exception" in {
          val f = new Promise[Int]
          val jf = f.toJavaFuture
          f.setException(new RuntimeException())
          jf.get() must throwA(new RuntimeException())
        }
      }

      "monitored" in {
        val inner = new Promise[Int]
        val exc = new Exception("some exception")
        "catch raw exceptions (direct)" in {
          val f = Future.monitored {
            throw exc
            inner
          }
          f.poll must beSome(Throw(exc))
        }

        "catch raw exceptions (indirect), cancelling computation" in {
          val inner1 = new Promise[Int]
          var ran = false
          val f = Future.monitored {
            inner1 ensure {
              throw exc
            }
            inner1 ensure {
              ran = true
              inner.update(Return(1)) mustNot throwA[Throwable]
            }
            inner
          }
          ran must beFalse
          f.poll must beNone
          inner.isCancelled must beFalse
          inner1.update(Return(1)) mustNot throwA[Throwable]
          ran must beTrue
          f.poll must beSome(Throw(exc))
          inner.isCancelled must beTrue
        }

        "link" in {
          val f = Future.monitored { inner }
          inner.isCancelled must beFalse
          f.cancel()
          inner.isCancelled must beTrue
        }
      }
    }

    "Promise (%s)".format(name) should {
      "apply" in {
        "when we're inside of a respond block (without deadlocking)" in {
          val f = Future(1)
          var didRun = false
          f foreach { _ =>
            f mustProduce Return(1)
            didRun = true
          }
          didRun must beTrue
        }
      }

      "map" in {
        "when it's all chill" in {
          val f = Future(1) map { x => x + 1 }
          f() mustEqual 2
        }

        "when there's a problem in the passed in function" in {
          val e = new Exception
          val f = Future(1) map { x =>
            throw e
            x + 1
          }
          f() must throwA(e)
        }

        "cancellation" in {
          val f1 = new Promise[Int]
          val f2 = f1 map { _ => () }
          f1.isCancelled must beFalse
          f2.cancel()
          f1.isCancelled must beTrue
        }
      }

      "transform" in {
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
      }

      "transformedBy" in {
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

      "flatMap" in {
        "successes" in {
          val f = Future(1) flatMap { x => Future(x + 1) }

          "apply" in {
            f() mustEqual 2
          }

          "respond" in {
            f mustProduce Return(2)
          }

          "cancellation of the produced future" in {
            "before the antecedent Future completes, propagates back to the antecedent" in {
              val f1, f2 = new Promise[Int]
              val f = f1 flatMap { _ => f2 }
              f1.isCancelled must beFalse
              f2.isCancelled must beFalse
              f.cancel()
              f1.isCancelled must beTrue
              f1() = Return(2)
              f2.isCancelled must beTrue
            }

            "after the antecedent Future completes, does not propagate back to the antecedent" in {
              val f1, f2= new Promise[Int]
              val f = f1 flatMap { _ => f2 }
              f1.isCancelled must beFalse
              f2.isCancelled must beFalse
              f1() = Return(2)
              f.cancel()
              f1.isCancelled must beFalse
              f2.isCancelled must beTrue
            }
          }
        }

        "failures" in {
          val e = new Exception
          val g = Future[Int](throw e) flatMap { x => Future(x + 1) }

          "apply" in {
            g() must throwA(e)
          }

          "respond" in {
            g mustProduce Throw(e)
          }

          "when there is an exception in the passed in function" in {
            val e = new Exception
            val f = Future(1).flatMap[Int, Future] { x =>
              throw e
            }
            f() must throwA(e)
          }
        }
      }

      "flatten" in {
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

        "cancellation" in {
          val f1 = new Promise[Future[Int]]
          val f2 = new Promise[Int]
          val f = f1.flatten
          f1.isCancelled must beFalse
          f2.isCancelled must beFalse
          f.cancel()
          f1.isCancelled must beTrue
          f2.isCancelled must beFalse
          f1() = Return(f2)
          f2.isCancelled must beTrue
        }
      }

      "rescue" in {
        val e = new Exception

        "successes" in {
          val f = Future(1) rescue { case e => Future(2) }

          "apply" in {
            f() mustEqual 1
          }

          "respond" in {
            f mustProduce Return(1)
          }
        }

        "failures" in {
          val g = Future[Int](throw e) rescue { case e => Future(2) }

          "apply" in {
            g() mustEqual 2 //must throwA(e)
          }

          "respond" in {
            g mustProduce Return(2)
          }

          "when the error handler errors" in {
            val g = Future[Int](throw e) rescue { case e => throw e; Future(2) }
            g() must throwA(e)
          }
        }

        "cancellation of the produced future" in {
          "before the antecedent Future completes, propagates back to the antecedent" in {
            val f1, f2 = new Promise[Int]
            val f = f1 rescue { case _ => f2 }
            f1.isCancelled must beFalse
            f2.isCancelled must beFalse
            f.cancel()
            f1.isCancelled must beTrue
            f2.isCancelled must beFalse
            f1() = Throw(new Exception)
            f2.isCancelled must beTrue
          }

          "after the antecedent Future completes, does not propagate back to the antecedent" in {
            val f1, f2 = new Promise[Int]
            val f = f1 rescue { case _ => f2 }
            f1.isCancelled must beFalse
            f2.isCancelled must beFalse
            f1() = Throw(new Exception)
            f.cancel()
            f1.isCancelled must beFalse
            f2.isCancelled must beTrue
          }
        }
      }

      "foreach" in {
        var wasCalledWith: Option[Int] = None
        val f = Future(1)
        f foreach { i =>
          wasCalledWith = Some(i)
        }
        wasCalledWith mustEqual Some(1)
      }

      "respond" in {
        "when the result has arrived" in {
          var wasCalledWith: Option[Int] = None
          val f = Future(1)
          f respond {
            case Return(i) => wasCalledWith = Some(i)
            case Throw(e) => fail(e.toString)
          }
          wasCalledWith mustEqual Some(1)
        }

        "when the result has not yet arrived it buffers computations" in {
          var wasCalledWith: Option[Int] = None
          val f = new Promise[Int]
          f foreach { i =>
            wasCalledWith = Some(i)
          }
          wasCalledWith mustEqual None
          f()= Return(1)
          wasCalledWith mustEqual Some(1)
        }

        "monitor exceptions" in {
          val m = spy(new MonitorSpec.MockMonitor)
          val exc = new Exception
          m.handle(any) returns true
          val p = new Promise[Int]

          m {
            p ensure { throw exc }
          }

          there was no(m).handle(any)
          p.update(Return(1)) mustNot throwA[Throwable]
          there was one(m).handle(exc)
        }
      }

      "willEqual" in {
        (const.value(1) willEqual(const.value(1)))(1.second) must beTrue
      }

      "Future() handles exceptions" in {
        val e = new Exception
        val f = Future[Int] { throw e }
        f() must throwA(e)
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
        promise0() = Return(())
        local() = 321
        promise1() = Return(())

        both.isDefined must beTrue
        both() must be_==((Some(1010), Some(1010)))
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

        done.isDefined must beTrue
        done() must be_==((Some(1010), Some(123)))
      }

      "cancellation" in {
        val c = spy(new Promise[Int])
        val c1 = spy(new Promise[Int])

        "dispatch onCancellation upon cancellation" in {
          val p = new Promise[Int]
          var wasRun = false
          p onCancellation { wasRun = true }
          wasRun must beFalse
          p.cancel()
          wasRun must beTrue
        }

        "cancel a linked cancellable (after cancellation)" in {
          c.cancel()
          there was no(c1).cancel()
          c.linkTo(c1)
          there was one(c1).cancel()
        }

        "cancel a linked cancellable (before cancellation)" in {
          c.linkTo(c1)
          there was no(c1).cancel()
          c.cancel()
          there was one(c1).cancel()
        }
      }

      "poll" in {
        val p = new Promise[Int]
        "when waiting" in {
          p.poll must beNone
        }

        "when succeeding" in {
          p.setValue(1)
          p.poll must beSome(Return(1))
        }

        "when failing" in {
          val e = new Exception
          p.setException(e)
          p.poll must beSome(Throw(e))
        }
      }

      "within" in {
        "when we run out of time" in {
          implicit val timer = new JavaTimer
          val p = new Promise[Int]
          p.within(50.milliseconds).get() must throwA[TimeoutException]
          timer.stop()
        }

        "when everything is chill" in {
          implicit val timer = new JavaTimer
          val p = new Promise[Int]
          p.setValue(1)
          p.within(50.milliseconds).get() mustBe 1
          timer.stop()
        }

        "cancellation" in Time.withCurrentTimeFrozen { tc =>
          implicit val timer = new MockTimer
          val p = new Promise[Int]
          val f = p.within(50.milliseconds)
          p.isCancelled must beFalse
          f.cancel()
          p.isCancelled must beTrue
        }
      }
    }

    "FutureTask (%s)".format(name) should {
      "return result" in {
        val task = new FutureTask("hello")
        task.run()
        task() mustEqual "hello"
      }

      "throw result" in {
        val task = new FutureTask[String](throw new IllegalStateException)
        task.run()
        task() must throwA(new IllegalStateException)
      }
    }
  }

  test("ConstFuture", new MkConst { def apply[A](r: Try[A]) = Future.const(r) })
  test("Promise", new MkConst { def apply[A](r: Try[A]) = new Promise(r) })
  
  "Future.never" should {
    "must be undefined" in {
      Future.never.isDefined must beFalse
      Future.never.poll must beNone
    }
    
    "always time out" in {
      Future.never.get(0.milliseconds) must throwA[TimeoutException]
    }
  }
}
