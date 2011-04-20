package com.twitter.io

import scala.util.Random

import org.specs.Specification

object StreamSpec extends Specification {
  noDetailedDiffs()

  "Stream.copy" should {
    "copy the entire stream" in {
      val buf = new Array[Byte](2048)
      (new Random).nextBytes(buf)
      val bis = new java.io.ByteArrayInputStream(buf)
      val bos = new java.io.ByteArrayOutputStream()
      Stream.copy(bis, bos)
      bos.toByteArray.toSeq must be_==(buf.toSeq)
    }

    "produce empty streams from empty streams" in {
      val bis = new java.io.ByteArrayInputStream(new Array[Byte](0))
      val bos = new java.io.ByteArrayOutputStream()
      Stream.copy(bis, bos)
      bos.size must be_==(0)
    }
  }
}
