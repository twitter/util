package com.twitter.util

import org.scalatest.WordSpec

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LocalTest extends WordSpec {
  "Local" should {
    val local = new Local[Int]

    "be undefined by default" in {
      assert(local() === None)
    }

    "hold on to values" in {
      local() = 123
      assert(local() === Some(123))
    }

    "restore saved values" in {
      local() = 123
      val saved = Local.save()
      local() = 321

      Local.restore(saved)
      assert(local() === Some(123))
    }

    "have a per-thread definition" in {
      var threadValue: Option[Int] = null

      local() = 123

      val t = new Thread {
        override def run() = {
          assert(local() === None)
          local() = 333
          threadValue = local()
        }
      }

      t.start()
      t.join()

      assert(local() === Some(123))
      assert(threadValue === Some(333))
    }

    "unset undefined variables when restoring" in {
      val local = new Local[Int]

      val saved = Local.save()
      local() = 123
      Local.restore(saved)

      assert(local() === None)
    }

    "not restore cleared variables" in {
      val local = new Local[Int]

      local() = 123
      Local.save()  // to trigger caching
      local.clear()
      Local.restore(Local.save())
      assert(local() === None)
    }

    "maintain value definitions when other locals change" in {
      val l0 = new Local[Int]
      l0() = 123
      val save0 = Local.save()
      val l1 = new Local[Int]
      assert(l0() === Some(123))
      l1() = 333
      assert(l1() === Some(333))

      val save1 = Local.save()
      Local.restore(save0)
      assert(l0() === Some(123))
      assert(l1() === None)

      Local.restore(save1)
      assert(l0() === Some(123))
      assert(l1() === Some(333))
    }

    "make a copy when clearing" in {
      val l = new Local[Int]
      l() = 1
      val save0 = Local.save()
      l.clear()
      assert(l() === None)
      Local.restore(save0)
      assert(l() === Some(1))
    }
  }
}
