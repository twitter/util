package com.twitter.io

import org.scalatest.funsuite.AnyFunSuite
import scala.util.Random

class StreamIOTest extends AnyFunSuite {
  test("StreamIO.copy should copy the entire stream") {
    val buf = new Array[Byte](2048)
    (new Random).nextBytes(buf)
    val bis = new java.io.ByteArrayInputStream(buf)
    val bos = new java.io.ByteArrayOutputStream()
    StreamIO.copy(bis, bos)
    assert(bos.toByteArray.toSeq == buf.toSeq)
  }

  test("StreamIO.copy should produce empty streams from empty streams") {
    val bis = new java.io.ByteArrayInputStream(new Array[Byte](0))
    val bos = new java.io.ByteArrayOutputStream()
    StreamIO.copy(bis, bos)
    assert(bos.size == 0)
  }
}
