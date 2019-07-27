package com.twitter.app

import java.net.URLClassLoader
import org.scalatest.FunSuite

object MyGlobalFlag extends GlobalFlag[String]("a test flag", "a global test flag")
object MyGlobalFlagNoDefault extends GlobalFlag[Int]("a global test flag with no default")
object MyGlobalBooleanFlag extends GlobalFlag[Boolean](false, "a boolean flag")

class GlobalFlagTest extends FunSuite {
  val flagSet = Set(MyGlobalFlag, MyGlobalBooleanFlag, MyGlobalFlagNoDefault, PackageObjectTest)

  test("GlobalFlag.get") {
    assert(MyGlobalBooleanFlag.get.isEmpty)
    assert(MyGlobalFlagNoDefault.get.isEmpty)

    assert(MyGlobalFlag.get.isEmpty)
    val flag = new Flags("my", includeGlobal = true)
    try {
      flag.parseArgs(Array("-com.twitter.app.MyGlobalFlag", "supplied"))
      assert(MyGlobalFlag.get.contains("supplied"))
    } finally {
      MyGlobalFlag.reset()
    }
  }

  test("GlobalFlag.getWithDefault") {
    assert(MyGlobalBooleanFlag.getWithDefault.contains(false))
    assert(MyGlobalFlagNoDefault.getWithDefault.isEmpty)

    assert(MyGlobalFlag.getWithDefault.contains("a test flag"))
    val flag = new Flags("my", includeGlobal = true)
    try {
      flag.parseArgs(Array("-com.twitter.app.MyGlobalFlag", "supplied"))
      assert(MyGlobalFlag.getWithDefault.contains("supplied"))
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
    assert(!MyGlobalBooleanFlag())
    val flag = new Flags("my", includeGlobal = true)
    flag.parseArgs(Array("-com.twitter.app.MyGlobalBooleanFlag"))
    assert(MyGlobalBooleanFlag())
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

  test("GlobalFlag.getAll") {
    val mockClassLoader = new MockClassLoader(getClass.getClassLoader.asInstanceOf[URLClassLoader])
    val flags = GlobalFlag.getAll(mockClassLoader)
    assert(flags.toSet == flagSet)
    assert(flags.exists(_.help.equals("a package object test flag")))
  }

  private class MockClassLoader(realClassLoader: URLClassLoader)
      extends URLClassLoader(realClassLoader.getURLs) {
    private val isValidClassName = (className: String) =>
      flagSet.map(_.getClass.getName)
        .contains(className)

    override def loadClass(name: String): Class[_] =
      if (isValidClassName(name)) realClassLoader.loadClass(name) else null
  }

}
