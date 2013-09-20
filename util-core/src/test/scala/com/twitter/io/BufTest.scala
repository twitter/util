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
  
  test("Buf.Utf8: English") {
    val buf = Buf.Utf8("Hello, world!")
    assert(buf.length === 13)
    val bytes = new Array[Byte](13)
    buf.write(bytes, 0)
    assert("Hello, world!".toSeq === bytes.toSeq.map(_.toChar))
    
    val Buf.Utf8(s) = buf
    assert(s === "Hello, world!")
  }
  
  test("Buf.Utf8: Japanese") {
    val buf = Buf.Utf8("￼￼￼￼￼￼￼")
    assert(buf.length === 21)
    val bytes = new Array[Byte](21)
    buf.write(bytes, 0)
    
    val expected = Array[Byte](
      -17, -65, -68, -17, -65, -68, -17, 
      -65, -68, -17, -65, -68, -17, -65, -68, 
      -17, -65, -68, -17, -65, -68)
      
    assert(bytes.toSeq === expected.toSeq)

    val Buf.Utf8(s) = buf
    assert(s === "￼￼￼￼￼￼￼")
  }
}

