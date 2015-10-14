package com.twitter.util.lint

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RuleTest extends FunSuite {

  private def withName(name: String): Rule =
    Rule(Category.Performance, name, "descriptive description") { Nil }

  private def idOfNamed(name: String): String =
    withName(name).id

  test("id") {
    assert("abc" == idOfNamed("abc"))
    assert("abc" == idOfNamed("ABC"))
    assert("abc-def" == idOfNamed("Abc Def"))
  }

}
