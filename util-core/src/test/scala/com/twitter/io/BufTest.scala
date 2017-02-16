package com.twitter.io

import com.twitter.io.Buf.{ByteArray, Processor}
import java.nio.CharBuffer
import java.nio.charset.{StandardCharsets => JChar}
import java.util.Arrays
import org.junit.runner.RunWith
import org.scalacheck.{Arbitrary, Gen, Prop}
import org.scalatest.FunSuite
import org.scalatest.junit.{AssertionsForJUnit, JUnitRunner}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.{Checkers, GeneratorDrivenPropertyChecks}
import scala.collection.mutable

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

  private[this] val bufs: Seq[Buf] =
    Seq(
      Buf.Empty,
      Buf.ByteArray.Owned(Array[Byte](0, 1, 2)),
      Buf.ByteBuffer.Owned(java.nio.ByteBuffer.wrap(Array[Byte](0, 1, 2))),
      Buf.Utf8("abc"),
      Buf.Utf8("abc").concat(Buf.Utf8("def"))
    )

  test("Buf.slice validates inputs must be non-negative") {
    bufs.foreach { buf =>
      withClue(buf.toString) {
        intercept[IllegalArgumentException] {
          buf.slice(-1, 0)
        }
        intercept[IllegalArgumentException] {
          buf.slice(0, -1)
        }
      }
    }
  }

  test("Buf.slice returns empty buf") {
    bufs.foreach { buf =>
      withClue(buf.toString) {
        assert(Buf.Empty == buf.slice(5, 0))
        assert(Buf.Empty == buf.slice(buf.length + 1, buf.length + 2))
      }
    }
  }

  test("Buf.slice is no-op when from and until are covering") {
    bufs.foreach { buf =>
      withClue(buf.toString) {
        assert(buf == buf.slice(0, buf.length))
        assert(buf == buf.slice(0, buf.length + 1))
      }
    }
  }

  test("Buf.write validates output array is large enough") {
    // we skip Empty because we cannot create an array
    // too small for this test.
    bufs.filterNot(_.isEmpty).foreach { buf =>
      withClue(buf.toString) {
        val notBigEnough = new Array[Byte](buf.length - 1)
        intercept[IllegalArgumentException] {
          buf.write(notBigEnough, 0)
        }
      }
    }
  }

  test("Buf.write validates offset") {
    bufs.foreach { buf =>
      withClue(buf.toString) {
        val bigEnough = new Array[Byte](buf.length)
        intercept[IllegalArgumentException] {
          // negative offsets are not allowed
          buf.write(bigEnough, -1)
          // not enough room in the output after the offset
          buf.write(bigEnough, 2)
        }
      }
    }
  }

  test("Buf.ByteArray.slice") {
    val arr = Array.range(0, 16).map(_.toByte)
    val buf = Buf.ByteArray.Owned(arr)
    for (i <- 0 until arr.length; j <- i until arr.length) {
      val w = new Array[Byte](j-i)
      buf.slice(i, j).write(w, 0)
      assert(w.toSeq == arr.slice(i, j).toSeq)
    }
  }

  test("Buf.process handles bad input arguments") {
    val processor = new Buf.Processor {
      def apply(byte: Byte): Boolean = false
    }
    bufs.foreach { buf =>
      withClue(buf) {
        intercept[IllegalArgumentException] {
          buf.process(-1, 5, processor)
        }
        intercept[IllegalArgumentException] {
          buf.process(0, -1, processor)
        }
      }
    }
  }

  test("Buf.process handles empty inputs") {
    val processor = new Buf.Processor {
      def apply(byte: Byte): Boolean = false
    }
    bufs.foreach { buf =>
      withClue(buf) {
        assert(-1 == buf.process(1, 0, processor))
        assert(-1 == buf.process(buf.length, buf.length + 1, processor))
      }
    }
  }

  test("Buf.process handles large until") {
    val processor = new Buf.Processor {
      def apply(byte: Byte): Boolean = true
    }
    bufs.foreach { buf =>
      withClue(buf) {
        assert(-1 == buf.process(0, buf.length, processor))
        assert(-1 == buf.process(0, buf.length + 1, processor))
      }
    }
  }

  test("Buf.process returns -1 when fully processed") {
    def assertAllProcessed(buf: Buf): Unit = {
      var n = 0
      val processor = new Buf.Processor {
        def apply(byte: Byte): Boolean = {
          n += 1
          true
        }
      }
      assert(-1 == buf.process(processor))
      assert(n == buf.length)
    }
    val arr = Array.range(0, 9).map(_.toByte)
    val byteArray = Buf.ByteArray.Owned(arr, 3, 8)
    assertAllProcessed(byteArray)

    val byteBuffer = Buf.ByteBuffer.Owned(java.nio.ByteBuffer.wrap(arr))
    assertAllProcessed(byteBuffer)

    val composite = byteArray.concat(byteBuffer)
    assertAllProcessed(composite)
  }

  test("Buf.process from and until returns -1 when fully processed") {
    def assertAllProcessed(
      expected: Array[Byte],
      buf: Buf,
      from: Int,
      until: Int
    ): Unit = {
      var seen = new mutable.ListBuffer[Byte]()
      val processor = new Buf.Processor {
        def apply(byte: Byte): Boolean = {
          seen += byte
          true
        }
      }
      assert(-1 == buf.process(from, until, processor))
      assert(expected.toSeq == seen)
    }
    val arr = Array.range(0, 9).map(_.toByte)

    val byteArrayOffset = 3
    val byteArray = Buf.ByteArray.Owned(arr, 3, 8)
    assertAllProcessed(
      Array[Byte](5, 6, 7),
      byteArray, 2, 5)

    val byteBuffer = Buf.ByteBuffer.Owned(java.nio.ByteBuffer.wrap(arr))
    assertAllProcessed(
      Array[Byte](2, 3, 4),
      byteBuffer, 2, 5)

    val composite = byteArray.concat(byteBuffer)
    assertAllProcessed(
      Array[Byte](7, 0, 1, 2),
      composite, 4, 8)
    assertAllProcessed(
      Array[Byte](1, 2, 3),
      composite, 6, 9)
  }

  private def assertProcessReturns0WhenProcessorReturnsFalse(buf: Buf) =
    assert(0 == buf.process(new Buf.Processor {
      def apply(byte: Byte): Boolean = false
    }))

  test("Buf.process returns index where processing stopped") {
    val arr = Array.range(0, 9).map(_.toByte)
    val byteArray = Buf.ByteArray.Owned(arr, 3, 8)
    assertProcessReturns0WhenProcessorReturnsFalse(byteArray)
    val stopAt5 = new Buf.Processor {
      def apply(byte: Byte): Boolean = byte < 5
    }
    assert(2 == byteArray.process(stopAt5))
    assert(2 == byteArray.process(0, 5, stopAt5))
    assert(2 == byteArray.process(2, 5, stopAt5))
    assert(3 == byteArray.process(3, 5, stopAt5))
    assert(-1 == byteArray.process(0, 2, stopAt5))

    val byteBuffer = Buf.ByteBuffer.Owned(java.nio.ByteBuffer.wrap(arr))
    assertProcessReturns0WhenProcessorReturnsFalse(byteBuffer)
    assert(5 == byteBuffer.process(stopAt5))
    assert(5 == byteBuffer.process(0, 6, stopAt5))
    assert(7 == byteBuffer.process(7, 10, stopAt5))
    assert(-1 == byteBuffer.process(0, 5, stopAt5))

    val composite =
      Buf.ByteArray.Owned(arr, 0, 1).concat(
        Buf.ByteArray.Owned(arr, 1, 10))
    assertProcessReturns0WhenProcessorReturnsFalse(composite)
    assert(5 == composite.process(stopAt5))
    assert(5 == composite.process(4, 6, stopAt5))
    assert(6 == composite.process(6, 7, stopAt5))
    assert(-1 == composite.process(0, 5, stopAt5))
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
      buf.slice(0, -1)
    }
  }

  test("Buf.get") {
    // compare slice/write to get
    val out = new Array[Byte](1)
    bufs
      .filter(b => b.length >= 2)
      .foreach { buf =>
        withClue(buf) {
          buf.slice(0, 1).write(out, 0)
          assert(out(0) == buf.get(0))

          buf.slice(1, 2).write(out, 0)
          assert(out(0) == buf.get(1))

          buf.slice(buf.length-1, buf.length).write(out, 0)
          assert(out(0) == buf.get(buf.length-1))
        }
      }
  }

  test("Buf.get over the length") {
    bufs
      .filter(b => b.length >= 1)
      .foreach { buf =>
        withClue(buf) {
          intercept[IndexOutOfBoundsException] {
            buf.get(buf.length)
          }
        }
      }
  }

  test("Buf.ByteArray.get") {
    // ba => [2, 3]
    val ba = Buf.ByteArray.Owned(Array[Byte](0, 1, 2, 3, 4), 2, 4)
    assert(2 == ba.length)
    assert(2 == ba.get(0))
    assert(3 == ba.get(1))
    intercept[IndexOutOfBoundsException] {
      ba.get(2)
    }
  }

  test("Buf.apply with no Bufs") {
    assert(Buf.Empty == Buf(Seq.empty))
  }

  test("Buf.apply with 1 Buf") {
    assert(Buf.Empty == Buf(Seq(Buf.Empty)))
    val abc = Buf.Utf8("abc")
    assert(abc == Buf(Seq(abc)))
  }

  test("Buf.apply with 2 or more Bufs") {
    val abc = Buf.Utf8("abc")
    val xyz = Buf.Utf8("xyz")
    assert(Buf.Utf8("abcxyz") == Buf(Seq(abc, xyz)))
    assert(Buf.Utf8("abcxyzabc") == Buf(Seq(abc, xyz, abc)))
  }

  test("Buf.apply ignores empty Bufs") {
    val abc = Buf.Utf8("abc")
    assert(Buf.Utf8("abcabc") == Buf(Seq(abc, Buf.Empty, abc)))
  }

  test("Buf.Composite.unapply") {
    Buf.Empty match {
      case Buf.Composite(_) => fail()
      case _ =>
    }

    val abc = Buf.Utf8("abc")
    abc match {
      case Buf.Composite(_) => fail()
      case _ =>
    }

    val xyz = Buf.Utf8("xyz")
    abc.concat(xyz) match {
      case Buf.Composite(bufs) =>
        assert(bufs == IndexedSeq(abc, xyz))
      case _ => fail()
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
      def slice(i: Int, j: Int): Buf = throw new Exception("not implemented")
      def length: Int = 12
      def write(output: Array[Byte], off: Int): Unit =
        (off until off+length) foreach { i =>
          output(i) = 'a'.toByte
        }
      def get(index: Int): Byte = ???
      def process(from: Int, until: Int, processor: Processor): Int = ???
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

  test("hash code memoization") {
    var count = 0
    val buf = new Buf {
      def write(output: Array[Byte], off: Int): Unit = {}
      def length: Int = 0
      def slice(from: Int, until: Int): Buf = this
      protected def unsafeByteArrayBuf: Option[ByteArray] = None
      override protected def computeHashCode: Int = {
        count += 1
        super.computeHashCode
      }
      def get(index: Int): Byte = ???
      def process(from: Int, until: Int, processor: Processor): Int = ???
    }

    assert(buf.hashCode != 0) // This is true for the used FNV-1 hash implementation.
    assert(count == 1)
    buf.hashCode
    assert(count == 1)
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

  test("concat two composite bufs") {
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
    val size = 100
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

  test("Buf.concat of Composite") {
    // Note: by design, not all generated Buf instances will be a Composite to make
    // sure they are properly handled when doing multiple concat operations.
    val bufGen: Gen[Buf] = for {
      n <- Gen.choose(1, 6)
      buf <- arbBuf.arbitrary
    } yield Buf(Seq.fill(n)(buf))

    forAll(Gen.listOf(bufGen)) { bufs: List[Buf] =>
      val concatLeft = bufs.foldLeft(Buf.Empty) { (l, r) => l.concat(r) }
      val concatRight = bufs.foldRight(Buf.Empty) { (l, r) => l.concat(r) }
      val constructor = Buf(bufs)
      assert(constructor == concatLeft)
      assert(constructor == concatRight)
    }
  }
}
