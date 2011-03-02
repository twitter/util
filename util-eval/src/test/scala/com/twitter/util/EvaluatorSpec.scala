package com.twitter.util

import org.specs.Specification
import java.io.{File, FileOutputStream}

object EvaluatorSpec extends Specification {
  def file(path: String) = {
    new File("util-eval/src/test/resources", path)
  }

  "Evaluator" should {
    "apply('expression')" in {
      Eval[Int]("1 + 1") mustEqual 2
    }

    "apply(new File(...))" in {
      Eval[Int](file("OnePlusOne.scala")) mustEqual 2
    }

    "apply(new File(...), new File(...))" in {
      val derived = Eval[() => String](file("/Base.scala"), file("/Derived.scala"))
      derived() mustEqual "hello"
    }

    "apply(InputStream)" in {
      Eval[Int](getClass.getResourceAsStream("/OnePlusOne.scala")) mustEqual 2
    }
  }
}
