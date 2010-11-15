package com.twitter.util

import org.specs.Specification
import java.io.File

object EvaluatorSpec extends Specification {
  "Evaluator" should {
    "apply('expression')" in {
      Eval[Int]("1 + 1") mustEqual 2
    }

    "apply(new File(...))" in {
      Eval[Int](new File("src/test/resources/OnePlusOne.scala")) mustEqual 2
    }

    "apply(new File(...), new File(...))" in {
      val derived = Eval[() => String](new File("src/test/resources/Base.scala"), new File("src/test/resources/Derived.scala"))
      derived() mustEqual "hello"
    }
  }
}
