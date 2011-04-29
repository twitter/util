package com.twitter.util

import org.specs.Specification
import java.io.{File, FileOutputStream}

import com.twitter.io.TempFile

object EvalSpec extends Specification {
  "Evaluator" should {
    "apply('expression')" in {
      (new Eval).apply[Int]("1 + 1") mustEqual 2
    }

    "apply(new File(...))" in {
      (new Eval).apply[Int](TempFile.fromResourcePath("/OnePlusOne.scala")) mustEqual 2
    }

    "apply(new File(...), new File(...))" in {
      val derived = (new Eval).apply[() => String](
        TempFile.fromResourcePath("/Base.scala"),
        TempFile.fromResourcePath("/Derived.scala"))
      derived() mustEqual "hello"
    }

    "apply(InputStream)" in {
      (new Eval).apply[Int](getClass.getResourceAsStream("/OnePlusOne.scala")) mustEqual 2
    }

    "inPlace('expression')" in {
      // Old object API works
      Eval.compile("object Doubler { def apply(n: Int) = n * 2 }")
      Eval.inPlace[Int]("Doubler(2)") mustEqual 4
      Eval.inPlace[Int]("Doubler(14)") mustEqual 28
      // New class API fails
      // val eval = new Eval
      // eval.compile("object Doubler { def apply(n: Int) = n * 2 }")
      // eval.inPlace[Int]("Doubler(2)") mustEqual 4
      // eval.inPlace[Int]("Doubler(14)") mustEqual 28
    }

    "check" in {
      (new Eval).check("23")      mustEqual ()
      (new Eval).check("invalid") must throwA[Eval.CompilerException]
    }

    "#include" in {
      val derived = Eval[() => String](
        TempFile.fromResourcePath("/Base.scala"),
        TempFile.fromResourcePath("/DerivedWithInclude.scala"))
      derived() mustEqual "hello"
      derived.toString mustEqual "hello, joe"
    }

    "throws a compilation error when Ruby is #included" in {
      Eval[() => String](
        TempFile.fromResourcePath("RubyInclude.scala")) must throwA[Throwable]
    }
  }
}
