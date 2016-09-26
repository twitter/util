package com.twitter.app

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import scala.collection.mutable.Buffer

@RunWith(classOf[JUnitRunner])
class FlagTest extends FunSuite {

  class Ctx(failFastUntilParsed: Boolean = false) {
    val flag = new Flags("test", includeGlobal = false, failFastUntilParsed)
    val fooFlag = flag("foo", 123, "The foo value")
    val barFlag = flag("bar", "okay", "The bar value")
  }

  test("Flag: defaults") {
    val ctx = new Ctx
    import ctx._

    flag.finishParsing()
    assert(fooFlag() == 123)
    assert(barFlag() == "okay")
  }

  test("Flag: override a flag") {
    val flag = new Flags("test")
    flag("foo", 1, "")
    flag("foo", 2, "")
    val allFlags = flag.getAll().toSet

    flag.finishParsing()
    assert(!allFlags.exists(_() == 1), "original flag was not overridden")
    assert(allFlags.exists(_() == 2), "overriding flag was not present in flags set")
  }

  test("Flag: let") {
    def current: Boolean = MyGlobalBooleanFlag()

    // track the order the blocks execute and that they only execute once
    var buf = Buffer[Int]()

    // make sure they stack properly
    assert(!current)
    MyGlobalBooleanFlag.let(true) {
      buf += 1
      assert(current)
      MyGlobalBooleanFlag.let(false) {
        buf += 2
        assert(!current)
      }
      buf += 3
      assert(current)
    }
    assert(!current)

    assert(buf == Seq(1, 2, 3))
  }

  test("Flag: letClear") {
    // track the order the blocks execute and that they only execute once
    var buf = Buffer[Int]()

    MyGlobalBooleanFlag.let(true) {
      assert(MyGlobalBooleanFlag.isDefined)
      buf += 1
      MyGlobalBooleanFlag.letClear {
        assert(!MyGlobalBooleanFlag.isDefined)
        buf += 2
      }
      assert(MyGlobalBooleanFlag.isDefined)
      buf += 3
    }

    assert(buf == Seq(1, 2, 3))
  }

  class Dctx extends Ctx {
    val quuxFlag = flag[Int]("quux", "an int")
  }

  test("Flag: no default usage") {
    val ctx = new Dctx
    import ctx._
    assert(quuxFlag.usageString == "  -quux='Int': an int")
  }

  private class GetCtx {
    private val flags = new Flags("test")
    val withDefault = flags[Int]("f1", 1, "an f1")
    val noDefault = flags[Int]("f2", "an f2")
    val noDefaultAndSupplied = flags[Int]("f3", "an f3")

    assert(flags.parseArgs(Array("-f3=3")) == Flags.Ok(Nil))
  }

  test("Flag.get") {
    val ctx = new GetCtx()
    import ctx._

    assert(withDefault.get == None)
    assert(noDefault.get == None)
    assert(noDefaultAndSupplied.get == Some(3))
  }

  test("Flag.getWithDefault") {
    val ctx = new GetCtx()
    import ctx._

    assert(withDefault.getWithDefault == Some(1))
    assert(noDefault.getWithDefault == None)
    assert(noDefaultAndSupplied.getWithDefault == Some(3))
  }

}
