package com.twitter.util

import java.util.Locale

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TwitterDateFormatTest extends WordSpec {
  "TwitterDateFormat" should {
    "disallow Y without w" in {
      intercept[IllegalArgumentException] {
        TwitterDateFormat("YYYYMMDD")
      }
      intercept[IllegalArgumentException] {
        TwitterDateFormat("YMD", Locale.GERMAN)
      }
    }

    "allow Y with w" in {
      TwitterDateFormat("YYYYww")
    }

    "allow Y when quoted" in {
      TwitterDateFormat("yyyy 'Year'")
    }

    "stripSingleQuoted" in {
      import TwitterDateFormat._
      assert(stripSingleQuoted("") === "")
      assert(stripSingleQuoted("YYYY") === "YYYY")
      assert(stripSingleQuoted("''") === "")
      assert(stripSingleQuoted("'abc'") === "")
      assert(stripSingleQuoted("x'abc'") === "x")
      assert(stripSingleQuoted("'abc'x") === "x")
      assert(stripSingleQuoted("'abc'def'ghi'") === "def")

      intercept[IllegalArgumentException] {
        stripSingleQuoted("'abc")
      }
      intercept[IllegalArgumentException] {
        stripSingleQuoted("'abc'def'ghi")
      }
    }
  }
}
