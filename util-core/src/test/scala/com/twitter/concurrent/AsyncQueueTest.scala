package com.twitter.concurrent


import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import com.twitter.util.{Return, Throw}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AsyncQueueTest extends WordSpec with ShouldMatchers {
  "AsyncQueue" should {
    val q = new AsyncQueue[Int]
    val exc = new Exception("sad panda")

    "queue pollers" in {
      val p0 = q.poll()
      val p1 = q.poll()
      val p2 = q.poll()

      p0.isDefined shouldEqual false
      p1.isDefined shouldEqual false
      p2.isDefined shouldEqual false

      q.offer(1)
      p0.poll shouldEqual Some(Return(1))
      p1.isDefined shouldEqual false
      p2.isDefined shouldEqual false

      q.offer(2)
      p1.poll shouldEqual Some(Return(2))
      p2.isDefined shouldEqual false

      q.offer(3)
      p2.poll shouldEqual Some(Return(3))
    }

    "queue offers" in {
      q.offer(1)
      q.offer(2)
      q.offer(3)

      q.poll().poll shouldEqual Some(Return(1))
      q.poll().poll shouldEqual Some(Return(2))
      q.poll().poll shouldEqual Some(Return(3))
    }

    "into idle state and back" in {
      q.offer(1)
      q.poll().poll shouldEqual Some(Return(1))

      val p = q.poll()
      p.isDefined shouldEqual false
      q.offer(2)
      p.poll shouldEqual Some(Return(2))

      q.offer(3)
      q.poll().poll shouldEqual Some(Return(3))
    }

    "fail pending and new pollers" in {
      val p0 = q.poll()
      val p1 = q.poll()

      p0.isDefined shouldEqual false
      p1.isDefined shouldEqual false

      q.fail(exc)
      p0.poll shouldEqual Some(Throw(exc))
      p1.poll shouldEqual Some(Throw(exc))

      q.poll().poll shouldEqual Some(Throw(exc))
    }

    "fail doesn't blow up offer" in {
      q.fail(exc)
      q.offer(1)
      q.poll().poll shouldEqual Some(Throw(exc))
    }
  }
}
