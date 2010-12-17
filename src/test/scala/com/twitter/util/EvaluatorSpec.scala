package com.twitter.util

import org.specs.Specification
import java.io.{File, FileOutputStream}

object EvaluatorSpec extends Specification {
  def file(path: String) = {
    val stream = getClass.getResourceAsStream(path)
    val file = File.createTempFile(path, "scala")
    val fos = new FileOutputStream(file)

    var byte = stream.read()
    while (byte != -1) {
      fos.write(byte)
      byte = stream.read()
    }

    file
  }

  "Evaluator" should {
    "apply('expression')" in {
      Eval[Int]("1 + 1") mustEqual 2
    }

    "apply(new File(...))" in {
      Eval[Int](new File("src/test/resources/OnePlusOne.scala")) mustEqual 2
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
