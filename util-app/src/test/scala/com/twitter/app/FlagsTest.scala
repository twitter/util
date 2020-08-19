package com.twitter.app

import com.twitter.util.registry.{Entry, GlobalRegistry, SimpleRegistry}
import org.scalatest.funsuite.AnyFunSuite

class FlagsTest extends AnyFunSuite {

  class Ctx(failFastUntilParsed: Boolean = false) {
    val flag = new Flags("test", includeGlobal = false, failFastUntilParsed)
    val fooFlag = flag("foo", 123, "The foo value")
    val barFlag = flag("bar", "okay", "The bar value")
  }

  test("Flag: add and parse flags") {
    val ctx = new Ctx
    import ctx._
    assert(flag.parseArgs(Array("-foo", "973", "-bar", "hello there")) == Flags.Ok(Nil))
    flag.finishParsing()
    assert(fooFlag() == 973)
    assert(barFlag() == "hello there")
  }

  test("Flag: registers after getting flag") {
    val ctx = new Ctx
    import ctx._
    val naive = new SimpleRegistry()
    GlobalRegistry.withRegistry(naive) {
      assert(flag.parseArgs(Array("-foo", "973", "-bar", "hello there")) == Flags.Ok(Nil))
      flag.finishParsing()
      assert(fooFlag() == 973)
      assert(barFlag() == "hello there")
      assert(
        naive.toSet == Set(
          Entry(Seq("flags", "foo"), "973"),
          Entry(Seq("flags", "bar"), "hello there"),
          Entry(Seq("flags", "help"), "false")
        )
      )
    }
  }

  test("Flag: registers when you don't parse a required argument") {
    val ctx = new Ctx
    import ctx._
    val naive = new SimpleRegistry()
    GlobalRegistry.withRegistry(naive) {
      val bazFlag = flag[String]("baz", "help help help")

      assert(flag.parseArgs(Array()) == Flags.Ok(Nil))
      flag.finishParsing()
      intercept[IllegalArgumentException] {
        bazFlag()
      }
      assert(
        naive.toSet == Set(
          Entry(Seq("flags", "baz"), Flag.EmptyRequired),
          Entry(Seq("flags", "help"), "false")
        )
      )
    }
  }

  test("Flag: registers the default when you don't parse an argument") {
    val ctx = new Ctx
    import ctx._
    val naive = new SimpleRegistry()
    GlobalRegistry.withRegistry(naive) {
      assert(flag.parseArgs(Array()) == Flags.Ok(Nil))
      flag.finishParsing()
      assert(fooFlag() == 123)
      assert(
        naive.toSet == Set(
          Entry(Seq("flags", "foo"), "123"),
          Entry(Seq("flags", "help"), "false")
        )
      )
    }
  }

  class Bctx extends Ctx {
    val yesFlag = flag("yes", false, "Just say yes.")
  }

  test("Boolean: default") {
    val ctx = new Bctx
    import ctx._

    flag.finishParsing()
    assert(!yesFlag())
  }

  test("Boolean: -yes") {
    val ctx = new Bctx
    import ctx._
    assert(flag.parseArgs(Array("-yes")) == Flags.Ok(Nil))
    assert(yesFlag())
  }

  test("Boolean: -yes=true") {
    val ctx = new Bctx
    import ctx._
    assert(flag.parseArgs(Array("-yes=true")) == Flags.Ok(Nil))
    assert(yesFlag())
  }

  test("Boolean: -yes=false") {
    val ctx = new Bctx
    import ctx._
    assert(flag.parseArgs(Array("-yes=false")) == Flags.Ok(Nil))
    assert(!yesFlag())
  }

  test("Boolean: -yes ARG") {
    val ctx = new Bctx
    import ctx._
    val rem = flag.parseArgs(Array("-yes", "ARG"))
    assert(yesFlag())
    assert(rem == Flags.Ok(Seq("ARG")))
  }

  test("Flag: handle remainders (sequential)") {
    val ctx = new Ctx
    import ctx._
    assert(flag.parseArgs(Array("-foo", "333", "arg0", "arg1")) == Flags.Ok(Seq("arg0", "arg1")))
  }

  test("Flag: handle remainders (interspersed)") {
    val ctx = new Ctx
    import ctx._
    assert(flag.parseArgs(Array("arg0", "-foo", "333", "arg1")) == Flags.Ok(Seq("arg0", "arg1")))
  }

  test("Flag: stop parsing at '--'") {
    val ctx = new Ctx
    import ctx._
    assert(
      flag.parseArgs(Array("arg0", "--", "-foo", "333")) == Flags.Ok(Seq("arg0", "-foo", "333"))
    )
  }

