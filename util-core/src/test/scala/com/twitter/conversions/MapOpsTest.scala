package com.twitter.conversions

import com.twitter.conversions.MapOps._
import org.scalatest.funsuite.AnyFunSuite
import scala.collection.SortedMap

class MapOpsTest extends AnyFunSuite {

  test("RichMap#mapKeys") {
    val mappedKeys = Map(1 -> "a") mapKeys { _.toString }
    assert(mappedKeys == Map("1" -> "a"))
  }

  test("RichMap#invert simple") {
    val invertedMap = Map(1 -> "a").invert
    assert(invertedMap == Map("a" -> Iterable(1)))
  }

  test("RichMap#invert complex") {
    val complexInverted = Map(1 -> "a", 2 -> "a", 3 -> "b").invert
    assert(complexInverted == Map("a" -> Iterable(1, 2), "b" -> Iterable(3)))
  }

  test("RichMap#invertSingleValue") {
    val singleValueInverted = Map(1 -> "a", 2 -> "a", 3 -> "b").invertSingleValue
    assert(singleValueInverted == Map("a" -> 2, "b" -> 3))
  }

  test("RichMap#filterValues") {
    val filteredMap = Map(1 -> "a", 2 -> "a", 3 -> "b") filterValues { _ == "b" }
    assert(filteredMap == Map(3 -> "b"))
  }

  test("RichMap#filterNotValues") {
    val filteredMap = Map(1 -> "a", 2 -> "a", 3 -> "b") filterNotValues { _ == "b" }
    assert(filteredMap == Map(1 -> "a", 2 -> "a"))
  }

  test("RichMap#filterNotKeys") {
    val filteredMap = Map(1 -> "a", 2 -> "a", 3 -> "b") filterNotKeys { _ == 3 }
    assert(filteredMap == Map(1 -> "a", 2 -> "a"))
  }

  test("RichMap#toSortedMap") {
    val sortedMap = Map(6 -> "f", 2 -> "b", 1 -> "a").toSortedMap
    assert(sortedMap == SortedMap(1 -> "a", 2 -> "b", 6 -> "f"))
  }

}
