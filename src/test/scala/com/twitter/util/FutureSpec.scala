package com.twitter.util

import org.specs.Specification
import com.twitter.conversions.time._
import java.util.concurrent.ConcurrentLinkedQueue

object FutureSpec extends Specification {
  "Future" should {
    import Future._

    "times" in {
      val queue = new ConcurrentLinkedQueue[Promise[Unit]]
      var complete = false
      var failure = false
      val iteration = times(3) {
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
    }

    "whileDo" in {
      var i = 0
      val queue = new ConcurrentLinkedQueue[Promise[Unit]]
      var complete = false
      var failure = false
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
        println(iteration.isDefined)

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
    }
  }

  "Promise" should {
    "map" in {
      val f = Future(1) map { x => x + 1 }
      f() mustEqual 2
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
      }

      "when there is an exception" in {
        val e = new Exception
        val f = Future(1).flatMap[Int] { x =>
          throw e
        }
        f() must throwA(e)
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
  }
}
