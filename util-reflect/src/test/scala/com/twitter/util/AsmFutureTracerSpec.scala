package com.twitter.util

import org.specs.Specification

object AsmFutureTracerSpec extends Specification {
  "error reporting" should {
    "be helpful" in {
//      val cause = new Exception("cause1")
//      val e = new Exception("asdf", cause)
//      val nestedFutureException =
//        Future.value(1) flatMap { i =>
//          Future.value(1) flatMap { j =>
//            throw e
//            Future.value(1)
//          }
//        }
//      val result = nestedFutureException.poll.get
//      result.isThrow must beTrue
//      result onFailure { t =>
//        t.toString mustEqual("java.lang.Exception: asdf")
//        t.getCause.toString mustEqual("java.lang.Exception: cause1")
//        t.getCause.getCause.toString mustEqual("com.twitter.util.Future asynchronous trace")
//        t.getCause.getCause.getStackTrace.toList.map(_.toString) mustEqual(List(
//          "com.twitter.util.AsmFutureTracerSpec$$anonfun$1$$anonfun$apply$2$$anonfun$2$$anonfun$apply$3.<init>(AsmFutureTracerSpec.scala:12)",
//          "com.twitter.util.AsmFutureTracerSpec$$anonfun$1$$anonfun$apply$2$$anonfun$2.<init>(AsmFutureTracerSpec.scala:11)"
//        ))
//      }
    }
  }
}