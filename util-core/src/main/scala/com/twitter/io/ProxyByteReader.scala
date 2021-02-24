package com.twitter.io

import java.nio.charset.Charset

/**
 * A proxy [[ByteReader]] that forwards all calls to another [[ByteReader]].
 * This is useful if you want to wrap-but-modify an existing [[ByteReader]].
 */
abstract class ProxyByteReader(underlying: ByteReader) extends ByteReader {

  def remaining: Int = underlying.remaining

  def remainingUntil(byte: Byte): Int = underlying.remainingUntil(byte)

  def readByte(): Byte = underlying.readByte()

  def readUnsignedByte(): Short = underlying.readUnsignedByte()

  def readShortBE(): Short = underlying.readShortBE()

  def readShortLE(): Short = underlying.readShortLE()

  def readUnsignedShortBE(): Int = underlying.readUnsignedShortBE()

  def readUnsignedShortLE(): Int = underlying.readUnsignedShortLE()

  def readMediumBE(): Int = underlying.readMediumBE()

  def readMediumLE(): Int = underlying.readMediumLE()

  def readUnsignedMediumBE(): Int = underlying.readUnsignedMediumBE()

  def readUnsignedMediumLE(): Int = underlying.readUnsignedMediumLE()

  def readIntBE(): Int = underlying.readIntBE()

  def readIntLE(): Int = underlying.readIntLE()

  def readUnsignedIntBE(): Long = underlying.readUnsignedIntBE()

  def readUnsignedIntLE(): Long = underlying.readUnsignedIntLE()

  def readLongBE(): Long = underlying.readLongBE()

  def readLongLE(): Long = underlying.readLongLE()

  def readUnsignedLongBE(): BigInt = underlying.readUnsignedLongBE()

  def readUnsignedLongLE(): BigInt = underlying.readUnsignedLongLE()

  def readFloatBE(): Float = underlying.readFloatBE()

  def readFloatLE(): Float = underlying.readFloatLE()

  def readDoubleBE(): Double = underlying.readDoubleBE()

  def readDoubleLE(): Double = underlying.readDoubleLE()

  def readBytes(n: Int): Buf = underlying.readBytes(n)

  def readString(bytes: Int, charset: Charset): String = underlying.readString(bytes, charset)

  def skip(n: Int): Unit = underlying.skip(n)

  def readAll(): Buf = underlying.readAll()

  def process(from: Int, until: Int, processor: Buf.Processor): Int =
    underlying.process(from, until, processor)

  def process(processor: Buf.Processor): Int =
    underlying.process(processor)

  def close(): Unit = underlying.close()
}
