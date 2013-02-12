package com.twitter.jvm

import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import com.twitter.conversions.storage._

@RunWith(classOf[JUnitRunner])
class JvmTest extends FunSuite {
  test("PoolState.-") {
    val a = PoolState(1, 1000.kilobytes, 500.kilobytes)
    val b = PoolState(2, 1000.kilobytes, 200.kilobytes)
    val c = PoolState(3, 1000.kilobytes, 600.kilobytes)
    assert(a - a == PoolState(0, 1000.kilobytes, 0.bytes))
    assert(b - a == PoolState(1, 1000.kilobytes, 500.kilobytes+200.kilobytes))
    assert(c - a == PoolState(2, 1000.kilobytes, 1000.kilobytes+500.kilobytes+600.kilobytes))
  }
}
