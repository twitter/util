package com.twitter.util.registry

import java.lang.{Character => JCharacter}
import org.scalatest.funsuite.AnyFunSuite

abstract class RegistryTest extends AnyFunSuite {
  def mkRegistry(): Registry
  def name: String

  test(s"$name can insert a key/value pair and then read it") {
    val registry = mkRegistry()
    registry.put(Seq("foo"), "bar")
    assert(registry.toSet == Set(Entry(Seq("foo"), "bar")))
  }

  test(s"$name's iterator is not affected by adding an element") {
    val registry = mkRegistry()
    registry.put(Seq("foo"), "bar")
    val iter = registry.iterator
    registry.put(Seq("foo"), "baz")
    assert(iter.next() == Entry(Seq("foo"), "bar"))
    assert(!iter.hasNext)
  }

  test(s"$name can overwrite old element") {
    val registry = mkRegistry()
    registry.put(Seq("foo"), "bar")
    registry.put(Seq("foo"), "baz")
    assert(registry.toSet == Set(Entry(Seq("foo"), "baz")))
  }

  test(s"$name can return the old element when replacing") {
    val registry = mkRegistry()
    registry.put(Seq("foo"), "bar")
    assert(registry.put(Seq("foo"), "baz") == Some("bar"))
    assert(registry.toSet == Set(Entry(Seq("foo"), "baz")))
  }

  test(s"$name can remove old element") {
    val registry = mkRegistry()
    registry.put(Seq("foo"), "bar")
    assert(registry.remove(Seq("foo")) == Some("bar"))
    assert(registry.toSet == Set.empty)
  }

  test(s"$name can remove nothing") {
    val registry = mkRegistry()
    assert(registry.remove(Seq("foo")) == None)
    assert(registry.toSet == Set.empty)
  }

  test(s"$name can support multiple elements") {
    val registry = mkRegistry()
    registry.put(Seq("foo"), "bar")
    registry.put(Seq("baz"), "qux")
    assert(registry.toSet == Set(Entry(Seq("foo"), "bar"), Entry(Seq("baz"), "qux")))
  }

  test(s"$name can support nontrivial keys") {
    val registry = mkRegistry()
    registry.put(Seq("foo", "bar", "baz"), "qux")
    assert(registry.toSet == Set(Entry(Seq("foo", "bar", "baz"), "qux")))
  }

  test(s"$name can support empty keys") {
    val registry = mkRegistry()
    registry.put(Seq(), "qux")
    assert(registry.toSet == Set(Entry(Seq(), "qux")))
  }

  test(s"$name can sanitize bad values") {
    val registry = mkRegistry()
    registry.put(Seq("foo"), "q☃ux")
    assert(registry.toSet == Set(Entry(Seq("foo"), "qux")))
  }

  test(s"$name sanitizes ASCII DEL and US characters") {
    val registry = mkRegistry()
    // ASCII US and DEL are the lower and upper control characters
    // space and tilde are the lower and upper permitted printable chars
    registry.put(Seq("foo"), "us" + 0x1f.toChar + " ~del" + 0x7f.toChar)
    assert(registry.toSet == Set(Entry(Seq("foo"), "us ~del")))
  }

  test(s"$name can sanitize bad keys") {
    val registry = mkRegistry()
    registry.put(Seq("fo☃o", s"bar${JCharacter.toString(31)}"), "qux")
    assert(registry.toSet == Set(Entry(Seq("foo", "bar"), "qux")))
  }

  test(s"$name can support keys that are subsequences of other keys") {
    val registry = mkRegistry()
    registry.put(Seq("foo"), "bar")
    registry.put(Seq("foo", "baz"), "qux")
    assert(registry.toSet == Set(Entry(Seq("foo"), "bar"), Entry(Seq("foo", "baz"), "qux")))
  }

  test(s"$name can support varargs API") {
    val registry = mkRegistry()
    registry.put("foo", "bar", "baz")
    registry.put("qux")
    assert(registry.toSet == Set(Entry(Seq("foo", "bar"), "baz"), Entry(Seq(), "qux")))
  }
}
