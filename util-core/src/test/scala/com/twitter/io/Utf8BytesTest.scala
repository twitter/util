package com.twitter.io

import org.scalatest.funsuite.AnyFunSuite
import java.nio.charset.StandardCharsets

class Utf8BytesTest extends AnyFunSuite {
  test("Utf8 created from a byte[] is immutable") {
    val arr = "this is a test".getBytes(StandardCharsets.UTF_8)
    val utf8 = Utf8Bytes(arr)
    arr(0) = 'd'

    assert(utf8.toString == "this is a test")
  }

  test("from string") {
    val utf8 = Utf8Bytes("test string")
    assert(utf8.toString == "test string")
  }

  test("from Buf") {
    val utf8 = Utf8Bytes(Buf.Utf8("test string"))
    assert(utf8.toString == "test string")
  }

  test("byte length and length accurate when using multi-byte characters") {
    val utf8 = Utf8Bytes("\u93E1")
    assert(utf8.length() == 1)
    assert(utf8.asBuf.length == 3)
  }

  test("simple subsequence") {
    val utf8 = Utf8Bytes("test string")
    val slice = utf8.subSequence(5, 11)
    assert(slice == "string")
  }

  test("subsequence with multi-byte characters") {
    val utf8 = Utf8Bytes("\u93E1 string")
    val slice = utf8.subSequence(2, 8)
    assert(slice == "string")
  }

  test("simple charAt") {
    val utf8 = Utf8Bytes("abcdef")
    assert(utf8.charAt(4) == 'e')
  }

  test("charAt with multi-byte characters") {
    val utf8 = Utf8Bytes("\u93E1abcdef")
    assert(utf8.charAt(4) == 'd')
  }

  test("asBuf round trip") {
    val buf = Buf.Utf8("test string")
    val utf8 = Utf8Bytes(buf)
    assert(utf8.asBuf == buf)
  }

  test("invalid utf8 bytes") {
    val buf = Buf.ByteArray.Owned(Array(0x80.toByte))
    val utf8 = Utf8Bytes(buf)
    assert(utf8.toString == "\uFFFD")
  }

  test("equality and hash code") {
    val a = Utf8Bytes("test string")
    val b = Utf8Bytes("test string")
    val c = Utf8Bytes("test string 2")

    assert(a.hashCode() == b.hashCode())

    assert(a == a)
    assert(a == b)
    assert(a != c)
  }
}
