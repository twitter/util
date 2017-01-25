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

  test("Tunable.toString contains the id of the Tunable") {
    val tunable = Tunable.const("MyTunableId", "hello")
    assert(tunable.toString == "Tunable(MyTunableId)")
  }
}