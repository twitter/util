package com.twitter.util.registry

import com.twitter.util.NoStacktrace
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class FormatterTest extends FunSuite {
  test("asMap generates reasonable Maps") {
    val registry = new SimpleRegistry
    registry.put(Seq("foo", "bar"), "baz")
    registry.put(Seq("foo", "qux"), "quux")

    val actual = Formatter.asMap(registry)
    val expected = Map("registry" -> Map("foo" -> Map("bar" -> "baz", "qux" -> "quux")))
    assert(actual == expected)
  }

  test("add should handle empties") {
    assert(
      Formatter.add(Map.empty, Seq.empty, "big") == Map(Formatter.Eponymous -> "big")
    )
  }

  test("add should handle putting an entry in an existing map if nothing's there") {
    assert(
      Formatter.add(Map.empty, Seq("it's"), "big") == Map("it's" -> "big")
    )
  }

  test("add should handle putting recursive entries in an existing map if nothing's there") {
    val actual = Formatter.add(Map.empty, Seq("it's", "very"), "big")
    val expected = Map("it's" -> Map("very" -> "big"))
    assert(actual == expected)
  }

  test("add should handle colliding prefixes") {
    val actual = Formatter.add(Map("it's" -> Map("not" -> "small")), Seq("it's", "very"), "big")
    val expected = Map("it's" -> Map("very" -> "big", "not" -> "small"))
    assert(actual == expected)
  }

  test("add should handle colliding prefixes that are shorter") {
    val actual = Formatter.add(Map("it's" -> "small"), Seq("it's", "very"), "big")
    val expected = Map("it's" -> Map("very" -> "big", Formatter.Eponymous -> "small"))
    assert(actual == expected)
  }

  test("add should bail on collisions") {
    val actual = intercept[Exception with NoStacktrace] {
      Formatter.add(Map("it's" -> "small"), Seq("it's"), "big")
    }
    val expected = Formatter.Collision
    assert(actual == expected)
  }

  test("add should bail on finding a weird type") {
    val actual = intercept[Exception with NoStacktrace] {
      Formatter.add(Map("it's" -> new Object), Seq("it's"), "big")
    }
    val expected = Formatter.InvalidType
    assert(actual == expected)
  }

  test("makeMap should make a map") {
    val seq = Seq("my", "spoon", "is", "too")
    val value = "big"
    val actual = Formatter.makeMap(seq, value)

    val expected = Map("my" -> Map("spoon" -> Map("is" -> Map("too" -> "big"))))
    assert(actual == expected)
  }

  test("makeMap should fail on empties") {
    val seq = Seq.empty
    val value = "big"
    val actual = intercept[Exception with NoStacktrace] {
      Formatter.makeMap(seq, value)
    }
    assert(actual == Formatter.Empty)
  }
}
