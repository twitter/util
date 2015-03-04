package com.twitter.util.registry

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite

@RunWith(classOf[JUnitRunner])
class LibraryTest extends FunSuite {
  test("Library.register registers libraries") {
    val simple = new SimpleRegistry
    GlobalRegistry.withRegistry(simple) {
      assert(Library.register("foo", Map("bar" -> "baz")).isDefined)
      val expected = Set(
        Entry(Seq("library", "foo"), Library.Registered),
        Entry(Seq("library", "foo", "bar"), "baz")
      )
      assert(GlobalRegistry.get.toSet == expected)
    }
  }

  test("Library.register will fail to register a same-named library") {
    val simple = new SimpleRegistry
    GlobalRegistry.withRegistry(simple) {
      assert(Library.register("foo", Map("bar" -> "baz")).isDefined)
      assert(!Library.register("foo", Map("qux" -> "quux")).isDefined)

      val expected = Set(
        Entry(Seq("library", "foo"), Library.Registered),
        Entry(Seq("library", "foo", "bar"), "baz")
      )
      assert(GlobalRegistry.get.toSet == expected)
    }
  }

  test("Library.register will provide a roster which lets you update params") {
    val simple = new SimpleRegistry
    GlobalRegistry.withRegistry(simple) {
      val maybeRoster = Library.register("foo", Map("bar" -> "baz"))

      val old = Set(
        Entry(Seq("library", "foo"), Library.Registered),
        Entry(Seq("library", "foo", "bar"), "baz")
      )
      assert(GlobalRegistry.get.toSet == old)

      assert(maybeRoster.isDefined)
      // guaranteed it's a Some at this point
      val roster = maybeRoster.get

      // good update
      assert(roster("bar") = "qux")
      val fresh = Set(
        Entry(Seq("library", "foo"), Library.Registered),
        Entry(Seq("library", "foo", "bar"), "qux")
      )
      assert(GlobalRegistry.get.toSet == fresh)

      // bad update
      assert(!(roster("baz") = "quux"))
      assert(GlobalRegistry.get.toSet == fresh)
    }
  }
}
