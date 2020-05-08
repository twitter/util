package com.twitter.io

import java.lang.{Double => JDouble, Float => JFloat}
import java.nio.charset.StandardCharsets
import org.scalacheck.Gen
import org.scalatest.FunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class ByteReaderTest extends FunSuite with ScalaCheckDrivenPropertyChecks {
  import ByteReader._

  def readerWith(bytes: Byte*): ByteReader = ByteReader(Buf.ByteArray.Owned(bytes.toArray))

  def maskMedium(i: Int): Int = i & 0x00ffffff

  test("readBytes increments position by the number of bytes returned") {
    val br = readerWith()
    assert(br.readBytes(4).length == 0)
    assert(br.remaining == 0)
  }

  test("readString underflow") {
    val br = readerWith()
    intercept[UnderflowException] { br.readString(1, StandardCharsets.UTF_8) }
  }

  test("readString")(forAll { (str1: String, str2: String) =>
    val bytes1 = str1.getBytes(StandardCharsets.UTF_8)
    val bytes2 = str2.getBytes(StandardCharsets.UTF_8)
    val br = readerWith((bytes1 ++ bytes2).toSeq: _*)
    assert(br.readString(bytes1.length, StandardCharsets.UTF_8) == str1)
    assert(br.readString(bytes2.length, StandardCharsets.UTF_8) == str2)
    intercept[UnderflowException] { br.readByte() }
  })

  test("readByte")(forAll { byte: Byte =>
    val br = readerWith(byte)
    assert(br.readByte() == byte)
    intercept[UnderflowException] { br.readByte() }
  })

  test("readShortBE")(forAll { s: Short =>
    val br = readerWith(
      ((s >> 8) & 0xff).toByte,
      ((s) & 0xff).toByte
    )
    // note, we need to cast here toShort so that the
    // MSB is interpreted as the sign bit.
    assert(br.readShortBE() == s)
    intercept[UnderflowException] { br.readByte() }
  })

  test("readShortLE")(forAll { s: Short =>
    val br = readerWith(
      ((s) & 0xff).toByte,
      ((s >> 8) & 0xff).toByte
    )

    // note, we need to cast here toShort so that the
    // MSB is interpreted as the sign bit.
    assert(br.readShortLE() == s)
    intercept[UnderflowException] { br.readByte() }
  })

  test("readUnsignedMediumBE")(forAll { m: Int =>
    val br = readerWith(
      ((m >> 16) & 0xff).toByte,
      ((m >> 8) & 0xff).toByte,
      ((m) & 0xff).toByte
    )
    assert(br.readUnsignedMediumBE() == maskMedium(m))
    intercept[UnderflowException] { br.readByte() }
  })

  test("readUnsignedMediumLE")(forAll { m: Int =>
    val br = readerWith(
      ((m) & 0xff).toByte,
      ((m >> 8) & 0xff).toByte,
      ((m >> 16) & 0xff).toByte
    )
    assert(br.readUnsignedMediumLE() == maskMedium(m))
    intercept[UnderflowException] { br.readByte() }
  })

  test("readIntBE")(forAll { i: Int =>
    val br = readerWith(
      ((i >> 24) & 0xff).toByte,
      ((i >> 16) & 0xff).toByte,
      ((i >> 8) & 0xff).toByte,
      ((i) & 0xff).toByte
    )
    assert(br.readIntBE() == i)
    intercept[UnderflowException] { br.readByte() }
  })

  test("readIntLE")(forAll { i: Int =>
    val br = readerWith(
      ((i) & 0xff).toByte,
      ((i >> 8) & 0xff).toByte,
      ((i >> 16) & 0xff).toByte,
      ((i >> 24) & 0xff).toByte
    )
    assert(br.readIntLE() == i)
    intercept[UnderflowException] { br.readByte() }
  })

  test("readLongBE")(forAll { l: Long =>
    val br = readerWith(
      ((l >> 56) & 0xff).toByte,
      ((l >> 48) & 0xff).toByte,
      ((l >> 40) & 0xff).toByte,
      ((l >> 32) & 0xff).toByte,
      ((l >> 24) & 0xff).toByte,
      ((l >> 16) & 0xff).toByte,
      ((l >> 8) & 0xff).toByte,
      ((l) & 0xff).toByte
    )
    assert(br.readLongBE() == l)
    intercept[UnderflowException] { br.readByte() }
  })

  test("readLongLE")(forAll { l: Long =>
    val br = readerWith(
      ((l) & 0xff).toByte,
      ((l >> 8) & 0xff).toByte,
      ((l >> 16) & 0xff).toByte,
      ((l >> 24) & 0xff).toByte,
      ((l >> 32) & 0xff).toByte,
      ((l >> 40) & 0xff).toByte,
      ((l >> 48) & 0xff).toByte,
      ((l >> 56) & 0xff).toByte
    )
    assert(br.readLongLE() == l)
    intercept[UnderflowException] { br.readByte() }
  })

  test("readUnsignedByte")(forAll { b: Byte =>
    val br = new ByteReaderImpl(Buf.Empty) { override def readByte() = b }
    assert(br.readUnsignedByte() == (b & 0xff))
  })

  test("readUnsignedShortBE")(forAll { s: Short =>
    val br = new ByteReaderImpl(Buf.Empty) { override def readShortBE() = s }
    assert(br.readUnsignedShortBE() == (s & 0xffff))
  })

  test("readUnsignedShortLE")(forAll { s: Short =>
    val br = new ByteReaderImpl(Buf.Empty) { override def readShortLE() = s }
    assert(br.readUnsignedShortLE() == (s & 0xffff))
  })

  test("readMediumBE")(forAll { i: Int =>
    val m = maskMedium(i)
    val br = new ByteReaderImpl(Buf.Empty) { override def readUnsignedMediumBE() = m }
    val expected = if (m > SignedMediumMax) m | 0xff000000 else m
    assert(br.readMediumBE() == expected)
  })

  test("readMediumLE")(forAll { i: Int =>
    val m = maskMedium(i)
    val br = new ByteReaderImpl(Buf.Empty) { override def readUnsignedMediumLE() = m }
    val expected = if (m > SignedMediumMax) m | 0xff000000 else m
    assert(br.readMediumLE() == expected)
  })

  test("readUnsignedIntBE")(forAll { i: Int =>
    val br = new ByteReaderImpl(Buf.Empty) { override def readIntBE() = i }
    assert(br.readUnsignedIntBE() == (i & 0xFFFFFFFFL))
  })

  test("readUnsignedIntLE")(forAll { i: Int =>
    val br = new ByteReaderImpl(Buf.Empty) { override def readIntLE() = i }
    assert(br.readUnsignedIntLE() == (i & 0xFFFFFFFFL))
  })

  val uInt64s: Gen[BigInt] = Gen
    .chooseNum(Long.MinValue, Long.MaxValue)
    .map(x => BigInt(x) + BigInt(2).pow(63))

  test("readUnsignedLongBE")(forAll(uInt64s) { bi: BigInt =>
    val br = readerWith(
      ((bi >> 56) & 0xff).toByte,
      ((bi >> 48) & 0xff).toByte,
      ((bi >> 40) & 0xff).toByte,
      ((bi >> 32) & 0xff).toByte,
      ((bi >> 24) & 0xff).toByte,
      ((bi >> 16) & 0xff).toByte,
      ((bi >> 8) & 0xff).toByte,
      ((bi) & 0xff).toByte
    )
    assert(br.readUnsignedLongBE() == bi)
    intercept[UnderflowException] { br.readByte() }
  })

  test("readUnsignedLongLE")(forAll(uInt64s) { bi1: BigInt =>
    val bi = bi1.abs
    val br = readerWith(
      ((bi) & 0xff).toByte,
      ((bi >> 8) & 0xff).toByte,
      ((bi >> 16) & 0xff).toByte,
      ((bi >> 24) & 0xff).toByte,
      ((bi >> 32) & 0xff).toByte,
      ((bi >> 40) & 0xff).toByte,
      ((bi >> 48) & 0xff).toByte,
      ((bi >> 56) & 0xff).toByte
    )
    assert(br.readUnsignedLongLE() == bi)
    intercept[UnderflowException] { br.readByte() }
  })

  // .equals is required to handle NaN
  test("readFloatBE")(forAll { i: Int =>
    val br = new ByteReaderImpl(Buf.Empty) { override def readIntBE() = i }
    assert(br.readFloatBE().equals(JFloat.intBitsToFloat(i)))
  })

  test("readFloatLE")(forAll { i: Int =>
    val br = new ByteReaderImpl(Buf.Empty) { override def readIntLE() = i }
    assert(br.readFloatLE().equals(JFloat.intBitsToFloat(i)))
  })

  test("readDoubleBE")(forAll { l: Long =>
    val br = new ByteReaderImpl(Buf.Empty) { override def readLongBE() = l }
    assert(br.readDoubleBE().equals(JDouble.longBitsToDouble(l)))
  })

  test("readDoubleLE")(forAll { l: Long =>
    val br = new ByteReaderImpl(Buf.Empty) { override def readLongLE() = l }
    assert(br.readDoubleLE().equals(JDouble.longBitsToDouble(l)))
  })

  test("readBytes")(forAll { bytes: Array[Byte] =>
    val buf = Buf.ByteArray.Owned(bytes ++ bytes)
    val br = ByteReader(buf)
    intercept[IllegalArgumentException] { br.readBytes(-1) }
    assert(br.readBytes(bytes.length) == Buf.ByteArray.Owned(bytes))
    assert(br.readBytes(bytes.length) == Buf.ByteArray.Owned(bytes))
    assert(br.readBytes(1) == Buf.Empty)
  })

  test("readAll")(forAll { bytes: Array[Byte] =>
    val buf = Buf.ByteArray.Owned(bytes ++ bytes)
    val br = ByteReader(buf)
    assert(br.readAll() == Buf.ByteArray.Owned(bytes ++ bytes))
    assert(br.readAll() == Buf.Empty)
  })

  test("underflow if too many bytes are skipped") {
    val br = ByteReader(Buf.ByteArray.Owned(new Array[Byte](2)))
    br.skip(2)
    intercept[UnderflowException] {
      br.skip(2)
    }
  }

  test("remainingUntil") {
    forAll { (bytes: Array[Byte], byte: Byte) =>
      val buf = Buf.ByteArray.Owned(bytes ++ Array(byte) ++ bytes)
      val br = ByteReader(buf)

      val remainingBefore = br.remaining
      val until = br.remainingUntil(byte)
      assert(remainingBefore == br.remaining)
      val before = br.readBytes(until)
      val pivot = br.readByte()
      val after = br.readAll()

      val expected = before.concat(Buf.ByteArray.Owned(Array(pivot))).concat(after)
      assert(pivot == byte && expected == buf)
    }

    assert(ByteReader(Buf.Empty).remainingUntil(0x0) == -1)

    val reader = readerWith(0x1, 0x2, 0x3)
    assert(0 == reader.remainingUntil(0x1))
    assert(1 == reader.remainingUntil(0x2))
    assert(2 == reader.remainingUntil(0x3))

    assert(0x1 == reader.readByte())
    assert(2 == reader.remaining)
    assert(-1 == reader.remainingUntil(0x1))
    assert(0 == reader.remainingUntil(0x2))
    assert(1 == reader.remainingUntil(0x3))
    assert(-1 == reader.remainingUntil(0x4))
    assert(2 == reader.remaining)

    assert(0x2 == reader.readByte())
    assert(0x3 == reader.readByte())
    assert(0 == reader.remaining)
    assert(-1 == reader.remainingUntil(0x3))
  }
}
