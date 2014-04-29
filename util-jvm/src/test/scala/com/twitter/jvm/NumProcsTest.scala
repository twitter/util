package com.twitter.jvm

import org.junit.runner.RunWith

import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class NumProcsTest extends FunSpec {
  describe("numProcs") {
    it("should be the number of available processors according to the runtime by default") {
      assert(System.getProperty("com.twitter.jvm.numProcs") === null)
      assert(numProcs() === Runtime.getRuntime().availableProcessors())
    }

    it("should be settable as a flag") {
      val old = numProcs()
      numProcs.parse("10.0")

      assert(numProcs() === 10.0)

      numProcs.parse()
      assert(numProcs() === old)
    }
  }
}
