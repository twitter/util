package com.twitter.app

import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

object MyGlobalFlag extends GlobalFlag("a test flag", "a global test flag")

@RunWith(classOf[JUnitRunner])
class FlagTest extends FunSuite {
  test("Flaggable: parse booleans") {
    assert(Flaggable.ofBoolean.parse("true"))
    assert(!Flaggable.ofBoolean.parse("false"))
    
    intercept[Throwable] { Flaggable.ofBoolean.parse("") }
    intercept[Throwable] { Flaggable.ofBoolean.parse("gibberish") }
  }
  
  test("Flaggable: parse strings") {
    assert(Flaggable.ofString.parse("blah") === "blah")
  }


  test("Flaggable: parse/show inet addresses") {
    val local8080 = Flaggable.ofInetSocketAddress.parse(":8080")
    assert(local8080.getAddress.isAnyLocalAddress)
    assert(local8080.getPort === 8080)

    val ip8080 = Flaggable.ofInetSocketAddress.parse("141.211.133.111:8080")
    assert(ip8080.getHostName === "141.211.133.111")
    assert(ip8080.getPort === 8080)

    assert(Flaggable.ofInetSocketAddress.show(local8080) === ":8080")
    assert(Flaggable.ofInetSocketAddress.show(ip8080) ==="141.211.133.111:8080")
  }

  test("Flaggable: parse seqs") {
    assert(Flaggable.ofSeq[Int].parse("1,2,3,4") === Seq(1,2,3,4))
  }

  test("Flaggable: parse tuples") {
    assert(Flaggable.ofTuple[Int, String].parse("1,hello") === (1, "hello"))
    intercept[IllegalArgumentException] { Flaggable.ofTuple[Int, String].parse("1") }
  }
  
  class Ctx {
    val flag = new Flags("test")
    val fooFlag = flag("foo", 123, "The foo value")
    val barFlag = flag("bar", "okay", "The bar value")
  }

  test("Flag: defaults") {
    val ctx = new Ctx
    import ctx._
    assert(fooFlag() === 123)
    assert(barFlag() === "okay")
  }

  test("Flag: add and parse flags") {
    val ctx = new Ctx
    import ctx._
    assert(flag.parse(Array("-foo", "973", "-bar", "hello there")).isEmpty)
    assert(fooFlag() === 973)
    assert(barFlag() === "hello there")
  }
  
  class Bctx extends Ctx {
    val yesFlag = flag("yes", false, "Just say yes.")
  }

  test("Boolean: default") {
    val ctx = new Bctx
    import ctx._
    assert(!yesFlag())
  }

  test("Boolean: -yes") {
    val ctx = new Bctx
    import ctx._
    assert(flag.parse(Array("-yes")).isEmpty)
    assert(yesFlag())
  }

  test("Boolean: -yes=true") {
    val ctx = new Bctx
    import ctx._
    assert(flag.parse(Array("-yes=true")).isEmpty)
    assert(yesFlag())
  }

  test("Boolean: -yes=false") {
    val ctx = new Bctx
    import ctx._
    assert(flag.parse(Array("-yes=false")).isEmpty)
    assert(!yesFlag())
  }

  test("Flag: handle remainders (sequential)") {
    val ctx = new Ctx
    import ctx._
    assert(flag.parse(Array("-foo", "333", "arg0", "arg1")) === Seq("arg0", "arg1"))
  }

  test("Flag: handle remainders (interpspersed)") {
    val ctx = new Ctx
    import ctx._
    assert(flag.parse(Array("arg0", "-foo", "333", "arg1")) === Seq("arg0", "arg1"))
  }

  test("Flag: give nice parse errors") {
    val ctx = new Ctx
    import ctx._
    val thr = intercept[Exception] { flag.parse(Array("-foo", "blah")) }
  }

  test("Flag: handle -help") {
    val ctx = new Ctx
    import ctx._
    intercept[FlagUsageError] { flag.parse(Array("-help")) }
  }

  test("Flag: mandatory flag without argument") {
    val ctx = new Ctx
    import ctx._
    val thr = intercept[FlagParseException] { flag.parse(Array("-foo")) }
  }

  test("Flag: undefined") {
    val ctx = new Ctx
    import ctx._
    val thr = intercept[FlagParseException] { flag.parse(Array("-undefined")) }
    assert(flag.parse(Array("-undefined"), true) === Seq("-undefined"))
  }

  test("GlobalFlag") {
    assert(MyGlobalFlag() === "a test flag")
    val flag = new Flags("my", includeGlobal=true)
    flag.parse(Array("-com.twitter.app.MyGlobalFlag", "okay"))
    assert(MyGlobalFlag() === "okay")
    System.setProperty("com.twitter.app.MyGlobalFlag", "not okay")
    assert(MyGlobalFlag() === "okay")
    MyGlobalFlag.reset()
    assert(MyGlobalFlag() === "not okay")
  }
}
