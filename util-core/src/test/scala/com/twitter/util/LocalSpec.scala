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
      val saved = Locals.save()
      local() = 321

      Locals.restore(saved)
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

      val saved = Locals.save()
      local() = 123
      Locals.restore(saved)

      local() must beNone
    }

    "not restore cleared variables" in {
      val local = new Local[Int]

      local() = 123
      Locals.save()  // to trigger caching
      local.clear()
      Locals.restore(Locals.save())
      local() must beNone
    }
  }
}
