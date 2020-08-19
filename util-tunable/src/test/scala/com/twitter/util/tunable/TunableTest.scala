package com.twitter.util.tunable

import org.scalatest.funsuite.AnyFunSuite

class TunableTest extends AnyFunSuite {

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

  test("orElse Tunable uses the id of the first Tunable") {
    val tunable1 = Tunable.mutable("id1", "hello1")
    val tunable2 = Tunable.mutable("id2", "hello2")
    val composed = tunable1.orElse(tunable2)
    assert(composed.id == tunable1.id)
  }

  test("orElse Tunable uses the value of the first Tunable if it is defined") {
    val tunable1 = Tunable.mutable("id1", "hello1")
    val tunable2 = Tunable.mutable("id2", "hello2")
    val composed = tunable1.orElse(tunable2)
    assert(composed() == Some("hello1"))
  }

  test("orElse Tunable uses the value of the second Tunable if the first is not defined") {
    val tunable1 = Tunable.emptyMutable[String]("id1")
    val tunable2 = Tunable.mutable("id2", "hello2")
    val composed = tunable1.orElse(tunable2)
    assert(composed() == Some("hello2"))
  }

  test("orElse Tunable returns None when applied if neither of the Tunables are defined") {
    val tunable1 = Tunable.emptyMutable[String]("id1")
    val tunable2 = Tunable.emptyMutable[String]("id2")
    val composed = tunable1.orElse(tunable2)
    assert(composed() == None)
  }

  test("orElse reflects the changes of mutable Tunables with an initial value") {
    val tunable1 = Tunable.mutable("id1", "hello1")
    val tunable2 = Tunable.mutable("id2", "hello2")
    val composed = tunable1.orElse(tunable2)
    assert(composed() == Some("hello1"))

    tunable1.set("new hello1")

    assert(composed() == Some("new hello1"))
  }

  test("orElse reflects the changes of mutable Tunables without an initial value") {
    val tunable1 = Tunable.emptyMutable[String]("id1")
    val tunable2 = Tunable.mutable("id2", "hello2")
    val composed = tunable1.orElse(tunable2)
    assert(composed() == Some("hello2"))

    tunable1.set("hello1")

    assert(composed() == Some("hello1"))
  }

  test("orElse reflects the changes of mutable Tunables when they are cleared") {
    val tunable1 = Tunable.mutable("id1", "hello1")
    val tunable2 = Tunable.mutable("id2", "hello2")
    val composed = tunable1.orElse(tunable2)
    assert(composed() == Some("hello1"))

    tunable1.clear()

    assert(composed() == Some("hello2"))
  }

  test("map uses the id of the original Tunable") {
    val tunable = Tunable.mutable("id", 5)
    val composed = tunable.map(v => v * 2)
    assert(composed.id == tunable.id)
  }

  test("map maps the value of the original Tunable if it is defined") {
    val tunable = Tunable.mutable("id", 5)
    val composed = tunable.map(v => v * 2)
    assert(composed() == Some(10))
  }

  test("map returns None when applied if the value of the first Tunable is not defined") {
    val tunable = Tunable.emptyMutable[Int]("id")
    val composed = tunable.map(v => v * 2)
    assert(composed() == None)
  }

  test("map reflects the changes of mutable Tunables with an initial value") {
    val tunable = Tunable.mutable("id", 5)
    val composed = tunable.map(v => v * 2)
    assert(composed() == Some(10))

    tunable.set(7)

    assert(composed() == Some(14))
  }

  test("map reflects the changes of mutable Tunables without an initial value") {
    val tunable = Tunable.emptyMutable[Int]("id")
    val composed = tunable.map(v => v * 2)
    assert(composed() == None)

    tunable.set(5)

    assert(composed() == Some(10))
  }

  test("map reflects the changes of mutable Tunables when it is cleared") {
    val tunable = Tunable.mutable("id", 5)
    val composed = tunable.map(v => v * 2)
    assert(composed() == Some(10))

    tunable.clear()

    assert(composed() == None)
  }

  test("mutable tunables are observable") {
    val tunable = Tunable.mutable("id", 5)
    val obs = tunable.asVar

    assert(obs.sample().contains(5))
  }

  test("empty mutable tunables are observable") {
    val tunable = Tunable.emptyMutable[Int]("id")
    val obs = tunable.asVar

    assert(obs.sample().isEmpty)
    tunable.set(5)
    assert(obs.sample().contains(5))
  }

  test("mutable tunable changes are observed") {
    val tunable = Tunable.mutable("id", 5)
    val obs = tunable.asVar

    assert(obs.sample().contains(5))
    tunable.set(6)
    assert(obs.sample().contains(6))
  }

  test("orElse Tunable is observable if first value is defined") {
    val tunable = Tunable.mutable("a", 5)
    val orElsed = tunable.orElse(Tunable.const("b", 6))

    val obs = orElsed.asVar
    assert(obs.sample().contains(5))
    tunable.clear()
    assert(obs.sample().contains(6))
  }

  test("orElse Tunable is observable if first value is undefined") {
    val tunable = Tunable.emptyMutable[Int]("a")
    val orElsed = tunable.orElse(Tunable.const("b", 5))

    val obs = orElsed.asVar
    assert(obs.sample().contains(5))
    tunable.set(6)
    assert(obs.sample().contains(6))
  }

  test("map of mutable Tunables is observable") {
    val tunable = Tunable.mutable("id", 5)
    val mapped = tunable.map(_ + 1)
    val obs = mapped.asVar

    assert(obs.sample().contains(6))
    tunable.set(6)
    assert(obs.sample().contains(7))
  }

  test("map of const Tunables is observable") {
    val tunable = Tunable.const("id", 5)
    val mapped = tunable.map(_ + 1)
    val obs = mapped.asVar

    assert(obs.sample().contains(6))
  }
}
