package com.twitter.io

import com.twitter.util.Activity
import org.scalatest.funsuite.AnyFunSuite

class ActivitySourceTest extends AnyFunSuite {

  val ok: ActivitySource[String] = new ActivitySource[String] {
    def get(varName: String) = Activity.value(varName)
  }

  val failed: ActivitySource[Nothing] = new ActivitySource[Nothing] {
    def get(varName: String) = Activity.exception(new Exception(varName))
  }

  test("ActivitySource.orElse") {
    val a = failed.orElse(ok)
    val b = ok.orElse(failed)

    assert("42" == a.get("42").sample())
    assert("42" == b.get("42").sample())
  }

}

object ActivitySourceTest {

  def bufToString(buf: Buf): String = buf match {
    case Buf.Utf8(s) => s
  }

}
