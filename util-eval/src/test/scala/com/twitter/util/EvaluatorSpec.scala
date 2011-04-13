package com.twitter.util

import org.specs.Specification
import java.io.{File, FileOutputStream}

import com.twitter.io.TempFile

object EvaluatorSpec extends Specification {
  "Evaluator" should {
    "apply('expression')" in {
      Eval[Int]("1 + 1") mustEqual 2
    }

    "apply(new File(...))" in {
      Eval[Int](TempFile.fromResourcePath("/OnePlusOne.scala")) mustEqual 2
    }

    "apply(new File(...), new File(...))" in {
      val derived = Eval[() => String](
        TempFile.fromResourcePath("/Base.scala"),
        TempFile.fromResourcePath("/Derived.scala"))
      derived() mustEqual "hello"
    }

    "apply(InputStream)" in {
      Eval[Int](getClass.getResourceAsStream("/OnePlusOne.scala")) mustEqual 2
    }

    "inPlace('expression')" in {
      Eval.compile("object Doubler { def apply(n: Int) = n * 2 }")
      Eval.inPlace[Int]("Doubler(2)") mustEqual 4
      Eval.inPlace[Int]("Doubler(14)") mustEqual 28
    }
  }
}
