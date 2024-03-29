package com.twitter.io

import java.lang.{Double => JDouble, Float => JFloat}
import java.nio.charset.StandardCharsets
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.funsuite.AnyFunSuite

final class BufByteWriterTest extends AnyFunSuite with ScalaCheckDrivenPropertyChecks {
  import ByteWriter.OverflowException

  private[this] def assertIndex(bw: ByteWriter, index: Int): Unit = bw match {
    case bw: AbstractBufByteWriterImpl => assert(bw.index == index)
    case other => fail(s"Unexpected ByteWriter representation: $other")
  }

  def testWriteString(name: String, bwFactory: Int => BufByteWriter, overflowOK: Boolean): Unit = {
    test(s"$name: writeString")(forAll { (str1: String, str2: String) =>
      val byteCount1 = str1.getBytes(StandardCharsets.UTF_8).length
      val byteCount2 = str2.getBytes(StandardCharsets.UTF_8).length

      val bw = bwFactory(byteCount1 + byteCount2)
      val buf =
        bw.writeString(str1, StandardCharsets.UTF_8)
          .writeString(str2, StandardCharsets.UTF_8)
          .owned()

      if (!overflowOK) intercept[OverflowException] { bw.writeByte(0xff) }

      val result = Buf.Utf8.unapply(buf)
      assert(result == Some(str1 + str2))
      assertIndex(bw, byteCount1 + byteCount2)
    })
  }

  def testWriteByte(name: String, bwFactory: () => BufByteWriter, overflowOK: Boolean): Unit =
    test(s"$name: writeByte")(forAll { (byte: Byte) =>
      val bw = bwFactory()
      val buf = bw.writeByte(byte).owned()

      if (!overflowOK) intercept[OverflowException] { bw.writeByte(byte) }

      assert(buf == Buf.ByteArray.Owned(Array(byte)))
    })

  def testWriteShort(name: String, bwFactory: () => BufByteWriter, overflowOK: Boolean): Unit =
    test(s"$name: writeShort{BE,LE}")(forAll { (s: Short) =>
      val be = bwFactory().writeShortBE(s)
      val le = bwFactory().writeShortLE(s)

      if (!overflowOK) {
        intercept[OverflowException] { be.writeByte(0xff) }
        intercept[OverflowException] { be.writeByte(0xff) }
      }

      val arr = Array[Byte](
        ((s >> 8) & 0xff).toByte,
        ((s) & 0xff).toByte
      )

      assert(be.owned() == Buf.ByteArray.Owned(arr))
      assert(le.owned() == Buf.ByteArray.Owned(arr.reverse))
      assertIndex(be, 2)
      assertIndex(le, 2)
    })

  def testWriteMedium(name: String, bwFactory: () => BufByteWriter, overflowOK: Boolean): Unit =
    test(s"$name: writeMedium{BE,LE}")(forAll { (m: Int) =>
      val be = bwFactory().writeMediumBE(m)
      val le = bwFactory().writeMediumLE(m)

      if (!overflowOK) {
        intercept[OverflowException] { be.writeByte(0xff) }
        intercept[OverflowException] { be.writeByte(0xff) }
      }

      val arr = Array[Byte](
        ((m >> 16) & 0xff).toByte,
        ((m >> 8) & 0xff).toByte,
        ((m) & 0xff).toByte
      )

      assert(be.owned() == Buf.ByteArray.Owned(arr))
      assert(le.owned() == Buf.ByteArray.Owned(arr.reverse))
      assertIndex(be, 3)
      assertIndex(le, 3)
    })

  def testWriteInt(name: String, bwFactory: () => BufByteWriter, overflowOK: Boolean): Unit =
    test(s"$name: writeInt{BE,LE}")(forAll { (i: Int) =>
      val be = bwFactory().writeIntBE(i)
      val le = bwFactory().writeIntLE(i)

      if (!overflowOK) {
        intercept[OverflowException] { be.writeByte(0xff) }
        intercept[OverflowException] { be.writeByte(0xff) }
      }

      val arr = Array[Byte](
        ((i >> 24) & 0xff).toByte,
        ((i >> 16) & 0xff).toByte,
        ((i >> 8) & 0xff).toByte,
        ((i) & 0xff).toByte
      )

      assert(be.owned() == Buf.ByteArray.Owned(arr))
      assert(le.owned() == Buf.ByteArray.Owned(arr.reverse))
      assertIndex(be, 4)
      assertIndex(le, 4)
    })

