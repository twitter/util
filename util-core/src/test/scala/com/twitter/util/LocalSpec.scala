package com.twitter.util

import org.specs.Specification

object LocalSpec extends Specification {
  "Local" should {
    val local = new Local[Int]

    "be undefined by default" in {
      local() must beNone
    }

    "hold on to values" in {
      local() = 123
      local() must beSome(123)
    }

    "restore saved values" in {
      local() = 123
      val saved = Local.save()
      local() = 321

      Local.restore(saved)
      local() must beSome(123)
    }

    "have a per-thread definition" in {
      var threadValue: Option[Int] = null

      local() = 123

      val t = new Thread {
        override def run() = {
          local() must beNone
          local() = 333
          threadValue = local()
        }
      }

      t.start()
      t.join()

      local() must beSome(123)
      threadValue must beSome(333)
    }

    "unset undefined variables when restoring" in {
      val local = new Local[Int]

      val saved = Local.save()
      local() = 123
      Local.restore(saved)

      local() must beNone
    }

    "not restore cleared variables" in {
      val local = new Local[Int]

      local() = 123
      Local.save()  // to trigger caching
      local.clear()
      Local.restore(Local.save())
      local() must beNone
    }

    "maintain value definitions when other locals change" in {
      val l0 = new Local[Int]
      l0() = 123
      val save0 = Local.save()
      val l1 = new Local[Int]
      l0() must beSome(123)
      l1() = 333
      l1() must beSome(333)

      val save1 = Local.save()
      Local.restore(save0)
      l0() must beSome(123)
      l1() must beNone

      Local.restore(save1)
      l0() must beSome(123)
      l1() must beSome(333)
    }

    "make a copy when clearing" in {
      val l = new Local[Int]
      l() = 1
      val save0 = Local.save()
      l.clear()
      l() must beNone
      Local.restore(save0)
      l() must beSome(1)
    }
  }
}
