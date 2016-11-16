package com.twitter.io

import java.nio.CharBuffer
import java.nio.charset.{StandardCharsets => JChar}
import java.util.Arrays
import org.junit.runner.RunWith
import org.scalacheck.{Arbitrary, Gen, Prop}
import org.scalatest.FunSuite
import org.scalatest.junit.{AssertionsForJUnit, JUnitRunner}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.{GeneratorDrivenPropertyChecks, Checkers}

@RunWith(classOf[JUnitRunner])
class BufTest extends FunSuite
  with MockitoSugar
  with GeneratorDrivenPropertyChecks
  with Checkers
  with AssertionsForJUnit {

  val AllCharsets = Seq(
    JChar.ISO_8859_1,
    JChar.US_ASCII,
    JChar.UTF_8,
    JChar.UTF_16,
    JChar.UTF_16BE,
    JChar.UTF_16LE
  )

  test("Buf.ByteArray.slice") {
    val arr = Array.range(0, 16).map(_.toByte)
    val buf = Buf.ByteArray.Owned(arr)
    for (i <- 0 until arr.length; j <- i until arr.length) {
      val w = new Array[Byte](j-i)
      buf.slice(i, j).write(w, 0)
      assert(w.toSeq == arr.slice(i, j).toSeq)
    }
  }

  test("Buf.ByteArray.Shared.apply is safe") {
    val array = Array.fill(10)(0.toByte)
    val buf = Buf.ByteArray.Shared(array)
    array(0) = 13
    val copy = new Array[Byte](10)
    buf.write(copy, 0)
    assert(copy(0) == 0)
  }

  test("Buf.ByteArray.Direct.apply is unsafe") {
    val array = Array.fill(10)(0.toByte)
    val buf = Buf.ByteArray.Owned(array)
    array(0) = 13
    val copy = new Array[Byte](10)
    buf.write(copy, 0)
    assert(copy(0) == 13)
  }

  test("Buf.ByteArray.Shared.unapply is safe") {
    val array = Array.fill(10)(0.toByte)
    val Buf.ByteArray.Shared(copy) = Buf.ByteArray.Owned(array)
    array(0) = 13
    assert(copy(0) == 0)
  }

  test("Buf.ByteArray.Direct.unapply is unsafe") {
    val array = Array.fill(10)(0.toByte)
    val Buf.ByteArray.Owned(copy, _, _) = Buf.ByteArray.Owned(array)
    array(0) = 13
    assert(copy(0) == 13)
  }

  test("Buf.concat") {
    val a1 = Array[Byte](1,2,3)
    val a2 = Array[Byte](4,5,6)
    val a3 = Array[Byte](7,8,9)

    val buf = Buf.ByteArray.Owned(a1) concat Buf.ByteArray.Owned(a2) concat Buf.ByteArray.Owned(a3)
    assert(buf.length == 9)
    val x = Array.fill(9) { 0.toByte }
    buf.write(x, 0)
    assert(x.toSeq == (a1++a2++a3).toSeq)
  }

  test("Buf.concat.slice") {
    val a1 = Array.range(0, 8).map(_.toByte)
    val a2 = Array.range(8, 16).map(_.toByte)
    val a3 = Array.range(16, 24).map(_.toByte)
    val arr = a1 ++ a2 ++ a3
    val buf = Buf.ByteArray.Owned(a1) concat Buf.ByteArray.Owned(a2) concat Buf.ByteArray.Owned(a3)

    for (i <- 0 until arr.length; j <- i until arr.length) {
      val w = new Array[Byte](j-i)
      buf.slice(i, j).write(w, 0)
      assert(w.toSeq == arr.slice(i, j).toSeq)
    }
  }

  test("Buf.concat.slice empty") {
    val a1 = Array.range(0, 8).map(_.toByte)
    val a2 = Array.range(8, 16).map(_.toByte)
    val a3 = Array.range(16, 24).map(_.toByte)
    val buf = Buf.ByteArray.Owned(a1) concat
      Buf.ByteArray.Owned(a2) concat
      Buf.ByteArray.Owned(a3)

    assert(buf.slice(25, 30) == Buf.Empty)
  }

  test("Buf.concat.slice truncated") {
    val a1 = Array.range(0, 8).map(_.toByte)
    val a2 = Array.range(8, 16).map(_.toByte)
    val a3 = Array.range(16, 24).map(_.toByte)
    val buf = Buf.ByteArray.Owned(a1) concat
      Buf.ByteArray.Owned(a2) concat
      Buf.ByteArray.Owned(a3)

    assert(buf.slice(20, 30) == buf.slice(20, 24)) // just last
    assert(buf.slice(12, 30) == buf.slice(12, 24)) // two bufs
    assert(buf.slice(8, 30) == buf.slice(8, 24)) // two bufs
  }

  test("Buf.concat.slice invalid args throws") {
    val a1 = Array.range(0, 8).map(_.toByte)
    val a2 = Array.range(8, 16).map(_.toByte)
    val a3 = Array.range(16, 24).map(_.toByte)
    val buf = Buf.ByteArray.Owned(a1) concat
      Buf.ByteArray.Owned(a2) concat
      Buf.ByteArray.Owned(a3)

    intercept[IllegalArgumentException] {
      buf.slice(-1, 0)
    }
    intercept[IllegalArgumentException] {
      buf.slice(1, 0)
    }
  }

  test("Buf.Utf8: English") {
    val buf = Buf.Utf8("Hello, world!")
    assert(buf.length == 13)
    val chars = Buf.ByteArray.Owned.extract(buf).toSeq.map(_.toChar)
    assert("Hello, world!".toSeq == chars)

    val Buf.Utf8(s) = buf
    assert(s == "Hello, world!")
  }

  test("Buf.Utf8: Japanese") {
    val buf = Buf.Utf8("￼￼￼￼￼￼￼")
    assert(buf.length == 21)
    val bytes = new Array[Byte](21)
    buf.write(bytes, 0)

    val expected = Array[Byte](
      -17, -65, -68, -17, -65, -68, -17,
      -65, -68, -17, -65, -68, -17, -65, -68,
      -17, -65, -68, -17, -65, -68)

    assert(bytes.toSeq == expected.toSeq)

    val Buf.Utf8(s) = buf
    assert(s == "￼￼￼￼￼￼￼")
  }

  test("Buf.Utf8.unapply with a Buf.ByteArray") {
    val str = "Hello, world!"
    val buf = Buf.Utf8(str)
    val Buf.Utf8(out) = buf
    assert(out == str)
  }

  test("Buf.Utf8.unapply with a non-Buf.ByteArray") {
    val buf = new Buf {
      protected val unsafeByteArrayBuf = None
      def slice(i: Int, j: Int) = throw new Exception("not implemented")
      def length = 12
      def write(output: Array[Byte], off: Int) =
        (off until off+length) foreach { i =>
          output(i) = 'a'.toByte
        }
    }

    val Buf.Utf8(str) = buf
    assert(str == "aaaaaaaaaaaa")
  }

  AllCharsets foreach { charset =>
    test("Buf.StringCoder: decoding to %s does not modify underlying byte buffer".format(charset.name)) {
      val coder = new Buf.StringCoder(charset) {}
      val hw = "Hello, world!"
      val bb = charset.encode(hw)
      val wrappedBuf = Buf.ByteBuffer.Owned(bb)
      val coder(_) = wrappedBuf
      val Buf.ByteBuffer.Owned(wbb) = wrappedBuf
      assert(wbb == bb)
      assert(wbb.position() == bb.position())
    }
  }

  AllCharsets foreach { charset =>
    test("Buf.StringCoder: %s charset can encode and decode an English phrase".format(charset.name)) {
      val coder = new Buf.StringCoder(charset) {}
      val phrase = "Hello, world!"
      val encoded = coder(phrase)
      val coder(out) = encoded
      assert(out == phrase)
    }
  }

  test("Buf.ByteBuffer") {
    val bytes = Array[Byte](111, 107, 116, 104, 101, 110)
    val bb = java.nio.ByteBuffer.wrap(bytes)
    val buf = Buf.ByteBuffer.Owned(bb)

    assert(Buf.ByteArray.Owned(bytes) == buf)

    val written = new Array[Byte](buf.length)
    buf.write(written, 0)
    assert(Arrays.equals(bytes, written))

    val offsetWritten = new Array[Byte](buf.length + 2)
    buf.write(offsetWritten, 2)
    assert(Arrays.equals(Array[Byte](0, 0) ++ bytes, offsetWritten))

    assert(Buf.ByteArray.Owned(bytes.drop(2).take(2)) == buf.slice(2, 4))
    // overflow slice
    assert(Buf.ByteArray.Owned(bytes.drop(2)) == buf.slice(2, 10))

    assert(Buf.ByteBuffer.Owned(java.nio.ByteBuffer.allocate(0)) == Buf.Empty)
    assert(Buf.ByteBuffer.Owned(java.nio.ByteBuffer.allocateDirect(0)) == Buf.Empty)

    val bytes2 = Array[Byte](1,2,3,4,5,6,7,8,9,0)
    val buf2 = Buf.ByteBuffer.Owned(java.nio.ByteBuffer.wrap(bytes2, 3, 4))
    assert(buf2 == Buf.ByteArray.Owned(Array[Byte](4,5,6,7)))

    val Buf.ByteBuffer.Owned(byteBuffer) = buf
    assert(byteBuffer == bb)
  }

  test("Buf.ByteBuffer.Shared.apply is safe") {
    val bytes = Array.fill(10)(0.toByte)
    val Buf.ByteBuffer.Owned(bb) = Buf.ByteBuffer.Shared(java.nio.ByteBuffer.wrap(bytes))
    bytes(0) = 10.toByte
    assert(bb.get(0) !== 10)
  }

  test("Buf.ByteBuffer.Direct.apply is unsafe") {
    val bytes = Array.fill(10)(0.toByte)
    val Buf.ByteBuffer.Owned(bb) = Buf.ByteBuffer.Owned(java.nio.ByteBuffer.wrap(bytes))
    bytes(0) = 10.toByte
    assert(bb.get(0) == 10)
  }

  test("Buf.ByteBuffer.Shared.unapply is safe") {
    val bytes = Array.fill(10)(0.toByte)
    val bb0 = java.nio.ByteBuffer.wrap(bytes)
    val Buf.ByteBuffer.Shared(bb1) = Buf.ByteBuffer.Owned(bb0)
    bb1.position(3)
    assert(bb0.position == 0)
  }

  test("Buf.ByteBuffer.Direct.unapply is unsafe") {
    val bytes = Array.fill(10)(0.toByte)
    val bb0 = java.nio.ByteBuffer.wrap(bytes)
    val Buf.ByteBuffer.Owned(bb1) = Buf.ByteBuffer.Owned(bb0)
    bytes(0) = 10.toByte
    assert(bb1.get(0) == 10.toByte)
  }

  test("Buf.ByteBuffer.unapply is readonly") {
    val bytes = Array.fill(10)(0.toByte)
    val bb0 = java.nio.ByteBuffer.wrap(bytes)
    val Buf.ByteBuffer(bb1) = Buf.ByteBuffer.Owned(bb0)
    assert(bb1.isReadOnly)
    bb1.position(3)
    assert(bb0.position == 0)
  }

  test("ByteArray.coerce(ByteArray)") {
    val orig = Buf.ByteArray.Owned(Array.fill(10)(0.toByte))
    val coerced = Buf.ByteArray.coerce(orig)
    assert(coerced eq orig)
  }

  test("ByteArray.coerce(ByteBuffer)") {
    val orig = Buf.ByteBuffer.Owned(java.nio.ByteBuffer.wrap(Array.fill(10)(0.toByte)))
    val coerced = Buf.ByteArray.coerce(orig)
    assert(coerced == orig)
  }

  test("ByteBuffer.coerce(ByteArray)") {
    val orig = Buf.ByteArray.Owned(Array.fill(10)(0.toByte))
    val coerced = Buf.ByteBuffer.coerce(orig)
    assert(coerced == orig)
  }

  test("ByteBuffer.coerce(ByteBuffer)") {
    val orig = Buf.ByteBuffer.Owned(java.nio.ByteBuffer.wrap(Array.fill(10)(0.toByte)))
    val coerced = Buf.ByteBuffer.coerce(orig)
    assert(coerced eq orig)
  }

  test("hash code, equals") {
    def ae(a: Buf, b: Buf) {
      assert(a == b)
      assert(a.hashCode == b.hashCode)
    }

    val string = "okthen"
    val bytes = Array[Byte](111, 107, 116, 104, 101, 110)
    val bbuf = Buf.ByteArray.Owned(bytes)

    ae(Buf.Utf8(string), bbuf)
    ae(Buf.Utf8(""), Buf.Empty)

    val concat = bbuf.slice(0, 3) concat bbuf.slice(3, 6)
    ae(concat, Buf.ByteArray.Owned(bytes))

    val shifted = new Array[Byte](bytes.length + 3)
    System.arraycopy(bytes, 0, shifted, 3, bytes.length)
    ae(Buf.Utf8(string), Buf.ByteArray.Owned(shifted, 3, 3+bytes.length))
  }

  check(Prop.forAll { (in: Int) =>
    val buf = Buf.U32BE(in)
    val Buf.U32BE(out, _) = buf

    val outByteBuf = java.nio.ByteBuffer.wrap(Buf.ByteArray.Owned.extract(buf))
    outByteBuf.order(java.nio.ByteOrder.BIG_ENDIAN)

    out == in && outByteBuf.getInt == in
  })

  check(Prop.forAll { (in: Int) =>
    val buf = Buf.U32LE(in)
    val Buf.U32LE(out, _) = buf

    val outByteBuf = java.nio.ByteBuffer.wrap(Buf.ByteArray.Owned.extract(buf))
    outByteBuf.order(java.nio.ByteOrder.LITTLE_ENDIAN)

    out == in && outByteBuf.getInt == in
  })

  check(Prop.forAll { (in: Long) =>
    val buf = Buf.U64BE(in)
    val Buf.U64BE(out, _) = buf

    val outByteBuf = java.nio.ByteBuffer.wrap(Buf.ByteArray.Owned.extract(buf))
    outByteBuf.order(java.nio.ByteOrder.BIG_ENDIAN)

    out == in && outByteBuf.getLong == in
  })

  check(Prop.forAll { (in: Long) =>
    val buf = Buf.U64LE(in)
    val Buf.U64LE(out, _) = buf

    val outByteBuf = java.nio.ByteBuffer.wrap(Buf.ByteArray.Owned.extract(buf))
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

    assert(be32 == Int.MaxValue)
    assert(be64 == Long.MaxValue)
    assert(le32 == Int.MinValue)
    assert(le64 == Long.MinValue)
    assert(rem == Buf.Empty)
  }

  test("concat two ConcatBufs") {
    val a1 = Array.range(0, 8).map(_.toByte)
    val a2 = Array.range(8, 16).map(_.toByte)

    val a3 = Array.range(16, 24).map(_.toByte)
    val a4 = Array.range(24, 32).map(_.toByte)

    val arr = a1 ++ a2 ++ a3 ++ a4

    val cbuf1 = Buf.ByteArray.Owned(a1) concat Buf.ByteArray.Owned(a2)
    val cbuf2 = Buf.ByteArray.Owned(a3) concat Buf.ByteArray.Owned(a4)
    val cbuf = cbuf1 concat cbuf2

    for (i <- 0 until arr.length; j <- i until arr.length) {
      val w = new Array[Byte](j-i)
      cbuf.slice(i, j).write(w, 0)
      assert(w.toSeq == arr.slice(i, j).toSeq)
    }
  }

  test("highly nested concat buffer shouldn't throw StackOverflowError") {
    val size = 50 * 1000
    val b = 'x'.toByte
    val bigBuf = (1 to size).foldLeft(Buf.Empty) {
      case (buf, _) => buf concat Buf.ByteArray.Owned(Array[Byte](b))
    }

    val expected = Array.fill(size) { 'x'.toByte }
    val output = Buf.ByteArray.Owned.extract(bigBuf)
    assert(bigBuf.length == size)
    assert(expected.toSeq == output.toSeq)

    val sliced = bigBuf.slice(1, size - 1)
    val size2 = size - 2
    val expected2 = Array.fill(size2) { 'x'.toByte }
    val output2 = Buf.ByteArray.Owned.extract(sliced)
    assert(sliced.length == size2)
    assert(expected2.toSeq == output2.toSeq)
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
      s => Buf.ByteArray.Owned(s.getBytes("UTF-8")),
      s => Buf.ByteBuffer.Owned(UTF_8.encode(CharBuffer.wrap(s))))

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

        assert(b1.length == k - i)
        assert(b2.length == j - i)
      }
    }
  }
}