  def testWriteLong(name: String, bwFactory: () => BufByteWriter, overflowOK: Boolean): Unit =
    test(s"$name: writeLong{BE,LE}")(forAll { (l: Long) =>
      val be = bwFactory().writeLongBE(l)
      val le = bwFactory().writeLongLE(l)

      if (!overflowOK) {
        intercept[OverflowException] { be.writeByte(0xff) }
        intercept[OverflowException] { be.writeByte(0xff) }
      }

      val arr = Array[Byte](
        ((l >> 56) & 0xff).toByte,
        ((l >> 48) & 0xff).toByte,
        ((l >> 40) & 0xff).toByte,
        ((l >> 32) & 0xff).toByte,
        ((l >> 24) & 0xff).toByte,
        ((l >> 16) & 0xff).toByte,
        ((l >> 8) & 0xff).toByte,
        ((l) & 0xff).toByte
      )

      assert(be.owned() == Buf.ByteArray.Owned(arr))
      assert(le.owned() == Buf.ByteArray.Owned(arr.reverse))
      assertIndex(be, 8)
      assertIndex(le, 8)
    })

  def testWriteFloat(name: String, bwFactory: () => BufByteWriter, overflowOK: Boolean): Unit =
    test(s"$name: writeFloat{BE,LE}")(forAll { (f: Float) =>
      val be = bwFactory().writeFloatBE(f)
      val le = bwFactory().writeFloatLE(f)

      if (!overflowOK) {
        intercept[OverflowException] { be.writeByte(0xff) }
        intercept[OverflowException] { be.writeByte(0xff) }
      }

      val i = JFloat.floatToIntBits(f)

      val arr = Array[Byte](
        ((i >> 24) & 0xff).toByte,
        ((i >> 16) & 0xff).toByte,
        ((i >> 8) & 0xff).toByte,
        ((i) & 0xff).toByte
      )

      assert(be.owned() == Buf.ByteArray.Owned(arr))
      assert(le.owned() == Buf.ByteArray.Owned(arr.reverse))
      assertIndex(be, 4)
      assertIndex(le, 4)
    })

  def testWriteDouble(name: String, bwFactory: () => BufByteWriter, overflowOK: Boolean): Unit =
    test(s"$name: writeDouble{BE,LE}")(forAll { (d: Double) =>
      val be = bwFactory().writeDoubleBE(d)
      val le = bwFactory().writeDoubleLE(d)

      if (!overflowOK) {
        intercept[OverflowException] { be.writeByte(0xff) }
        intercept[OverflowException] { be.writeByte(0xff) }
      }

      val l = JDouble.doubleToLongBits(d)

      val arr = Array[Byte](
        ((l >> 56) & 0xff).toByte,
        ((l >> 48) & 0xff).toByte,
        ((l >> 40) & 0xff).toByte,
        ((l >> 32) & 0xff).toByte,
        ((l >> 24) & 0xff).toByte,
        ((l >> 16) & 0xff).toByte,
        ((l >> 8) & 0xff).toByte,
        ((l) & 0xff).toByte
      )

      assert(be.owned() == Buf.ByteArray.Owned(arr))
      assert(le.owned() == Buf.ByteArray.Owned(arr.reverse))
      assertIndex(be, 8)
      assertIndex(le, 8)
    })

  // FIXED
  test("index initialized to zero") {
    assertIndex(BufByteWriter.fixed(1), 0)
  }

  test("trims unwritten bytes") {
    assert(BufByteWriter.fixed(5).owned().length == 0)
    assert(BufByteWriter.fixed(5).writeIntBE(1).owned().length == 4)
    assert(BufByteWriter.fixed(4).writeIntBE(1).owned().length == 4)
  }

  testWriteString("fixed", size => BufByteWriter.fixed(size), overflowOK = false)
  testWriteByte("fixed", () => BufByteWriter.fixed(1), overflowOK = false)
  testWriteShort("fixed", () => BufByteWriter.fixed(2), overflowOK = false)
  testWriteMedium("fixed", () => BufByteWriter.fixed(3), overflowOK = false)
  testWriteInt("fixed", () => BufByteWriter.fixed(4), overflowOK = false)
  testWriteLong("fixed", () => BufByteWriter.fixed(8), overflowOK = false)
  testWriteFloat("fixed", () => BufByteWriter.fixed(4), overflowOK = false)
  testWriteDouble("fixed", () => BufByteWriter.fixed(8), overflowOK = false)

