package com.twitter.util

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.FunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class LocalTest extends FunSuite with ScalaCheckDrivenPropertyChecks {
  test("Local: should be undefined by default") {
    val local = new Local[Int]
    assert(local() == None)
  }

  test("Local: should hold on to values") {
    val local = new Local[Int]
    local() = 123
    assert(local() == Some(123))
  }

  test("Local: should have a per-thread definition") {
    val local = new Local[Int]
    var threadValue: Option[Int] = null

    local() = 123

    val t = new Thread {
      override def run() = {
        assert(local() == None)
        local() = 333
        threadValue = local()
      }
    }

    t.start()
    t.join()

    assert(local() == Some(123))
    assert(threadValue == Some(333))
  }

  test("Local: should maintain value definitions when other locals change") {
    val l0 = new Local[Int]
    l0() = 123
    val save0 = Local.save()
    val l1 = new Local[Int]
    assert(l0() == Some(123))
    l1() = 333
    assert(l1() == Some(333))

    val save1 = Local.save()
    Local.restore(save0)
    assert(l0() == Some(123))
    assert(l1() == None)

    Local.restore(save1)
    assert(l0() == Some(123))
    assert(l1() == Some(333))
  }

  test("Local.restore: should restore saved values") {
    val local = new Local[Int]
    local() = 123
    val saved = Local.save()
    local() = 321

    Local.restore(saved)
    assert(local() == Some(123))
  }

  test("Local.let: should set locals and restore previous value") {
    val l1, l2 = new Local[Int]
    l1() = 1
    l2() = 2
    val ctx = Local.save()
    l1() = 2
    l2() = 4
    var executeCount = 0

    Local.let(ctx) {
      assert(l1() == Some(1))
      assert(l2() == Some(2))
      executeCount += 1
    }

    assert(l1() == Some(2))
    assert(l2() == Some(4))
    assert(executeCount == 1)
  }

  test("Local.letClear: should clear all locals and restore previous value") {
    val l1, l2 = new Local[Int]
    l1() = 1
    l2() = 2

    Local.letClear {
      assert(!l1().isDefined)
      assert(!l2().isDefined)
    }

    assert(l1() == Some(1))
    assert(l2() == Some(2))
  }

  test("Local.restore: should unset undefined variables when restoring") {
    val local = new Local[Int]
    val saved = Local.save()
    local() = 123
    Local.restore(saved)

    assert(local() == None)
  }

  test("Local.restore: should not restore cleared variables") {
    val local = new Local[Int]

    local() = 123
    Local.save() // to trigger caching
    local.clear()
    Local.restore(Local.save())
    assert(local() == None)
  }

  test("Local.let: should scope with a value and restore previous value") {
    val local = new Local[Int]
    local() = 123
    local.let(321) {
      assert(local() == Some(321))
    }
    assert(local() == Some(123))
  }

  test("Local.letClear: should clear Local and restore previous value") {
    val local = new Local[Int]
    local() = 123
    local.letClear {
      assert(local() == None)
    }
    assert(local() == Some(123))
  }

  test("Local.clear: should make a copy when clearing") {
    val l = new Local[Int]
    l() = 1
    val save0 = Local.save()
    l.clear()
    assert(l() == None)
    Local.restore(save0)
    assert(l() == Some(1))
  }

  test("Local.closed") {
    val l = new Local[Int]
    l() = 1
    val adder: () => Int = { () =>
      val rv = 100 + l().get
      l() = 10000
      rv
    }
    val fn = Local.closed(adder)
    l() = 100
    assert(fn() == 101)
    assert(l() == Some(100))
    assert(fn() == 101)
  }

  implicit lazy val arbKey: Arbitrary[Local.Key] = Arbitrary(new Local.Key)

  test("Context should have the same behavior with Map") {
    val context = Local.Context.empty
    val map = Map.empty[Local.Key, Int]

    forAll(arbitrary[Int], arbitrary[Local.Key]) { (value, key) =>
      if (value % 2 == 0) {
        val c = context.set(key, Some(value))
        val m = map.updated(key, value)
        assert(c.get(key) == m.get(key))
      } else {
        val c = context.remove(key)
        val m = map - key
        assert(c.get(key) == m.get(key))
      }
    }
  }

}
