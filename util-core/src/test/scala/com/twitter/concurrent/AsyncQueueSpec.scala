package com.twitter.concurrent

import org.specs.Specification
import com.twitter.util.{Return, Throw}

object AsyncQueueSpec extends Specification {
  "AsyncQueue" should {
    val q = new AsyncQueue[Int]

    "queue pollers" in {
      val p0 = q.poll()
      val p1 = q.poll()
      val p2 = q.poll()

      p0.isDefined must beFalse
      p1.isDefined must beFalse
      p2.isDefined must beFalse

      q.offer(1)
      p0.poll must beSome(Return(1))
      p1.isDefined must beFalse
      p2.isDefined must beFalse

      q.offer(2)
      p1.poll must beSome(Return(2))
      p2.isDefined must beFalse

      q.offer(3)
      p2.poll must beSome(Return(3))
    }

    "queue offers" in {
      q.offer(1)
      q.offer(2)
      q.offer(3)

      q.poll().poll must beSome(Return(1))
      q.poll().poll must beSome(Return(2))
      q.poll().poll must beSome(Return(3))
    }

    "into idle state and back" in {
      q.offer(1)
      q.poll().poll must beSome(Return(1))

      val p = q.poll()
      p.isDefined must beFalse
      q.offer(2)
      p.poll must beSome(Return(2))

      q.offer(3)
      q.poll().poll must beSome(Return(3))
    }

    "fail pending and new pollers" in {
      val exc = new Exception("sad panda")
      val p0 = q.poll()
      val p1 = q.poll()

      p0.isDefined must beFalse
      p1.isDefined must beFalse

      q.fail(exc)
      p0.poll must beSome(Throw(exc))
      p1.poll must beSome(Throw(exc))

      q.poll().poll must beSome(Throw(exc))
    }

    "fail doesn't blow up offer" in {
      val exc = new Exception
      q.fail(exc)
      q.offer(1) mustNot throwA[Throwable]
      q.poll().poll must beSome(Throw(exc))
    }
  }
}
