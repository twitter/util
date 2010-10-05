package com.twitter.util

import org.specs.Specification
import com.twitter.util.TimeConversions._

object FutureSpec extends Specification {
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
  }
}