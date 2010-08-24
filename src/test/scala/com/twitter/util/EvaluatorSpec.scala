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
  }
}
