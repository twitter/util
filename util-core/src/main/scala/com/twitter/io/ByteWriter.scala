package com.twitter.io

import java.lang.{Double => JDouble, Float => JFloat}
import java.nio.charset.Charset

object ByteWriter {
  /**
   * Indicates there isn't enough room to write into
   * a [[ByteWriter]].
   */
  class OverflowException(msg: String) extends Exception(msg)
}

/**
 * [[ByteWriter]]s allow efficient encoding to a buffer. Concatenating
 * [[Buf]]s together is a common pattern for codecs (especially on the encode
 * path), but using the `concat` method on [[Buf]] results in a suboptimal
 * representation.  In many cases, a [[ByteWriter]] not only allows for a more
 * optimal representation (i.e., sequential accessible out regions), but also
 * allows for writes to avoid allocations.  What this means in practice is that
 * builders are stateful. Assume that the builder implementations are ""not""
 * threadsafe unless otherwise noted.
 */
trait ByteWriter {

  /**
   * Write the byte representation of `string`, encoded using the specified `Charset`
   * into the running buffer.
   */
  def writeString(string: CharSequence, charset: Charset): this.type

  /**
   * Write all the bytes from `bs` into the running buffer.
   */
  def writeBytes(bs: Array[Byte]): this.type

  /**
   * Write all the bytes from `buf` into the running buffer.
   */
  def writeBytes(buf: Buf): this.type

  /**
   * Write 8 bits of `b`. The remaining 24 bits are ignored.
   */
  def writeByte(b: Int): this.type

  /**
   * Write 16 bits from `s` in big-endian order. The remaining
   * 16 bits are ignored.
   */
  def writeShortBE(s: Int): this.type

  /**
   * Write 16 bits from `s` in little-endian order. The remaining
   * 16 bits are ignored.
   */
  def writeShortLE(s: Int): this.type

  /**
   * Write 24 bits from `m` in big-endian order. The remaining
   * 8 bits are ignored.
   */
  def writeMediumBE(m: Int): this.type

  /**
   * Write 24 bits from `m` in little-endian order. The remaining
   * 8 bits are ignored.
   */
  def writeMediumLE(m: Int): this.type

  /**
   * Write 32 bits from `i` in big-endian order.
   */
  def writeIntBE(i: Long): this.type

  /**
   * Write 32 bits from `i` in little-endian order.
   */
  def writeIntLE(i: Long): this.type

  /**
   * Write 64 bits from `l` in big-endian order.
   */
  def writeLongBE(l: Long): this.type

  /**
   * Write 64 bits from `l` in little-endian order.
   */
  def writeLongLE(l: Long): this.type

  /**
   * Write 32 bits from `f` in big-endian order.
   */
  def writeFloatBE(f: Float): this.type

  /**
   * Write 32 bits from `f` in little-endian order.
   */
  def writeFloatLE(f: Float): this.type

  /**
   * Write 64 bits from `d` in big-endian order.
   */
  def writeDoubleBE(d: Double): this.type

  /**
   * Write 64 bits from `d` in little-endian order.
   */
  def writeDoubleLE(d: Double): this.type
}

/**
 * An abstract implementation that implements the floating point methods
 * in terms of integer methods.
 */
abstract class AbstractByteWriter extends ByteWriter {
  /**
   * Write 32 bits from `f` in big-endian order.
   */
  def writeFloatBE(f: Float): this.type =
    writeIntBE(JFloat.floatToIntBits(f).toLong)

  /**
   * Write 32 bits from `f` in little-endian order.
   */
  def writeFloatLE(f: Float): this.type =
    writeIntLE(JFloat.floatToIntBits(f).toLong)

  /**
   * Write 64 bits from `d` in big-endian order.
   */
  def writeDoubleBE(d: Double): this.type =
    writeLongBE(JDouble.doubleToLongBits(d))

  /**
   * Write 64 bits from `d` in little-endian order.
   */
  def writeDoubleLE(d: Double): this.type =
    writeLongLE(JDouble.doubleToLongBits(d))
}
