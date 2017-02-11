package com.twitter.util.tunable

import org.scalatest.FunSuite

class MutableTest extends FunSuite {

  test("map is initially empty") {
    assert(TunableMap.newMutable().size == 0)
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
    assert(map.size == 0)
    map.put(id, value)
    assert(map.size == 1)
    assert(map(key) eq map(key))
    assert(map.size == 1)
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
}