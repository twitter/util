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
}
