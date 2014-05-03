package com.twitter.util

import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LocalTest extends WordSpec with ShouldMatchers {
  "Local" should {
    val local = new Local[Int]

    "be undefined by default" in {
      local() shouldEqual None
    }

    "hold on to values" in {
      local() = 123
      local() shouldEqual Some(123)
    }

    "restore saved values" in {
      local() = 123
      val saved = Local.save()
      local() = 321

      Local.restore(saved)
      local() shouldEqual Some(123)
    }

    "have a per-thread definition" in {
      var threadValue: Option[Int] = null

      local() = 123

      val t = new Thread {
        override def run() = {
          local() shouldEqual None
          local() = 333
          threadValue = local()
        }
      }

      t.start()
      t.join()

      local() shouldEqual Some(123)
      threadValue shouldEqual Some(333)
    }

    "unset undefined variables when restoring" in {
      val local = new Local[Int]

      val saved = Local.save()
      local() = 123
      Local.restore(saved)

      local() shouldEqual None
    }

    "not restore cleared variables" in {
      val local = new Local[Int]

      local() = 123
      Local.save()  // to trigger caching
      local.clear()
      Local.restore(Local.save())
      local() shouldEqual None
    }

    "maintain value definitions when other locals change" in {
      val l0 = new Local[Int]
      l0() = 123
      val save0 = Local.save()
      val l1 = new Local[Int]
      l0() shouldEqual Some(123)
      l1() = 333
      l1() shouldEqual Some(333)

      val save1 = Local.save()
      Local.restore(save0)
      l0() shouldEqual Some(123)
      l1() shouldEqual None

      Local.restore(save1)
      l0() shouldEqual Some(123)
      l1() shouldEqual Some(333)
    }

    "make a copy when clearing" in {
      val l = new Local[Int]
      l() = 1
      val save0 = Local.save()
      l.clear()
      l() shouldEqual None
      Local.restore(save0)
      l() shouldEqual Some(1)
    }
  }
}
