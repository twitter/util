package com.twitter.app

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

object MyGlobalFlag extends GlobalFlag[String]("a test flag", "a global test flag")
object MyGlobalFlagNoDefault extends GlobalFlag[Int]("a global test flag with no default")
object MyGlobalBooleanFlag extends GlobalFlag[Boolean](false, "a boolean flag")

@RunWith(classOf[JUnitRunner])
class GlobalFlagTest extends FunSuite {

  test("GlobalFlag.get") {
    assert(MyGlobalBooleanFlag.get == None)
    assert(MyGlobalFlagNoDefault.get == None)

    assert(MyGlobalFlag.get == None)
    val flag = new Flags("my", includeGlobal = true)
    try {
      flag.parseArgs(Array("-com.twitter.app.MyGlobalFlag", "supplied"))
      assert(MyGlobalFlag.get == Some("supplied"))
    } finally {
      MyGlobalFlag.reset()
    }
  }

  test("GlobalFlag.getWithDefault") {
    assert(MyGlobalBooleanFlag.getWithDefault == Some(false))
    assert(MyGlobalFlagNoDefault.getWithDefault == None)

    assert(MyGlobalFlag.getWithDefault == Some("a test flag"))
    val flag = new Flags("my", includeGlobal = true)
    try {
      flag.parseArgs(Array("-com.twitter.app.MyGlobalFlag", "supplied"))
      assert(MyGlobalFlag.getWithDefault == Some("supplied"))
    } finally {
      MyGlobalFlag.reset()
    }
  }

  test("GlobalFlag: no default usage") {
    assert(
      MyGlobalFlagNoDefault.usageString ==
        "  -com.twitter.app.MyGlobalFlagNoDefault='Int': a global test flag with no default"
    )
  }

  test("GlobalFlag: implicit value of true for booleans") {
    assert(MyGlobalBooleanFlag() == false)
    val flag = new Flags("my", includeGlobal = true)
    flag.parseArgs(Array("-com.twitter.app.MyGlobalBooleanFlag"))
    assert(MyGlobalBooleanFlag() == true)
    MyGlobalBooleanFlag.reset()
  }

  test("GlobalFlag") {
    assert(MyGlobalFlag() == "a test flag")
    val flag = new Flags("my", includeGlobal = true)
    flag.parseArgs(Array("-com.twitter.app.MyGlobalFlag", "okay"))
    assert(MyGlobalFlag() == "okay")
    MyGlobalFlag.reset()
    assert(MyGlobalFlag() == "a test flag")
    MyGlobalFlag.let("not okay") {
      assert(MyGlobalFlag() == "not okay")
    }
  }

}
