package com.twitter.conversions

import com.twitter.conversions.TupleOps._
import org.scalatest.funsuite.AnyFunSuite
import scala.collection.SortedMap

class TupleOpsTest extends AnyFunSuite {

  val tuples = Seq(1 -> "bob", 2 -> "sally")

  test("RichTuple#toKeys") {
    assert(tuples.toKeys == Seq(1, 2))
  }

  test("RichTuple#toKeySet") {
    assert(tuples.toKeySet == Set(1, 2))
  }

  test("RichTuple#toValues") {
    assert(tuples.toValues == Seq("bob", "sally"))
  }

  test("RichTuple#mapValues") {
    assert(tuples.mapValues { _.length } == Seq(1 -> 3, 2 -> 5))
  }

  test("RichTuple#groupByKey") {
    val multiTuples = Seq(1 -> "a", 1 -> "b", 2 -> "ab")
    assert(multiTuples.groupByKey == Map(2 -> Seq("ab"), 1 -> Seq("a", "b")))
  }

  test("RichTuple#groupByKeyAndReduce") {
    val multiTuples = Seq(1 -> 5, 1 -> 6, 2 -> 7)
    assert(multiTuples.groupByKeyAndReduce(_ + _) == Map(2 -> 7, 1 -> 11))
  }

  test("RichTuple#sortByKey") {
    val unorderedTuples = Seq(2 -> "sally", 1 -> "bob")
    assert(unorderedTuples.sortByKey == Seq(1 -> "bob", 2 -> "sally"))
  }

  test("RichTuple#toSortedMap") {
    val unorderedTuples = Seq(2 -> "sally", 1 -> "bob")
    assert(unorderedTuples.toSortedMap == SortedMap(1 -> "bob", 2 -> "sally"))
  }

}
