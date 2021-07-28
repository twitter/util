package com.twitter.conversions

import com.twitter.conversions.SeqOps._
import org.scalatest.funsuite.AnyFunSuite
import scala.collection.compat.immutable.LazyList

class SeqOpsTest extends AnyFunSuite {

  test("RichSeq#createMap") {
    val map = Seq("a", "and") createMap (_.size, _.toUpperCase)
    assert(map == Map(1 -> "A", 3 -> "AND"))
  }

  test("RichSeq#groupBySingle chooses last element in seq when key collision occurs") {
    val map = Seq("a", "and", "the") groupBySingleValue { _.size }
    assert(map == Map(3 -> "the", 1 -> "a"))
  }

  test("RichSeq#findItemAfter") {
    assert(Seq(1, 2, 3).findItemAfter(1) == Some(2))
    assert(Seq(1, 2, 3).findItemAfter(2) == Some(3))
    assert(Seq(1, 2, 3).findItemAfter(3) == None)
    assert(Seq(1, 2, 3).findItemAfter(4) == None)
    assert(Seq(1, 2, 3).findItemAfter(5) == None)
    assert(Seq[Int]().findItemAfter(5) == None)

    assert(LazyList(1, 2, 3).findItemAfter(1) == Some(2))
    assert(LazyList[Int]().findItemAfter(5) == None)

    assert(Vector(1, 2, 3).findItemAfter(1) == Some(2))
    assert(Vector[Int]().findItemAfter(5) == None)
  }

  test("RichSeq#foreachPartial") {
    var numStrings = 0
    Seq("a", 1) foreachPartial {
      case str: String =>
        numStrings += 1
    }
    assert(numStrings == 1)
  }

}