  test("Flag: give nice parse errors") {
    val ctx = new Ctx
    import ctx._
    assert(flag.parseArgs(Array("-foo", "blah")).isInstanceOf[Flags.Error])
  }

  test("Flag: handle -help") {
    val ctx = new Ctx
    import ctx._
    assert(flag.parseArgs(Array("-help")).isInstanceOf[Flags.Help])
  }

  test("Flag: mandatory flag without argument") {
    val ctx = new Ctx
    import ctx._
    assert(flag.parseArgs(Array("-foo")).isInstanceOf[Flags.Error])
  }

  test("Flag: undefined") {
    val ctx = new Ctx
    import ctx._
    assert(flag.parseArgs(Array("-undefined")).isInstanceOf[Flags.Error])
    assert(
      flag.parseArgs(Array("-undefined"), allowUndefinedFlags = true) == Flags.Ok(Seq("-undefined"))
    )
  }

  test("formatFlagValues") {

    val flagWithGlobal = new Flags("my", includeGlobal = true)
    flagWithGlobal("unset.local.flag", "a flag!", "this is a local flag")
    flagWithGlobal("set.local.flag", "a flag!", "this is a local flag")
    flagWithGlobal("flag.with.single.quote", "i'm so cool", "why would you do this?")
    flagWithGlobal.parseArgs(Array("-set.local.flag=hi"))

    val flagWithoutGlobal = new Flags("my", includeGlobal = false)
    flagWithoutGlobal("unset.local.flag", "a flag!", "this is a local flag")
    flagWithoutGlobal("set.local.flag", "a flag!", "this is a local flag")
    flagWithoutGlobal("flag.with.single.quote", "i'm so cool", "why would you do this?")
    flagWithoutGlobal.parseArgs(Array("-set.local.flag=hi"))

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

    assert(flagWithGlobal.formattedFlagValuesString(WithoutGlobal) == localOnly)
    assert(flagWithoutGlobal.formattedFlagValuesString(WithoutGlobal) == localOnly)

    assert(matchesGlobal(flagWithGlobal.formattedFlagValuesString()))
    assert(flagWithoutGlobal.formattedFlagValuesString() == localOnly)
  }

  test("Flag that needs parsing will complain without parsing") {
    val flag = new Flag[Int]("foo", "bar", Left(() => 3), failFastUntilParsed = true)
    intercept[IllegalStateException] {
      flag()
    }
    flag.finishParsing()
    assert(flag() == 3)
  }

  test("Flag that needs parsing ok after parsing") {
    val flag = new Flag[Int]("foo", "bar", Left(() => 3), failFastUntilParsed = true)
    intercept[IllegalStateException] {
      flag()
    }
    flag.parse("4")
    assert(flag() == 4)
  }

  test("Flag that needs parsing ok resets properly") {
    val flag = new Flag[Int]("foo", "bar", Left(() => 3), failFastUntilParsed = true)
    flag.parse("4")
    assert(flag() == 4)

    flag.reset()
    intercept[IllegalStateException] {
      flag()
    }
    flag.parse("4")
    assert(flag() == 4)
  }

  test("Flag has failFast from Flags when added") {
    val somethingIdFlag =
      new Flag[Int]("something.id", "bar", Left(() => 3), failFastUntilParsed = false)
    assert(somethingIdFlag() == 3) // this logs a message in SEVERE and we get the default value

    val testFlags =
      new Flags(
        "failFastTest",
        includeGlobal = false,
        failFastUntilParsed = true
      ) // added flags will inherit this value
    testFlags.add(somethingIdFlag)
    intercept[IllegalStateException] {
      testFlags
        .getAll()
        .foreach(flag =>
          if (flag.name == "something.id")
            flag.apply()) // this should fail, since all added flags should now fail fast.
    }

    // now parse and apply
    assert(testFlags.parseArgs(Array("-something.id=5")) == Flags.Ok(Nil))
    assert(somethingIdFlag() == 5) // we get the parsed value
  }

  test("Flags fail before parsing, OK after") {
    val ctx = new Ctx(failFastUntilParsed = true)
    import ctx._

    intercept[IllegalStateException] {
      fooFlag()
    }
    assert(flag.parseArgs(Array()) == Flags.Ok(Nil))
    assert(fooFlag() == 123)
  }

  test("Flags reset properly with respect to failure") {
    val ctx = new Ctx(failFastUntilParsed = true)
    import ctx._

    assert(flag.parseArgs(Array()) == Flags.Ok(Nil))
    assert(fooFlag() == 123)

    flag.reset()
    intercept[IllegalStateException] {
      fooFlag()
    }
    assert(flag.parseArgs(Array()) == Flags.Ok(Nil))
    assert(fooFlag() == 123)
  }
}
