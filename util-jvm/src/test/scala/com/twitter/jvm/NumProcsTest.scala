package com.twitter.jvm

import org.scalatest.FunSuite

class NumProcsTest extends FunSuite {

  test("return the number of available processors according to the runtime by default") {
    assert(System.getProperty("com.twitter.jvm.numProcs") == null)
    assert(numProcs() == Runtime.getRuntime().availableProcessors())
  }

  test("settable as a flag") {
    val old = numProcs()
    numProcs.parse("10.0")

    assert(numProcs() == 10.0)

    numProcs.parse()
    assert(numProcs() == old)
  }

  test("return the number of available processors occording to the runtime if set to -1.0") {
    numProcs.parse("-1.0")

    assert(numProcs() == Runtime.getRuntime.availableProcessors().toDouble)
  }
}
