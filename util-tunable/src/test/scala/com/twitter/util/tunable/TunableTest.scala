package com.twitter.util.tunable

import org.scalatest.FunSuite

class TunableTest extends FunSuite {

  test("Tunable cannot have empty id") {
    intercept[IllegalArgumentException] {
      val tunable = Tunable.const("", "hello")
    }

    intercept[IllegalArgumentException] {
      val tunable = Tunable.const("    ", "hello")
    }
  }

  test("Tunable.const produces Some(value) when applied") {
    val tunable = Tunable.const("MyTunableId", "hello")
    assert(tunable() == Some("hello"))
  }

  test("Tunable.none returns None when applied") {
    assert(Tunable.none[String]() == None)
  }

  test("Tunable.toString contains the id of the Tunable") {
    val tunable = Tunable.const("MyTunableId", "hello")
    assert(tunable.toString == "Tunable(MyTunableId)")
  }

  test("Tunable.Mutable created without a value produces None when applied") {
    val tunable = Tunable.emptyMutable("id")
    assert(tunable() == None)
  }

  test("Tunable.Mutable created with a value produces that value when applied") {
    val tunable = Tunable.mutable("id", 5)
    assert(tunable() == Some(5))
  }

  test("Tunable.Mutable created with a value `null` produces Some(null) when applied") {
    val tunable = Tunable.mutable("id", null)
    assert(tunable() == Some(null))
  }

  test("Tunable.Mutable produces the new value when set") {
    val tunable = Tunable.mutable("id", "hello")
    tunable.set("goodbye")
    assert(tunable() == Some("goodbye"))
  }

  test("Tunable.Mutable with a value set to `null` produces Some(null) when applied") {
    val tunable = Tunable.mutable("id", "hello")
    tunable.set(null)
    assert(tunable() == Some(null))
  }

  test("Tunable.Mutable produces None when its value is cleared") {
    val tunable = Tunable.mutable("id", "hello")
    assert(tunable() == Some("hello"))
    tunable.clear()
    assert(tunable() == None)
  }
}