  test("fixed: writeBytes(Array[Byte])")(forAll { (bytes: Array[Byte]) =>
    val bw = BufByteWriter.fixed(bytes.length)
    val buf = bw.writeBytes(bytes).owned()
    intercept[OverflowException] { bw.writeByte(0xff) }
    assert(buf == Buf.ByteArray.Owned(bytes))
    assertIndex(bw, bytes.length)
  })

  test("fixed: writeBytes(Array[Byte]) 2 times")(forAll { (bytes: Array[Byte]) =>
    val bw = BufByteWriter.fixed(bytes.length * 2)
    val buf = bw.writeBytes(bytes).writeBytes(bytes).owned()
    intercept[OverflowException] { bw.writeByte(0xff) }

    assert(buf == Buf.ByteArray.Owned(bytes ++ bytes))
    assertIndex(bw, bytes.length * 2)
  })

  test("fixed: writeBytes(Buf)")(forAll { (arr: Array[Byte]) =>
    val bytes = Buf.ByteArray.Owned(arr)
    val bw = BufByteWriter.fixed(bytes.length)
    val buf = bw.writeBytes(bytes).owned()
    intercept[OverflowException] { bw.writeByte(0xff) }
    assert(buf == bytes)
    assertIndex(bw, bytes.length)
  })

  test("fixed: writeBytes(Buf) 2 times")(forAll { (arr: Array[Byte]) =>
    val bytes = Buf.ByteArray.Owned(arr)
    val bw = BufByteWriter.fixed(bytes.length * 2)
    val buf = bw.writeBytes(bytes).writeBytes(bytes).owned()
    intercept[OverflowException] { bw.writeByte(0xff) }
    assert(buf == bytes.concat(bytes))
    assertIndex(bw, bytes.length * 2)
  })

  // DYNAMIC
  test("dynamic: writeByte with initial size 0 should throw exception") {
    intercept[IllegalArgumentException] { BufByteWriter.dynamic(0) }
  }

  testWriteString("dynamic", _ => BufByteWriter.dynamic(1), overflowOK = true)
  testWriteByte("dynamic", () => BufByteWriter.dynamic(1), overflowOK = true)
  testWriteShort("dynamic", () => BufByteWriter.dynamic(1), overflowOK = true)
  testWriteMedium("dynamic", () => BufByteWriter.dynamic(2), overflowOK = true)
  testWriteInt("dynamic", () => BufByteWriter.dynamic(3), overflowOK = true)
  testWriteLong("dynamic", () => BufByteWriter.dynamic(20), overflowOK = true)
  testWriteFloat("dynamic", () => BufByteWriter.dynamic(4), overflowOK = true)
  testWriteDouble("dynamic", () => BufByteWriter.dynamic(), overflowOK = true)
  testWriteLong(
    "dynamic, must grow multiple times",
    () => BufByteWriter.dynamic(1),
    overflowOK = true
  )

  test("dynamic: writeBytes(Array[Byte])")(forAll { (bytes: Array[Byte]) =>
    val bw = BufByteWriter.dynamic()
    val buf = bw.writeBytes(bytes).owned()
    assert(buf == Buf.ByteArray.Owned(bytes))
  })

  test("dynamic: writeBytes(Buf)")(forAll { (arr: Array[Byte]) =>
    val bytes = Buf.ByteArray.Owned(arr)
    val bw = BufByteWriter.dynamic()
    val buf = bw.writeBytes(bytes).owned()
    assert(buf == bytes)
  })

  test("dynamic: writeBytes(Array[Byte]) 3 times")(forAll { (bytes: Array[Byte]) =>
    val bw = BufByteWriter.dynamic()
    val buf = bw
      .writeBytes(bytes)
      .writeBytes(bytes)
      .writeBytes(bytes)
      .owned()
    assert(buf == Buf.ByteArray.Owned(bytes ++ bytes ++ bytes))
  })

  test("dynamic: writeBytes(Buf) 3 times")(forAll { (arr: Array[Byte]) =>
    val bytes = Buf.ByteArray.Owned(arr)
    val bw = BufByteWriter.dynamic()
    val buf = bw
      .writeBytes(bytes)
      .writeBytes(bytes)
      .writeBytes(bytes)
      .owned()
    assert(buf == bytes.concat(bytes).concat(bytes))
  })

  // Requires additional heap space to run.
  // Pass JVM option '-Xmx8g'.
  /*test("dynamic: try to write more than Int.MaxValue -2 bytes") {
    val bw = ByteWriter.dynamic()
    val bytes = new Array[Byte](Int.MaxValue - 2)
    bw.writeBytes(bytes)
    intercept[OverflowException] { bw.writeByte(0xff) }
  }*/
}
