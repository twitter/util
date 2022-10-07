package com.twitter.util

import java.util.Locale
import org.scalatest.funsuite.AnyFunSuite

class TwitterDateFormatTest extends AnyFunSuite {
  test("TwitterDateFormat should disallow Y without w") {
    intercept[IllegalArgumentException] {
      TwitterDateFormat("YYYYMMDD")
    }
    intercept[IllegalArgumentException] {
      TwitterDateFormat("YMD", Locale.GERMAN)
    }
  }

  test("TwitterDateFormat should allow Y with w") {
    TwitterDateFormat("YYYYww")
  }

  test("TwitterDateFormat should allow Y when quoted") {
    TwitterDateFormat("yyyy 'Year'")
  }

  test("TwitterDateFormat should stripSingleQuoted") {
    import TwitterDateFormat._
    assert(stripSingleQuoted("") == "")
    assert(stripSingleQuoted("YYYY") == "YYYY")
    assert(stripSingleQuoted("''") == "")
    assert(stripSingleQuoted("'abc'") == "")
    assert(stripSingleQuoted("x'abc'") == "x")
    assert(stripSingleQuoted("'abc'x") == "x")
    assert(stripSingleQuoted("'abc'def'ghi'") == "def")

    intercept[IllegalArgumentException] {
      stripSingleQuoted("'abc")
    }
    intercept[IllegalArgumentException] {
      stripSingleQuoted("'abc'def'ghi")
    }
  }
}
