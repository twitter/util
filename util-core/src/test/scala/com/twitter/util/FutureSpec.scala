package com.twitter.util

import org.specs.Specification
import com.twitter.conversions.time._
import java.util.concurrent.ConcurrentLinkedQueue
import com.twitter.concurrent.SimpleSetter

object FutureSpec extends Specification {
  "Future" should {
    import Future._

    "times" in {
      val queue = new ConcurrentLinkedQueue[Promise[Unit]]
      var complete = false
      var failure = false
      var exception: Throwable = null
      val iteration = times(3) {
        val promise = new Promise[Unit]
        queue add promise
        promise
      }
      iteration onSuccess { _ =>
        complete = true
      } onFailure { f =>
        failure = true
        exception = f
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
          f.isDefined must beTrue
          f() must throwA(Future.CancelledException)
        }
        complete mustBe true
        failure mustBe true
        exception must be_==(Future.CancelledException)
      }
    }

    "whileDo" in {
      var i = 0
      val queue = new ConcurrentLinkedQueue[Promise[Unit]]
      var complete = false
      var failure = false
      var exception: Throwable = null
      val iteration = whileDo(i < 3) {
        i += 1
        val promise = new Promise[Unit]
        queue add promise
        promise
      }

      iteration onSuccess { _ =>
        complete = true
      } onFailure { f =>
        failure = true
        exception = f
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
        queue.poll().setValue(())
        iteration.cancel()
        val f = queue.poll()
        f.isDefined must beTrue
        f() must throwA(Future.CancelledException)
        complete mustBe false
        failure mustBe true
        exception must be_==(Future.CancelledException)
      }
    }
  }

  "Promise" should {
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

      "when the cancellation reigns" in {
        val p = new Promise[Int]
        var didCompute = false
        val f = p map { _ => didCompute = true; 111 }
        f.isDefined must beFalse
        f.cancel()
        f.isDefined must beTrue
        f() must throwA(Future.CancelledException)
        didCompute must beFalse
      }
    }

    "flatMap" in {
      "successes" in {
        val f = Future(1) flatMap { x => Future(x + 1) }

        "apply" in {
          f() mustEqual 2
        }

        "respond" in {
          val latch = new CountDownLatch(1)
          f respond { response =>
            response mustEqual Return(2)
            latch.countDown()
          }
          latch.within(1.second)
        }
      }

      "failures" in {
        val e = new Exception
        val g = Future[Int](throw e) flatMap { x => Future(x + 1) }

        "apply" in {
          g() must throwA(e)
        }

        "respond" in {
          val latch = new CountDownLatch(1)
          g respond { response =>
            response mustEqual Throw(e)
            latch.countDown()
          }
          latch.within(1.second)
        }

        "when there is an exception in the passed in function" in {
          val e = new Exception
          val f = Future(1).flatMap[Int, Future] { x =>
            throw e
          }
          f() must throwA(e)
        }
      }

      "cancellation" in {
        "propagate" in {
          val p = new Promise[String]
          val f = Future[Int](1) flatMap { _ => p }
          f.isDefined must beFalse
          p.isDefined must beFalse
          f.cancel()
          p.isDefined must beTrue
          p() must throwA(Future.CancelledException)
        }
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
          val latch = new CountDownLatch(1)
          f respond { response =>
            response mustEqual Return(1)
            latch.countDown()
          }
          latch.within(1.second)
        }
      }

      "failures" in {
        val g = Future[Int](throw e) rescue { case e => Future(2) }

        "apply" in {
          g() mustEqual 2 //must throwA(e)
        }

        "respond" in {
          val latch = new CountDownLatch(1)
          g respond { response =>
            response mustEqual Return(2)
            latch.countDown()
          }
          latch.within(1.second)
        }

        "when the error handler errors" in {
          val g = Future[Int](throw e) rescue { case e => throw e; Future(2) }
          g() must throwA(e)
        }
      }

      "cancellation" in {
        val p = new Promise[Int]
        val g = Future[Int](throw e) rescue { case e => p }
        g.isDefined must beFalse
        g.cancel()
        g.isDefined must beTrue
        g() must throwA(Future.CancelledException)
        p.isDefined must beTrue
        p() must throwA(Future.CancelledException)
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

    "when cancelled" in Time.withCurrentTimeFrozen { tc =>
      implicit val timer = new MockTimer
      val p = new Promise[Int]
      val f = p.within(50.milliseconds)
      f.cancel()
      p.isDefined must beTrue
      p() must throwA(Future.CancelledException)
      tc.advance(1.second)
      timer.tick()
      // *crickets*
    }
  }

  "FutureTask" should {
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

  "Future.select()" should {
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
      p0.isDefined must beFalse
      p1.isDefined must beFalse
      f.cancel()
      p0.isDefined must beTrue
      p1.isDefined must beTrue
      p0() must throwA(Future.CancelledException)
      p1() must throwA(Future.CancelledException)
    }
  }

  "Future.join()" should {
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
      f.cancel()
      p0.isDefined must beTrue
      p0() must throwA(Future.CancelledException)
      p1.isDefined must beTrue
      p1() must throwA(Future.CancelledException)
    }
  }

  "Future.collect()" should {
    val p0 = new Promise[Int]
    val p1 = new Promise[Int]
    val f = Future.collect(Seq(p0, p1))
    f.isDefined must beFalse

    "only return when both futures complete" in {
      p0() = Return(1)
      f.isDefined must beFalse
      p1() = Return(2)
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
      f.cancel()
      p0.isDefined must beTrue
      p1.isDefined must beTrue
      p0() must throwA(Future.CancelledException)
      p1() must throwA(Future.CancelledException)
    }
  }

  "Future.select()" should {
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
        f.isDefined must beTrue
        f() must throwA(Future.CancelledException)
      }
    }
  }

  "Promise.cancel" should {
    "throw Future.CancelledException on cancellation" in {
      "on respond" in {
        val p = new Promise[Int]
        var res: Try[Int] = null
        p respond { res = _ }
        p.cancel()
        res must be_==(Throw(Future.CancelledException))
      }

      "on get()" in {
        val p = new Promise[Int]
        p.cancel()
        p.isDefined must beTrue
        p() must throwA(Future.CancelledException)
      }
    }

    "invoke onCancellation upon cancellation" in {
      val p = new Promise[Int]
      var invoked0, invoked1 = false
      p onCancellation { invoked0 = true }
      invoked0 must beFalse
      p.cancel()
      invoked0 must beTrue
      p onCancellation { invoked1 = true }
      invoked1 must beTrue
    }

    "not cancel after a successful set" in {
      val p = new Promise[Int]
      p() = Return(1)
      var res1: Try[Int] = null
      var res2: Try[Int] = null
      p respond { res1 = _ }
      p.cancel()
      p respond { res2 = _ }
      p.isDefined must beTrue
      p() must be_==(1)
      res1 must be_==(Return(1))
      res2 must be_==(Return(1))
    }

    "allow setting *once* after cancellation" in {
      val p = new Promise[Int]
      p.cancel()
      p.updateIfEmpty(Return(1)) must beTrue
      p.updateIfEmpty(Return(2)) must beFalse
    }
  }

  "Future.toOffer" should {
    "activate when future is satisfied (poll)" in {
      val p = new Promise[Int]
      val o = p.toOffer
      o.poll() must beNone
      p() = Return(123)
      o.poll() must beLike {
        case Some(f) =>
          f() must be_==(Return(123))
      }
    }

    "activate when future is satisfied (enqueue)" in {
      val p = new Promise[Int]
      val o = p.toOffer
      val s = new SimpleSetter[Try[Int]]
      o.enqueue(s)
      s.get must beNone
      p() = Return(123)
      s.get must beSome(Return(123))
    }
  }
}
