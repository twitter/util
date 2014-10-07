package com.twitter.io

import java.nio.CharBuffer
import java.util.Arrays
import org.junit.runner.RunWith
import org.mockito.Matchers.any
import org.mockito.Mockito.{verify, when}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalacheck.{Arbitrary, Gen, Prop}
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.{GeneratorDrivenPropertyChecks, Checkers}

@RunWith(classOf[JUnitRunner])
class BufTest extends FunSuite with MockitoSugar with GeneratorDrivenPropertyChecks with Checkers {
  val AllCharsets = Seq(
    Charsets.Iso8859_1,
    Charsets.UsAscii,
    Charsets.Utf8,
    Charsets.Utf16,
    Charsets.Utf16BE,
    Charsets.Utf16LE
  )

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
    val a3 = Array[Byte](7,8,9)

    val buf = Buf.ByteArray(a1) concat Buf.ByteArray(a2) concat Buf.ByteArray(a3)
    assert(buf.length === 9)
    val x = Array.fill(9) { 0.toByte }
    buf.write(x, 0)
    assert(x.toSeq === (a1++a2++a3).toSeq)
  }

  test("Buf.concat.slice") {
    val a1 = Array.range(0, 8).map(_.toByte)
    val a2 = Array.range(8, 16).map(_.toByte)
    val a3 = Array.range(16, 24).map(_.toByte)
    val arr = a1 ++ a2 ++ a3
    val buf = Buf.ByteArray(a1) concat Buf.ByteArray(a2) concat Buf.ByteArray(a3)

    for (i <- 0 until arr.length; j <- i until arr.length) {
      val w = new Array[Byte](j-i)
      buf.slice(i, j).write(w, 0)
      assert(w.toSeq === arr.slice(i, j).toSeq)
    }
  }

  test("Buf.concat.slice empty") {
    val a1 = Array.range(0, 8).map(_.toByte)
    val a2 = Array.range(8, 16).map(_.toByte)
    val a3 = Array.range(16, 24).map(_.toByte)
    val arr = a1 ++ a2 ++ a3
    val buf = Buf.ByteArray(a1) concat Buf.ByteArray(a2) concat Buf.ByteArray(a3)

    assert(buf.slice(25, 30) === Buf.Empty)
  }

  test("Buf.concat.slice truncated") {
    val a1 = Array.range(0, 8).map(_.toByte)
    val a2 = Array.range(8, 16).map(_.toByte)
    val a3 = Array.range(16, 24).map(_.toByte)
    val arr = a1 ++ a2 ++ a3
    val buf = Buf.ByteArray(a1) concat Buf.ByteArray(a2) concat Buf.ByteArray(a3)

    assert(buf.slice(20, 30) === buf.slice(20, 24)) // just last
    assert(buf.slice(12, 30) === buf.slice(12, 24)) // two bufs
    assert(buf.slice(8, 30) === buf.slice(8, 24)) // two bufs
  }

  test("Buf.concat.slice invalid args throws") {
    val a1 = Array.range(0, 8).map(_.toByte)
    val a2 = Array.range(8, 16).map(_.toByte)
    val a3 = Array.range(16, 24).map(_.toByte)
    val arr = a1 ++ a2 ++ a3
    val buf = Buf.ByteArray(a1) concat Buf.ByteArray(a2) concat Buf.ByteArray(a3)

    intercept[IllegalArgumentException] {
      buf.slice(-1, 0)
    }
    intercept[IllegalArgumentException] {
      buf.slice(1, 0)
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

  test("Buf.Utf8.unapply with a Buf.ByteArray") {
    val str = "Hello, world!"
    val buf = Buf.Utf8(str)
    val Buf.Utf8(out) = buf
    assert(out === str)
  }

  test("Buf.Utf8.unapply with a non-Buf.ByteArray") {
    val buf = mock[Buf]
    when(buf.length) thenReturn(12)
    when(buf.write(any[Array[Byte]], any[Int])) thenAnswer(
      new Answer[Unit]() {
        def answer(invocation: InvocationOnMock) = {}
      }
    )

    val Buf.Utf8(_) = buf
    verify(buf).write(any[Array[Byte]], any[Int])
  }

  AllCharsets foreach { charset =>
    test("Buf.StringCoder: decoding to %s does not modify underlying byte buffer".format(charset.name)) {
      val coder = new Buf.StringCoder(charset) {}
      val hw = "Hello, world!"
      val bb = charset.encode(hw)
      val wrappedBuf = new Buf.ByteBuffer(bb)
      val coder(_) = wrappedBuf
      assert(wrappedBuf.bb === bb)
      assert(wrappedBuf.bb.position() === bb.position())
    }
  }

  AllCharsets foreach { charset =>
    test("Buf.StringCoder: %s charset can encode and decode an English phrase".format(charset.name)) {
      val coder = new Buf.StringCoder(charset) {}
      val phrase = "Hello, world!"
      val encoded = coder(phrase)
      val coder(out) = encoded
      assert(out === phrase)
    }
  }

  test("Buf.ByteBuffer") {
    val bytes = Array[Byte](111, 107, 116, 104, 101, 110)
    val bb = java.nio.ByteBuffer.wrap(bytes)
    val buf = Buf.ByteBuffer(bb)

    assert(Buf.ByteArray(bytes) === buf)

    val written = new Array[Byte](buf.length)
    buf.write(written, 0)
    assert(Arrays.equals(bytes, written))

    val offsetWritten = new Array[Byte](buf.length + 2)
    buf.write(offsetWritten, 2)
    assert(Arrays.equals(Array[Byte](0, 0) ++ bytes, offsetWritten))

    assert(Buf.ByteArray(bytes.drop(2).take(2)) === buf.slice(2, 4))
    // overflow slice
    assert(Buf.ByteArray(bytes.drop(2)) === buf.slice(2, 10))

    assert(Buf.ByteBuffer(java.nio.ByteBuffer.allocate(0)) === Buf.Empty)
    assert(Buf.ByteBuffer(java.nio.ByteBuffer.allocateDirect(0)) === Buf.Empty)

    val bytes2 = Array[Byte](1,2,3,4,5,6,7,8,9,0)
    val buf2 = Buf.ByteBuffer(java.nio.ByteBuffer.wrap(bytes2, 3, 4))
    assert(buf2 === Buf.ByteArray(4,5,6,7))

    val Buf.ByteBuffer(byteBuffer) = buf
    assert(byteBuffer === bb)
  }

  test("hash code, equals") {
    def ae(a: Buf, b: Buf) {
      assert(a === b)
      assert(a.hashCode === b.hashCode)
    }

    val string = "okthen"
    val bytes = Array[Byte](111, 107, 116, 104, 101, 110)

    ae(Buf.Utf8(string), Buf.ByteArray(bytes))

    val shifted = new Array[Byte](bytes.length + 3)
    System.arraycopy(bytes, 0, shifted, 3, bytes.length)
    ae(Buf.Utf8(string), Buf.ByteArray(shifted, 3, 3+bytes.length))
  }

  check(Prop.forAll { (in: Int) =>
    val buf = Buf.U32BE(in)
    val Buf.U32BE(out, _) = buf

    val arr = new Array[Byte](4)
    buf.write(arr, 0)
    val outByteBuf = java.nio.ByteBuffer.wrap(arr)
    outByteBuf.order(java.nio.ByteOrder.BIG_ENDIAN)

    out == in && outByteBuf.getInt == in
  })

  check(Prop.forAll { (in: Int) =>
    val buf = Buf.U32LE(in)
    val Buf.U32LE(out, _) = buf

    val arr = new Array[Byte](4)
    buf.write(arr, 0)
    val outByteBuf = java.nio.ByteBuffer.wrap(arr)
    outByteBuf.order(java.nio.ByteOrder.LITTLE_ENDIAN)

    out == in && outByteBuf.getInt == in
  })

  check(Prop.forAll { (in: Long) =>
    val buf = Buf.U64BE(in)
    val Buf.U64BE(out, _) = buf

    val arr = new Array[Byte](8)
    buf.write(arr, 0)
    val outByteBuf = java.nio.ByteBuffer.wrap(arr)
    outByteBuf.order(java.nio.ByteOrder.BIG_ENDIAN)

    out == in && outByteBuf.getLong == in
  })

  check(Prop.forAll { (in: Long) =>
    val buf = Buf.U64LE(in)
    val Buf.U64LE(out, _) = buf

    val arr = new Array[Byte](8)
    buf.write(arr, 0)
    val outByteBuf = java.nio.ByteBuffer.wrap(arr)
    outByteBuf.order(java.nio.ByteOrder.LITTLE_ENDIAN)

    out == in && outByteBuf.getLong == in
  })

  test("Buf num matching") {
    val hasMatch = Buf.Empty match {
      case Buf.U32BE(_, _) => true
      case Buf.U64BE(_, _) => true
      case Buf.U32LE(_, _) => true
      case Buf.U64LE(_, _) => true
      case _ => false
    }
    assert(!hasMatch)

    val buf = Buf.Empty
      .concat(Buf.U32BE(Int.MaxValue))
      .concat(Buf.U64BE(Long.MaxValue))
      .concat(Buf.U32LE(Int.MinValue))
      .concat(Buf.U64LE(Long.MinValue))

    val Buf.U32BE(be32,
        Buf.U64BE(be64,
        Buf.U32LE(le32,
        Buf.U64LE(le64,
        rem
    )))) = buf

    assert(be32 === Int.MaxValue)
    assert(be64 === Long.MaxValue)
    assert(le32 === Int.MinValue)
    assert(le64 === Long.MinValue)
    assert(rem === Buf.Empty)
  }

  test("concat two ConcatBufs") {
    val a1 = Array.range(0, 8).map(_.toByte)
    val a2 = Array.range(8, 16).map(_.toByte)

    val a3 = Array.range(16, 24).map(_.toByte)
    val a4 = Array.range(24, 32).map(_.toByte)

    val arr = a1 ++ a2 ++ a3 ++ a4

    val cbuf1 = Buf.ByteArray(a1) concat Buf.ByteArray(a2)
    val cbuf2 = Buf.ByteArray(a3) concat Buf.ByteArray(a4)
    val cbuf = cbuf1 concat cbuf2

    for (i <- 0 until arr.length; j <- i until arr.length) {
      val w = new Array[Byte](j-i)
      cbuf.slice(i, j).write(w, 0)
      assert(w.toSeq === arr.slice(i, j).toSeq)
    }
  }

  test("highly nested concat buffer shouldn't throw StackOverflowError") {
    val size = 50 * 1000
    val b = 'x'.toByte
    val bigBuf = (1 to size).foldLeft(Buf.Empty) {
      case (buf, _) => buf concat Buf.ByteArray(b)
    }

    val expected = Array.fill(size) { 'x'.toByte }
    val output = new Array[Byte](size)
    bigBuf.write(output, 0)
    assert(bigBuf.length === size)
    assert(expected.toSeq === output.toSeq)

    val sliced = bigBuf.slice(1, size - 1)
    val size2 = size - 2
    val expected2 = Array.fill(size2) { 'x'.toByte }
    val output2 = new Array[Byte](size2)
    sliced.write(output2, 0)
    assert(sliced.length === size2)
    assert(expected2.toSeq === output2.toSeq)
  }

  implicit lazy val arbBuf: Arbitrary[Buf] = {
    import java.nio.charset.StandardCharsets.UTF_8
    val ctors: Seq[String => Buf] = Seq(
      Buf.Iso8859_1.apply,
      Buf.UsAscii.apply,
      Buf.Utf8.apply,
      Buf.Utf16.apply,
      Buf.Utf16BE.apply,
      Buf.Utf16LE.apply,
      s => Buf.ByteArray(s.getBytes("UTF-8")),
      s => Buf.ByteBuffer(UTF_8.encode(CharBuffer.wrap(s))))

    Arbitrary(for {
      s <- Arbitrary.arbitrary[String]
      c <- Gen.oneOf(ctors)
    } yield c.apply(s))
  }

  test("Buf.slice") {
    val bufSplits = for {
      b <- Arbitrary.arbitrary[Buf]
      i <- Gen.choose(0, b.length)
      j <- Gen.choose(i, b.length)
      k <- Gen.choose(j, b.length)
    } yield (b, i, j, k)

    forAll(bufSplits) { case (buf, i, j, k) =>
      // This `whenever` would be unnecessary if not for Shrinking, see:
      // https://github.com/rickynils/scalacheck/issues/18.
      whenever (i <= j && j <= k) {
        val b1 = buf.slice(i, k)
        val b2 = b1.slice(0, j - i)

        assert(b1.length === k - i)
        assert(b2.length === j - i)
      }
    }
  }
}
