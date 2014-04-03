package com.twitter.util

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LocalTest extends FunSuite {
  test("Local: should be undefined by default") {
    val local = new Local[Int]
    assert(local() === None)
  }

  test("Local: should hold on to values") {
    val local = new Local[Int]
    local() = 123
    assert(local() === Some(123))
  }

  test("Local: should have a per-thread definition") {
    val local = new Local[Int]
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

  test("Local: should maintain value definitions when other locals change") {
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

  test("Local.restore: should restore saved values") {
    val local = new Local[Int]
    local() = 123
    val saved = Local.save()
    local() = 321

    Local.restore(saved)
    assert(local() === Some(123))
  }

  test("Local.restore: should unset undefined variables when restoring") {
    val local = new Local[Int]
    val saved = Local.save()
    local() = 123
    Local.restore(saved)

    assert(local() === None)
  }

  test("Local.restore: should not restore cleared variables") {
    val local = new Local[Int]

    local() = 123
    Local.save()  // to trigger caching
    local.clear()
    Local.restore(Local.save())
    assert(local() === None)
  }

  test("Local.let: should scope with a value and restore previous value") {
    val local = new Local[Int]
    local() = 123
    local.let(321) {
      assert(local() === Some(321))
    }
    assert(local() === Some(123))
  }

  test("Local.letClear: should clear Local and restore previous value") {
    val local = new Local[Int]
    local() = 123
    local.letClear {
      assert(local() === None)
    }
    assert(local() === Some(123))
  }

  test("Local.clear: should make a copy when clearing") {
    val l = new Local[Int]
    l() = 1
    val save0 = Local.save()
    l.clear()
    assert(l() === None)
    Local.restore(save0)
    assert(l() === Some(1))
  }
}
