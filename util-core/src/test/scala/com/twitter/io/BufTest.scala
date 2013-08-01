package com.twitter.io

import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

@RunWith(classOf[JUnitRunner])
class BufTest extends FunSuite {
  test("Buf.ByteArray.slice") {
    val arr = Array.range(0, 16).map(_.toByte)
    val buf = Buf.ByteArray(arr)
    for (i <- 0 until arr.length; j <- i until arr.length) {
      val w = new Array[Byte](j-i)
      buf.slice(i, j).write(w, 0)
      assert(w.toSeq === arr.slice(i, j).toSeq)
    }
  }

  test("Buf.concat") {
    val a1 = Array[Byte](1,2,3)
    val a2 = Array[Byte](4,5,6)

    val buf = Buf.ByteArray(a1) concat Buf.ByteArray(a2)
    assert(buf.length === 6)
    val x = Array.fill(6) { 0.toByte }
    buf.write(x, 0)
    assert(x.toSeq === (a1++a2).toSeq)
  }

  test("Buf.concat.slice") {
    val a1 = Array.range(0, 8).map(_.toByte)
    val a2 = Array.range(8, 16).map(_.toByte)
    val arr = a1 ++ a2
    val buf = Buf.ByteArray(a1) concat Buf.ByteArray(a2)

    for (i <- 0 until arr.length; j <- i until arr.length) {
      val w = new Array[Byte](j-i)
      buf.slice(i, j).write(w, 0)
      assert(w.toSeq === arr.slice(i, j).toSeq)
    }
  }
}
