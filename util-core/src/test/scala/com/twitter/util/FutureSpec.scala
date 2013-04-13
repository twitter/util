package com.twitter.util

import com.twitter.common.objectsize.ObjectSizeCalculator
import com.twitter.conversions.time._
import java.util.concurrent.ConcurrentLinkedQueue
import org.specs.SpecificationWithJUnit
import org.specs.mock.Mockito
import scala.collection.JavaConverters._

class FutureSpec extends SpecificationWithJUnit with Mockito {
  implicit def futureMatcher[A](future: Future[A]) = new {
    def mustProduce(expected: Try[A]) {
      expected match {
        case Throw(ex) =>
          Await.result(future, 1.second) must throwA(ex)
        case Return(v) =>
          Await.result(future, 1.second) must be_==(v)
      }
    }
  }

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
    "object Future (%s)".format(name) should {
      "times" in {
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

        "when interrupted" in {
          ninterrupt must be_==(0)
          iteration.raise(new Exception)
          for (i <- 1 to 3) {
            ninterrupt must be_==(i)
            queue.poll().setValue(())
          }
        }
      }

      "whileDo" in {
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

        "when interrupted" in {
          queue.asScala exists { _.handled.isDefined } must beFalse
          iteration.raise(new Exception)
          queue.asScala forall { _.handled.isDefined } must beTrue
        }
      }

      "collect" in {
        val p0, p1 = new HandledPromise[Int]
        val f = Future.collect(Seq(p0, p1))
        f.isDefined must beFalse

        "only return when both futures complete" in {
          p0() = Return(1)
          f.isDefined must beFalse
          p1() = Return(2)
          f.isDefined must beTrue
          Await.result(f) must be_==(Seq(1, 2))
        }

        "return with exception if the first future throws" in {
          p0() = Throw(new Exception)
          Await.result(f) must throwA[Exception]
        }

        "return with exception if the second future throws" in {
          p0() = Return(1)
          f.isDefined must beFalse
          p1() = Throw(new Exception)
          Await.result(f) must throwA[Exception]
        }

        "propagate interrupts" in {
          val ps = Seq(p0, p1)
          ps.count(_.handled.isDefined) must be_==(0)
          f.raise(new Exception)
          ps.count(_.handled.isDefined) must be_==(2)
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
              Await.result(f) must beLike {
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
              Await.result(f) must beLike {
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
          Await.result(f) must throwA(new IllegalArgumentException("empty future list!"))
        }

        "propagate interrupts" in {
          val fs = for (_ <- 0 until 10 toArray) yield new HandledPromise[Int]
          Future.select(fs).raise(new Exception)
          fs forall (_.handled.isDefined) must beTrue
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
        var nhandled = 0
        val p0, p1 = new HandledPromise[Int]
        val f = p0 select p1
        f.isDefined must beFalse

        "select the first [result] to complete" in {
          p0() = Return(1)
          p1() = Return(2)
          Await.result(f) must be_==(1)
        }

        "select the first [exception] to complete" in {
          p0() = Throw(new Exception)
          p1() = Return(2)
          Await.result(f) must throwA[Exception]
        }

        "propagate interrupts" in {
          val ps = Seq(p0, p1)
          ps exists (_.handled.isDefined) must beFalse
          f.raise(new Exception)
          ps forall (_.handled.isDefined) must beTrue
        }
      }

      def testJoin(label: String, joiner: ((Future[Int], Future[Int]) => Future[(Int, Int)])) {
        "join(%s)".format(label) in {
          val p0 = new HandledPromise[Int]
          val p1 = new HandledPromise[Int]
          val f = joiner(p0, p1)
          f.isDefined must beFalse

          "only return when both futures complete" in {
            p0() = Return(1)
            f.isDefined must beFalse
            p1() = Return(2)
            Await.result(f) must be_==(1, 2)
          }

          "return with exception if the first future throws" in {
            p0() = Throw(new Exception)
            Await.result(f) must throwA[Exception]
          }

          "return with exception if the second future throws" in {
            p0() = Return(1)
            f.isDefined must beFalse
            p1() = Throw(new Exception)
            Await.result(f) must throwA[Exception]
          }

          "propagate interrupts" in {
            p0.handled must beNone
            p1.handled must beNone
            val exc = new Exception
            f.raise(exc)
            p0.handled must beSome(exc)
            p1.handled must beSome(exc)
          }
        }
      }

      testJoin("f join g", _ join _)
      testJoin("Future.join(f, g)", Future.join(_, _))

      "toJavaFuture" in {
        "return the same thing as our Future when initialized" in {
          val f = const.value(1)
          val jf = f.toJavaFuture
          Await.result(f) mustBe jf.get()
          "must both be done" in {
            f.isDefined must beTrue
            jf.isDone must beTrue
            jf.isCancelled must beFalse
          }
        }

        "return the same thing as our Future when set later" in {
          val f = new Promise[Int]
          val jf = f.toJavaFuture
          f.setValue(1)
          Await.result(f) mustBe jf.get()
          "must both be done" in {
            f.isDefined must beTrue
            jf.isDone must beTrue
            jf.isCancelled must beFalse
          }
        }

        "java future should throw an exception" in {
          val f = new Promise[Int]
          val jf = f.toJavaFuture
          f.setException(new RuntimeException())
          jf.get() must throwA(new RuntimeException())
        }

        "interrupt Future when cancelled" in {
          val f = new HandledPromise[Int]
          val jf = f.toJavaFuture
          f.handled must beNone
          jf.cancel(true)
          f.handled must beLike {
            case Some(e: java.util.concurrent.CancellationException) => true
          }
        }
      }

      "monitored" in {
        val inner = new HandledPromise[Int]
        val exc = new Exception("some exception")
        "catch raw exceptions (direct)" in {
          val f = Future.monitored {
            throw exc
            inner
          }
          f.poll must beSome(Throw(exc))
        }

        "catch raw exceptions (indirect), interrupting computation" in {
          val inner1 = new Promise[Int]
          var ran = false
          val f = Future.monitored {
            inner1 ensure {
              throw exc
            } ensure {
              // Note that these are sequenced so that interrupts
              // will be delivered before inner's handler is cleared.
              ran = true
              inner.update(Return(1)) mustNot throwA[Throwable]
            }
            inner
          }
          ran must beFalse
          f.poll must beNone
          inner.handled must beNone
          inner1.update(Return(1)) mustNot throwA[Throwable]
          ran must beTrue
          inner.isDefined must beTrue
          f.poll must beSome(Throw(exc))

          inner.handled must beSome(exc)
        }

        "link" in {
          val f = Future.monitored { inner }
          inner.handled must beNone
          val exc = new Exception
          f.raise(exc)
          inner.handled must beSome(exc)
        }

        "doesn't leak the underlying promise after completion" in {
          val inner = new Promise[String]
          val inner1 = new Promise[String]
          val f = Future.monitored { inner1.ensure(()); inner }
          val s = "."*1024
          val sSize = ObjectSizeCalculator.getObjectSize(s)
          inner.setValue("."*1024)
          val inner1Size = ObjectSizeCalculator.getObjectSize(inner1)
          inner1Size must be_<(sSize)
        }
      }
      
      "get(deprecated)" in {
        val e = new Exception
        val v = 123
        Future.exception[Int](e).get(0.seconds) must be_==(Throw(e))
        Future.value(v).get(0.seconds) must be_==(Return(v))
        
        // Including fatal ones:
        val e2 = new java.lang.IllegalAccessError
        Future.exception[Int](e2).get(0.seconds)  must be_==(Throw(e2))

        implicit val timer = new JavaTimer
        val p = new Promise[Int]
        val r = p.get(50.milliseconds)
        r() must throwA[TimeoutException]
        timer.stop()
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
          Await.result(f) mustEqual 2
        }

        "when there's a problem in the passed in function" in {
          val e = new Exception
          val f = Future(1) map { x =>
            throw e
            x + 1
          }
          Await.result(f) must throwA(e)
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
            Await.result(f) mustEqual 2
          }

          "respond" in {
            f mustProduce Return(2)
          }

          "interruption of the produced future" in {
            "before the antecedent Future completes, propagates back to the antecedent" in {
              val f1, f2 = new HandledPromise[Int]
              val f = f1 flatMap { _ => f2 }
              f1.handled must beNone
              f2.handled must beNone
              f.raise(new Exception)
              f1.handled must beSomething
              f1() = Return(2)
              f2.handled must beSomething
            }

            "after the antecedent Future completes, does not propagate back to the antecedent" in {
              val f1, f2 = new HandledPromise[Int]
              val f = f1 flatMap { _ => f2 }
              f1.handled must beNone
              f2.handled must beNone
              f1() = Return(2)
              f.raise(new Exception)
              f1.handled must beNone
              f2.handled must beSomething
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
              didInterrupt must beFalse
              f1.setValue(())
              didInterrupt must beFalse
              f2.setValue(())
              didInterrupt must beTrue
            }
          }
        }

        "failures" in {
          val e = new Exception
          val g = Future[Int](throw e) flatMap { x => Future(x + 1) }

          "apply" in {
            Await.result(g) must throwA(e)
          }

          "respond" in {
            g mustProduce Throw(e)
          }

          "when there is an exception in the passed in function" in {
            val e = new Exception
            val f = Future(1).flatMap[Int] { x =>
              throw e
            }
            Await.result(f) must throwA(e)
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

        "interruption" in {
          val f1 = new HandledPromise[Future[Int]]
          val f2 = new HandledPromise[Int]
          val f = f1.flatten
          f1.handled must beNone
          f2.handled must beNone
          f.raise(new Exception)
          f1.handled must beSomething
          f2.handled must beNone
          f1() = Return(f2)
          f2.handled must beSomething
        }
      }

      "rescue" in {
        val e = new Exception

        "successes" in {
          val f = Future(1) rescue { case e => Future(2) }

          "apply" in {
            Await.result(f) mustEqual 1
          }

          "respond" in {
            f mustProduce Return(1)
          }
        }

        "failures" in {
          val g = Future[Int](throw e) rescue { case e => Future(2) }

          "apply" in {
            Await.result(g) mustEqual 2 //must throwA(e)
          }

          "respond" in {
            g mustProduce Return(2)
          }

          "when the error handler errors" in {
            val g = Future[Int](throw e) rescue { case e => throw e; Future(2) }
            Await.result(g) must throwA(e)
          }
        }

        "interruption of the produced future" in {
          "before the antecedent Future completes, propagates back to the antecedent" in {
            val f1, f2 = new HandledPromise[Int]
            val f = f1 rescue { case _ => f2 }
            f1.handled must beNone
            f2.handled must beNone
            f.raise(new Exception)
            f1.handled must beSomething
            f2.handled must beNone
            f1() = Throw(new Exception)
            f2.handled must beSomething
          }

          "after the antecedent Future completes, does not propagate back to the antecedent" in {
            val f1, f2 = new HandledPromise[Int]
            val f = f1 rescue { case _ => f2 }
            f1.handled must beNone
            f2.handled must beNone
            f1() = Throw(new Exception)
            f.raise(new Exception)
            f1.handled must beNone
            f2.handled must beSomething
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

          i must be_==(0)
          j must be_==(0)
          k must be_==(0)
          h must be_==(0)

          p.setValue(1)
          i must be_==(1)
          j must be_==(2)
          k must be_==(4)
          h must be_==(8)
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
          
          m.handled must beNull
          p.update(Return(1)) mustNot throwA[Throwable]
          m.handled must be_==(exc)
        }
      }

      "willEqual" in {
        Await.result((const.value(1) willEqual(const.value(1))), 1.second) must beTrue
      }

      "Future() handles exceptions" in {
        val e = new Exception
        val f = Future[Int] { throw e }
        Await.result(f) must throwA(e)
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
        Await.result(both) must be_==((Some(1010), Some(1010)))
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
        Await.result(done) must be_==((Some(1010), Some(123)))
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
          Await.result(p.within(50.milliseconds)) must throwA[TimeoutException]
          timer.stop()
        }

        "when everything is chill" in {
          implicit val timer = new JavaTimer
          val p = new Promise[Int]
          p.setValue(1)
          Await.result(p.within(50.milliseconds)) mustBe 1
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
          p.within(Duration.Top) must be(p)
        }

        "interruption" in Time.withCurrentTimeFrozen { tc =>
          implicit val timer = new MockTimer
          val p = new HandledPromise[Int]
          val f = p.within(50.milliseconds)
          p.handled must beNone
          f.raise(new Exception)
          p.handled must beSomething
        }
      }
    }

    "FutureTask (%s)".format(name) should {
      "return result" in {
        val task = new FutureTask("hello")
        task.run()
        Await.result(task) mustEqual "hello"
      }

      "throw result" in {
        val task = new FutureTask[String](throw new IllegalStateException)
        task.run()
        Await.result(task) must throwA(new IllegalStateException)
      }
    }
  }

  test("ConstFuture", new MkConst { def apply[A](r: Try[A]) = Future.const(r) })
  test("Promise", new MkConst { def apply[A](r: Try[A]) = new Promise(r) })

  "Future.None" should {
    "always be defined" in {
      Future.None.isDefined must beTrue
    }
    "but still None" in {
      Await.result(Future.None) must beNone
    }
  }

  "Future.never" should {
    "must be undefined" in {
      Future.never.isDefined must beFalse
      Future.never.poll must beNone
    }

    "always time out" in {
      Await.ready(Future.never, 0.milliseconds) must throwA[TimeoutException]
    }
  }

  "Java" should {
    "work" in {
      val test = new FutureTest()
      test.testFutureCastMap()
      test.testFutureCastFlatMap()
      test.testTransformedBy()
      true mustEqual true
    }
  }
}

