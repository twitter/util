package com.twitter.util

import org.specs.Specification

object AsmFutureTracerSpec extends Specification {
  class MyException extends Exception("yarrr")

  "error reporting" should {
    "be helpful" in {
      val cause = new Exception("cause1")
      val e = new Exception("asdf", cause)
      val nestedFutureException =
        Future.value(1) flatMap { i => // line A
          Future.value(1) flatMap { j => // line B
            throw e
            Future.value(1)
          }
        }
      val result = nestedFutureException.poll.get
      result.isThrow must beTrue
      result onFailure { t =>
        t.toString mustEqual("java.lang.Exception: asdf")
        t.getCause.toString mustEqual("java.lang.Exception: cause1")
        t.getCause.getCause.toString mustEqual("com.twitter.util.Future asynchronous trace")

        /**
         * Unfortunately, the assertion below is brittle because of the way the Scala
         * compiler names anonymous functions. If you add more tests to `AsmFutureTracerSpec`
         * or any lines of code above `line A` and `line B`, the assertion below must change.
         *
         * I recommend inspecting the output of the following by hand:
         *   println(t.getCause.getCause.getStackTrace.toList.map(_.toString))
         */
        t.getCause.getCause.getStackTrace.toList.map(_.toString) mustEqual(List(
          "com.twitter.util.AsmFutureTracerSpec$$anonfun$2$$anonfun$apply$2$$anonfun$3$$anonfun$apply$3.<init>(AsmFutureTracerSpec.scala:14)",
          "com.twitter.util.AsmFutureTracerSpec$$anonfun$2$$anonfun$apply$2$$anonfun$3.<init>(AsmFutureTracerSpec.scala:13)"
        ))
      }
    }
  }

  "exceptions should preserve class" in {
    val e = Future.value(1) flatMap { _ =>
      Future.exception(new MyException)
    }

    (e handle {
      case _: MyException => ()
    }).poll.get.isReturn mustBe true

    try {
      e()
    } catch {
      case _: MyException => ()
      case f => fail(f.getClass.getName + " must be an insance of " + classOf[MyException].getClass.getName)
    }
  }
}