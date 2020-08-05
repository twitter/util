package com.twitter.logging


import com.twitter.util.StorageUnit
import org.scalatest.funsuite.AnyFunSuite

class PolicyTest extends AnyFunSuite {
  import Policy._

  test("Policy.parse: never") {
    assert(parse("never") == Never)
  }

  test("Policy.parse: hourly") {
    assert(parse("hourly") == Hourly)
  }

  test("Policy.parse: daily") {
    assert(parse("daily") == Daily)
  }

  test("Policy.parse: sighup") {
    assert(parse("sighup") == SigHup)
  }

  test("Policy.parse: weekly") {
    assert(parse("weekly(3)") == Weekly(3))
  }

  test("Policy.parse: maxsize") {
    val size = "3.megabytes"
    assert(parse(size) == MaxSize(StorageUnit.parse(size)))
  }

  test("Policy.parse: should be case-insensitive") {
    assert(parse("DAily") == Daily)
    assert(parse("weEkLy(3)") == Weekly(3))
    assert(parse("3.meGabYteS") == MaxSize(StorageUnit.parse("3.megabytes")))
  }
}
