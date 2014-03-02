package com.twitter.app

import com.twitter.util.RandomSocket
import java.net.InetSocketAddress
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

object MyGlobalFlag extends GlobalFlag("a test flag", "a global test flag")
object MyGlobalFlagNoDefault extends GlobalFlag[Int]("a global test flag with no default")

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
    val port = RandomSocket.nextPort
    val local = Flaggable.ofInetSocketAddress.parse(":" + port)
    assert(local.getAddress.isAnyLocalAddress)
    assert(local.getPort === port)

    val ip = "141.211.133.111"
    val expectedRemote = new InetSocketAddress(ip, port)
    val remote = Flaggable.ofInetSocketAddress.parse(ip + ":" + port)

    assert(remote.getHostName === remote.getHostName)
    assert(remote.getPort === port)

    assert(Flaggable.ofInetSocketAddress.show(local) === ":" + port)
    assert(Flaggable.ofInetSocketAddress.show(remote) === remote.getHostName + ":" + port)
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

  test("Flag: override a flag") {
    val flag = new Flags("test")
    val flag1 = flag("foo", 1, "")
    val flag2 = flag("foo", 2, "")
    val allFlags = flag.getAll().toSet

    assert(!allFlags.exists(_() == 1), "original flag was not overridden")
    assert(allFlags.exists(_() == 2), "overriding flag was not present in flags set")
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

  test("Boolean: -yes ARG") {
    val ctx = new Bctx
    import ctx._
    val rem = flag.parse(Array("-yes", "ARG"))
    assert(yesFlag())
    assert(rem === Seq("ARG"))
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

  test("Flag: stop parsing at '--'") {
    val ctx = new Ctx
    import ctx._
    assert(flag.parse(Array("arg0", "--", "-foo", "333")) === Seq("arg0", "-foo", "333"))
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

  class Dctx extends Ctx {
    val quuxFlag = flag[Int]("quux", "an int")
  }

  test("Flag: no default usage") {
    val ctx = new Dctx
    import ctx._
    assert(quuxFlag.usageString === "  -quux=<Int>: an int")
  }

  test("GlobalFlag: no default usage") {
    assert(MyGlobalFlagNoDefault.usageString ===
      "  -com.twitter.app.MyGlobalFlagNoDefault=<Int>: a global test flag with no default")
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
    System.clearProperty("com.twitter.app.MyGlobalFlag")
  }

  test("formatFlagValues") {

    val flagWithGlobal = new Flags("my", includeGlobal = true)
    flagWithGlobal("unset.local.flag", "a flag!", "this is a local flag")
    flagWithGlobal("set.local.flag", "a flag!", "this is a local flag")
    flagWithGlobal("flag.with.single.quote", "i'm so cool", "why would you do this?")
    flagWithGlobal.parse(Array("-set.local.flag=hi"))

    val flagWithoutGlobal = new Flags("my", includeGlobal = false)
    flagWithoutGlobal("unset.local.flag", "a flag!", "this is a local flag")
    flagWithoutGlobal("set.local.flag", "a flag!", "this is a local flag")
    flagWithoutGlobal("flag.with.single.quote", "i'm so cool", "why would you do this?")
    flagWithoutGlobal.parse(Array("-set.local.flag=hi"))


    val localOnly =
      """|Set flags:
         |-set.local.flag='hi' \
         |Unset flags:
         |-flag.with.single.quote='i'"'"'m so cool' \
         |-help='false' \
         |-unset.local.flag='a flag!' \""".stripMargin

    val WithGlobal = true
    val WithoutGlobal = false

    /**
     * This is done because global flags from other code can pollute the global flag space
     */
    def matchesGlobal(flagString: String): Boolean = {
      val localAndGlobal = Seq(
        """Set flags:""",
        """-set.local.flag='hi'""",
        """Unset flags:""",
        """-com.twitter.app.MyGlobalFlag='a test flag'""",
        """-flag.with.single.quote='i'"'"'m so cool'""",
        """-help='false'""",
        """-unset.local.flag='a flag!' \"""
      )

      // make sure every line in localAndGlobal exists in the flagString
      localAndGlobal map { flagString.contains } reduce { _ && _ }
    }

    assert(matchesGlobal(flagWithGlobal.formattedFlagValuesString(WithGlobal)))
    assert(matchesGlobal(flagWithoutGlobal.formattedFlagValuesString(WithGlobal)))

    assert(flagWithGlobal.formattedFlagValuesString(WithoutGlobal) === localOnly)
    assert(flagWithoutGlobal.formattedFlagValuesString(WithoutGlobal) === localOnly)

    assert(matchesGlobal(flagWithGlobal.formattedFlagValuesString()))
    assert(flagWithoutGlobal.formattedFlagValuesString() === localOnly)
  }
}
