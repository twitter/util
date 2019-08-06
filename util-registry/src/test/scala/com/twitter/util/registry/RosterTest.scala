package com.twitter.util.registry

import java.util.logging.Logger
import org.mockito.Mockito.{never, verify}
import org.mockito.Matchers.anyObject
import org.scalatest.FunSuite
import org.scalatestplus.mockito.MockitoSugar

class RosterTest extends FunSuite with MockitoSugar {
  def withRoster(fn: (Roster, Logger) => Unit): Unit = {
    val simple = new SimpleRegistry
    simple.put(Seq("foo", "bar"), "fantastic")
    val log = mock[Logger]
    GlobalRegistry.withRegistry(simple) {
      val roster = new Roster(Seq("foo"), Set("bar", "baz"), log)
      fn(roster, log)
    }
  }

  test("Roster#update updates when the key is good") {
    withRoster { (roster, log) =>
      assert(roster("bar") = "qux")
      val expected = Set(
        Entry(Seq("foo", "bar"), "qux")
      )
      assert(GlobalRegistry.get.toSet == expected)
      verify(log, never).warning(anyObject[String])
    }
  }

  test("Roster#update fails to update when the key is bad") {
    withRoster { (roster, log) =>
      assert(!(roster("unseen") = "qux"))
      val expected = Set(
        Entry(Seq("foo", "bar"), "fantastic")
      )
      assert(GlobalRegistry.get.toSet == expected)
      verify(log, never).warning(anyObject[String])
    }
  }

  test("Roster#update logs noisily when the key is good but the registry is inconsistent") {
    val expected =
      "expected there to be a value at key \"(foo,baz)\" in registry but it was empty."
    withRoster { (roster, log) =>
      assert(!(roster("baz") = "qux"))
      val expectedSet = Set(
        Entry(Seq("foo", "bar"), "fantastic"),
        Entry(Seq("foo", "baz"), "qux")
      )
      assert(GlobalRegistry.get.toSet == expectedSet)
      verify(log).warning(expected)
    }
  }
}
