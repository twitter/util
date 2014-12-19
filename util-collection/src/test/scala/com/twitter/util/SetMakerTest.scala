package com.twitter.util

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SetMakerTest extends FunSuite {
  test("SetMaker") {
    val s1 = SetMaker[Int](_.initialCapacity(23))
    s1 += 22
    s1 += 33
    s1 -= 22

    assert(s1.contains(33))
    assert(!s1.contains(22))
  }
}
