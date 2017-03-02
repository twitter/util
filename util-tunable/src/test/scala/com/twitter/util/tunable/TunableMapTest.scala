package com.twitter.util.tunable

import org.scalatest.FunSuite

class MutableTest extends FunSuite {

  test("map is initially empty") {
    assert(TunableMap.newMutable().entries.size == 0)
  }

  test("Getting a Tunable not yet in the map returns a Tunable that produces None when applied") {
    val map = TunableMap.newMutable()
    val key = TunableMap.Key[String]("key")
    val tunable = map(key)
    assert(tunable() == None)
  }

  test("Putting an id and value and getting the tunable from the map returns the Tunable with " +
    "that id and value") {
    val id = "key"
    val value = "value"

    val map = TunableMap.newMutable()
    val key = map.put(id, value)
    val tunable = map(key)
    assert(tunable() == Some(value))
  }

  test("Getting a tunable with a Key whose subclass is that of the matching Tunable in the map " +
    "produces the Tunable") {

    class Animal
    class Cat extends Animal

    val id = "key"
    val value = new Cat

    val map = TunableMap.newMutable()
    map.put(id, value)
    val tunable = map(TunableMap.Key[Animal](id))
    assert(tunable() == Some(value))
    assert(tunable().isInstanceOf[Option[Animal]])
  }

  test("Can create keys to retrieve tunables") {
    val id = "key"
    val value = 5
    val key = TunableMap.Key[Int](id)

    val map = TunableMap.newMutable()
    map.put(id, value)
    val tunable = map(key)
    assert(tunable() == Some(value))
  }

  test("Retrieving a present tunable does not create a new one") {
    val id = "key"
    val value = 5
    val key = TunableMap.Key[Int](id)

    val map = TunableMap.newMutable()
    assert(map.entries.size == 0)
    map.put(id, value)
    assert(map.entries.size == 1)
    assert(map(key) eq map(key))
    assert(map.entries.size == 1)
  }

  test("Retrieving a Tunable with a Key of the wrong type will throw a ClassCastException") {
    val id = "key"
    val value = 5
    val key = TunableMap.Key[String](id)

    val map = TunableMap.newMutable()
    map.put(id, value)

    intercept[ClassCastException] {
      map(key)
    }
  }

  test("Putting the value for an id already in the map updates the Tunable ") {
    val id = "key"
    val value1 = "value1"
    val value2 = "value2"

    val map = TunableMap.newMutable()

    val key = map.put(id, value1)

    val tunable = map(key)
    assert(tunable() == Some(value1))

    map.put(id, value2)
    assert(tunable() == Some(value2))
  }

  test("Putting the value of the wrong type for an id already in the map throws a CCE") {
    val id = "key"
    val value1 = "value1"
    val value2 = 2

    val map = TunableMap.newMutable()

    val key = map.put(id, value1)

    val tunable = map(key)
    assert(tunable() == Some(value1))

    intercept[ClassCastException] {
      map.put(id, value2)
    }
  }

  test("Clearing a key from the map clears the Tunable") {
    val id = "key"
    val value = "value"

    val map = TunableMap.newMutable()
    val key = map.put(id, value)

    val tunable = map(key)
    assert(tunable() == Some(value))

    map.clear(key)
    assert(tunable() == None)
  }

  test("entries returns TunableMap.Entry for Tunables with values") {
    val map = TunableMap.newMutable()
    val key1 = map.put("id1", "value1")
    val key2 = map.put("id2", "value2")
    val toClear = map.put("id3", "value3")

    map.clear(toClear)

    assert(map.entries.size == 2)
    assert(map.entries.find(_.key == key1).head.value == "value1")
    assert(map.entries.find(_.key == key2).head.value == "value2")
  }

  test("replace updates the initially empty TunableMap") {
    val map, replacement = TunableMap.newMutable()

    val id = "key"
    val value = "value"
    replacement.put(id, value)

    val tunable = map(TunableMap.Key[String](id))
    assert(tunable() == None)

    map.replace(replacement)
    assert(map.entries.size == 1)
    assert(tunable() == Some(value))
  }

  test("replace clears Tunables for ids no longer in the map") {
    val map, replacement = TunableMap.newMutable()

    val id = "key"
    val value = "value"

    map.put(id, value)
    assert(map.entries.size == 1)
    val tunable = map(TunableMap.Key[String](id))
    assert(tunable() == Some(value))

    map.replace(replacement)
    assert(map.entries.size == 0)
    assert(tunable() == None)
  }

  test("replace adds new tunables to the map") {
    val map, replacement1, replacement2 = TunableMap.newMutable()

    val id1 = "key1"
    val value1 = "value1"

    replacement1.put(id1, value1)
    map.replace(replacement1)
    val tunable = map(TunableMap.Key[String](id1))
    assert(tunable() == Some(value1))

    val id2 = "key2"
    val value2 = "value2"

    replacement2.put(id2, value2)
    map.replace(replacement2)
    assert(tunable() == None)
    assert(map(TunableMap.Key[String](id2))() == Some(value2))
    assert(map.entries.size == 1)
  }

  test("replace updates existing tunables in the map") {
    val map, replacement1, replacement2 = TunableMap.newMutable()

    val id = "key"
    val value1 = "value1"

    replacement1.put(id, value1)
    map.replace(replacement1)
    val tunable = map(TunableMap.Key[String](id))
    assert(tunable() == Some(value1))

    val value2 = "value2"

    replacement2.put(id, value2)
    map.replace(replacement2)
    assert(tunable() == Some(value2))
    assert(map.entries.size == 1)
  }
}

class NullTunableMapTest extends FunSuite {

  test("NullTunableMap returns Tunable.None") {
    val nullTunableMap = NullTunableMap
    val key = TunableMap.Key[String]("foo")
    assert(nullTunableMap(key)() == None)
  }

  test("NullTunableMap does not grow in size when tunables accessed") {
    val nullTunableMap = NullTunableMap
    assert(nullTunableMap.entries.size == 0)
    val key = TunableMap.Key[String]("foo")
    assert(nullTunableMap(key)() == None)
    assert(nullTunableMap.entries.size == 0)
  }
}
