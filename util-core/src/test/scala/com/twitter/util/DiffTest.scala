package com.twitter.util

import org.scalatest.FunSuite
import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class DiffTest extends FunSuite with ScalaCheckDrivenPropertyChecks {
  val f: Int => String = _.toString

  test("Diffable.ofSet") {
    forAll(arbitrary[Set[Int]], arbitrary[Set[Int]]) { (a, b) =>
      assert(Diffable.diff(a, b).patch(a) == b)
    }

    forAll(arbitrary[Set[Int]], arbitrary[Set[Int]]) { (a, b) =>
      val diff = Diffable.diff(a, b)
      assert(diff.map(f).patch(a.map(f)) == b.map(f))
    }
  }

  test("Diffable.ofSeq") {
    forAll(arbitrary[Seq[Int]], arbitrary[Seq[Int]]) { (a, b) =>
      assert(Diffable.diff(a, b).patch(a) == b)
    }

    forAll(arbitrary[Seq[Int]], arbitrary[Seq[Int]]) { (a, b) =>
      val diff = Diffable.diff(a, b)
      assert(diff.map(f).patch(a.map(f)) == b.map(f))
    }
  }
}
