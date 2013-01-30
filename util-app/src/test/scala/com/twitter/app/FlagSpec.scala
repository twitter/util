package com.twitter.app

import org.specs.SpecificationWithJUnit

class FlagSpec extends SpecificationWithJUnit {
  "Flaggable" should {
    "parse booleans" in {
      Flaggable.ofBoolean.parse("true") must beTrue
      Flaggable.ofBoolean.parse("false") must beFalse

      Flaggable.ofBoolean.parse("") must throwA(new Exception).like {
        case _: NumberFormatException => true  // 2.9.x
        case _: IllegalArgumentException => true  // 2.10.x
      }
      Flaggable.ofBoolean.parse("gibberish") must throwA(new Exception).like {
        case _: NumberFormatException => true  // 2.9.x
        case _: IllegalArgumentException => true  // 2.10.x
      }
    }

    "parse strings" in {
      Flaggable.ofString.parse("blah") must be_==("blah")
    }

    "parse/show inet addresses" in {
      val local8080 = Flaggable.ofInetSocketAddress.parse(":8080")
      local8080.getAddress.isAnyLocalAddress must beTrue
      local8080.getPort must be_==(8080)

      val ip8080 = Flaggable.ofInetSocketAddress.parse("141.211.133.111:8080")
      ip8080.getHostName must be_==("141.211.133.111")
      ip8080.getPort must be_==(8080)

      Flaggable.ofInetSocketAddress.show(local8080) must be_==(":8080")
      Flaggable.ofInetSocketAddress.show(ip8080) must be_==("141.211.133.111:8080")
    }

    "parse seqs" in {
      Flaggable.ofSeq[Int].parse("1,2,3,4") must be_==(Seq(1,2,3,4))
    }

    "parse tuples" in {
      Flaggable.ofTuple[Int, String].parse("1,hello") must be_==((1, "hello"))
      Flaggable.ofTuple[Int, String].parse("1") must throwA(new IllegalArgumentException("not a 't,u'"))
    }
  }

  "Flags" should {
    val flag = new Flags("test")
    val fooFlag = flag("foo", 123, "The foo value")
    val barFlag = flag("bar", "okay", "The bar value")

    fooFlag() must be_==(123)
    barFlag() must be_==("okay")

    "add and parse flags" in {
      flag.parse(Array("-foo", "973", "-bar", "hello there")) must beEmpty
      fooFlag() must be_==(973)
      barFlag() must be_==("hello there")
    }

    "deal with boolean (which have defaults)" in {
      val yesFlag = flag("yes", false, "Just say yes.")
      yesFlag() must beFalse

      "-yes" in {
        flag.parse(Array("-yes")) must beEmpty
        yesFlag() must beTrue
      }

      "-yes=true" in {
        flag.parse(Array("-yes=true")) must beEmpty
        yesFlag() must beTrue
      }

      "-yes=false" in {
        flag.parse(Array("-yes=false")) must beEmpty
        yesFlag() must beFalse
      }

    }

    "handle remainders (sequential)" in {
      flag.parse(Array("-foo", "333", "arg0", "arg1")) must be_==(Seq("arg0", "arg1"))
    }

    "handle remainders (interpspersed)" in {
      flag.parse(Array("arg0", "-foo", "333", "arg1")) must be_==(Seq("arg0", "arg1"))
    }

    "give nice parse errors" in {
      flag.parse(Array("-foo", "blah")) must throwA(new Exception).like {
        case FlagParseException("foo", _: NumberFormatException) => true
      }
    }

    "handle -help" in {
      flag.parse(Array("-help")) must throwA[FlagUsageError]
    }

    "mandatory flag without argument" in {
      flag.parse(Array("-foo")) must throwA(FlagParseException("foo", new FlagValueRequiredException))
    }

    "undefined" in {
      flag.parse(Array("-undefined")) must throwA(FlagParseException("undefined", new FlagUndefinedException))
      flag.parse(Array("-undefined"), true) must be_==(Seq("-undefined"))
    }
  }
}
