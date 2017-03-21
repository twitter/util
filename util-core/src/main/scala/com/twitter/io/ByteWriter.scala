package com.twitter.io

import java.lang.{Double => JDouble, Float => JFloat}

/**
 * [[ByteWriter]]s allow efficient construction of a buffer. Concatenating
 * [[Buf]]s together is a common pattern for codecs (especially on the encode
 * path), but using the `concat` method on [[Buf]] results in a suboptimal
 * representation.  In many cases, a [[ByteWriter]] not only allows for a more
 * optimal representation (i.e., sequential accessible out regions), but also
 * allows for writes to avoid allocations.  What this means in practice is that
 * builders are stateful and keep track of a writer index. Assume that the
 * builder implementations are *not* threadsafe unless otherwise noted.
 */
abstract class ByteWriter {

  /**
   * Returns the array to write `numBytes` bytes into. Subclasses should
   * assume that [[ByteWriter]] writes after calling `arrayToWrite`
   * and `startWriteIndex`, and update their state accordingly.
   * We obtain the array and index to write into for performance; successive
   * writing of bytes in each of the write methods don't require additional
   * function calls and size checks.
   */
  private[io] def arrayToWrite(numBytes: Int): Array[Byte]

  /**
   * Returns the index at which to start writing. Subclasses
   * are expected to update the index according to numBytes.
   */
  private[io] def getAndIncrementIndex(numBytes: Int): Int

  /**
   * Write all the bytes from `bs` into the running buffer.
   */
  def writeBytes(bs: Array[Byte]): this.type = {
    val arr = arrayToWrite(numBytes = bs.length)
    val index = getAndIncrementIndex(numBytes = bs.length)
    System.arraycopy(bs, 0, arr, index, bs.length)
    this
  }

  /**
   * Write 8 bits of `b`. The remaining 24 bits are ignored.
   */
  def writeByte(b: Int): this.type = {
    val arr = arrayToWrite(numBytes = 1)
    val index = getAndIncrementIndex(numBytes = 1)
    arr(index) = ((b & 0xff).toByte)
    this
  }

  /**
   * Write 16 bits from `s` in big-endian order. The remaining
   * 16 bits are ignored.
   */
  def writeShortBE(s: Int): this.type = {
    val arr = arrayToWrite(numBytes = 2)
    val index = getAndIncrementIndex(numBytes = 2)
    arr(index)     = ((s >>  8) & 0xff).toByte
    arr(index + 1) = ((s      ) & 0xff).toByte
    this
  }

  /**
   * Write 16 bits from `s` in little-endian order. The remaining
   * 16 bits are ignored.
   */
  def writeShortLE(s: Int): this.type = {
    val arr = arrayToWrite(numBytes = 2)
    val index = getAndIncrementIndex(numBytes = 2)
    arr(index)     = ((s      ) & 0xff).toByte
    arr(index + 1) = ((s >>  8) & 0xff).toByte
    this
  }

  /**
   * Write 24 bits from `m` in big-endian order. The remaining
   * 8 bits are ignored.
   */
  def writeMediumBE(m: Int): this.type = {
    val arr = arrayToWrite(numBytes = 3)
    val index = getAndIncrementIndex(numBytes = 3)
    arr(index)     = ((m >> 16) & 0xff).toByte
    arr(index + 1) = ((m >>  8) & 0xff).toByte
    arr(index + 2) = ((m      ) & 0xff).toByte
    this
  }

  /**
   * Write 24 bits from `m` in little-endian order. The remaining
   * 8 bits are ignored.
   */
  def writeMediumLE(m: Int): this.type = {
    val arr = arrayToWrite(numBytes = 3)
    val index = getAndIncrementIndex(numBytes = 3)
    arr(index)     =  ((m      ) & 0xff).toByte
    arr(index + 1) =  ((m >>  8) & 0xff).toByte
    arr(index + 2) =  ((m >> 16) & 0xff).toByte
    this
  }

  /**
   * Write 32 bits from `i` in big-endian order.
   */
  def writeIntBE(i: Long): this.type = {
    val arr = arrayToWrite(numBytes = 4)
    val index = getAndIncrementIndex(numBytes = 4)
    arr(index)     = ((i >> 24) & 0xff).toByte
    arr(index + 1) = ((i >> 16) & 0xff).toByte
    arr(index + 2) = ((i >>  8) & 0xff).toByte
    arr(index + 3) = ((i      ) & 0xff).toByte
    this
  }

  /**
   * Write 32 bits from `i` in little-endian order.
   */
  def writeIntLE(i: Long): this.type = {
    val arr = arrayToWrite(numBytes = 4)
    val index = getAndIncrementIndex(numBytes = 4)
    arr(index)     = ((i      ) & 0xff).toByte
    arr(index + 1) = ((i >>  8) & 0xff).toByte
    arr(index + 2) = ((i >> 16) & 0xff).toByte
    arr(index + 3) = ((i >> 24) & 0xff).toByte
    this
  }

  /**
   * Write 64 bits from `l` in big-endian order.
   */
  def writeLongBE(l: Long): this.type = {
    val arr = arrayToWrite(numBytes = 8)
    val index = getAndIncrementIndex(numBytes = 8)
    arr(index)     = ((l >> 56) & 0xff).toByte
    arr(index + 1) = ((l >> 48) & 0xff).toByte
    arr(index + 2) = ((l >> 40) & 0xff).toByte
    arr(index + 3) = ((l >> 32) & 0xff).toByte
    arr(index + 4) = ((l >> 24) & 0xff).toByte
    arr(index + 5) = ((l >> 16) & 0xff).toByte
    arr(index + 6) = ((l >>  8) & 0xff).toByte
    arr(index + 7) = ((l      ) & 0xff).toByte
    this
  }

  /**
   * Write 64 bits from `l` in little-endian order.
   */
  def writeLongLE(l: Long): this.type = {
    val arr = arrayToWrite(numBytes = 8)
    val index = getAndIncrementIndex(numBytes = 8)
    arr(index)     = ((l      ) & 0xff).toByte
    arr(index + 1) = ((l >>  8) & 0xff).toByte
    arr(index + 2) = ((l >> 16) & 0xff).toByte
    arr(index + 3) = ((l >> 24) & 0xff).toByte
    arr(index + 4) = ((l >> 32) & 0xff).toByte
    arr(index + 5) = ((l >> 40) & 0xff).toByte
    arr(index + 6) = ((l >> 48) & 0xff).toByte
    arr(index + 7) = ((l >> 56) & 0xff).toByte
    this
  }

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

  /**
   * In keeping with [[Buf]] terminology, creates a potential zero-copy [[Buf]]
   * which has a reference to the same region as the [[ByteWriter]]. That is, if
   * any methods called on the builder are propagated to the returned [[Buf]].
   * By definition, the builder owns the region it is writing to so this is
   * the natural way to coerce a builder to a [[Buf]].
   */
  def owned(): Buf

  /**
   * Offset in bytes of next write. Visible for testing only.
   */
  private[io] def index: Int
}

private[twitter] trait ProxyByteWriter extends ByteWriter {
  protected def writer: ByteWriter

  private[io] def arrayToWrite(numBytes: Int): Array[Byte] = writer.arrayToWrite(numBytes)

  private[io] def getAndIncrementIndex(numBytes: Int): Int = writer.getAndIncrementIndex(numBytes)

  private[io] def index: Int = writer.index

  def owned(): Buf = writer.owned()
}

object ByteWriter {

  /**
   * Creates a fixed sized [[ByteWriter]] that writes to `bytes`
   */
  def apply(bytes: Array[Byte]): ByteWriter = {
    new FixedByteWriter(bytes)
  }

  /**
   * Creates a fixed size [[ByteWriter]].
   */
  def fixed(size: Int): ByteWriter = {
    require(size >= 0)
    new FixedByteWriter(new Array[Byte](size))
  }

  /**
   * Creates a [[ByteWriter]] that grows as needed.
   * The maximum size is Int.MaxValue - 2 (maximum array size).
   *
   * @param estimatedSize the starting size of the backing buffer, in bytes.
   *                      If no size is given, 256 bytes will be used.
   */
  def dynamic(estimatedSize: Int = 256): ByteWriter = {
    require(estimatedSize > 0 && estimatedSize <= DynamicByteWriter.MaxBufferSize)
    new DynamicByteWriter(new Array[Byte](estimatedSize))
  }

  /**
   * Indicates there isn't enough room to write into
   * a [[ByteWriter]].
   */
  class OverflowException(msg: String) extends Exception(msg)
}

/**
 * A fixed size [[ByteWriter]].
 */
private class FixedByteWriter(arr: Array[Byte], private[io] var index: Int = 0) extends ByteWriter {
  import ByteWriter.OverflowException

  require(index >= 0)
  require(index <= arr.length)

  /**
   * Exposed to allow `DynamicByteWriter` access to the underlying array of its
   * `FixedByteWriter.` DO NOT USE OUTSIDE OF BUFWRITER!
   */
  private[io] def array: Array[Byte] = arr
  def size: Int = arr.length
  def remaining: Int = arr.length - index

  def arrayToWrite(bytes: Int): Array[Byte] = {
    if (remaining < bytes) {
      throw new OverflowException(s"insufficient space to write $bytes bytes")
    }
    array
  }

  def getAndIncrementIndex(numBytes: Int): Int = {
    val curIndex = index
    index += numBytes
    curIndex
  }

  val owned: Buf = Buf.ByteArray.Owned(arr)
}

/**
 * A dynamically growing buffer.
 *
 * @note The size of the buffer increases by 50% as necessary to contain the content,
 *       up to a maximum size of Int.MaxValue - 2 (maximum array size; platform-specific).
 */
private object DynamicByteWriter {
  val MaxBufferSize: Int = Int.MaxValue - 2
}

private class DynamicByteWriter(arr: Array[Byte]) extends ByteWriter {
  import ByteWriter.OverflowException
  import DynamicByteWriter._

  var underlying: FixedByteWriter = new FixedByteWriter(arr)

  /**
   * Offset in bytes of next write. Visible for testing only.
   */
  private[io] def index: Int = underlying.index

  // Create a new underlying buffer if `requiredRemainingBytes` will not
  // fit in the current underlying buffer. The size of the buffer is increased
  // by 50% until current and new bytes will fit. If increasing the size causes
  // the buffer size to overflow, but the contents will still fit in `MaxBufferSize`,
  // the size is scaled back to `requiredSize`.
  private[this] def resizeIfNeeded(requiredRemainingBytes: Int): Unit = {
    if (requiredRemainingBytes > underlying.remaining) {

      var size: Int = underlying.size

      val written = underlying.size - underlying.remaining

      val requiredSize: Int = written + requiredRemainingBytes

      // Check to make sure we won't exceed max buffer size
      if (requiredSize < 0 || requiredSize > MaxBufferSize)
        throw new OverflowException(s"maximum dynamic buffer size is $MaxBufferSize." +
          s" Insufficient space to write $requiredRemainingBytes bytes")

      // Increase size of the buffer by 50% until it can contain `requiredBytes`
      while (requiredSize > size && size > 0) {
        size = (size * 3) / 2 + 1
      }

      // If we've overflowed by increasing the size, scale back to `requiredSize`.
      // We know that we can still fit the current + new bytes, because of
      // the requiredSize check above.
      if (size < 0 || requiredSize > MaxBufferSize)
        size = requiredSize

      // Create a new underlying buffer
      val newArr = new Array[Byte](size)
      System.arraycopy(underlying.array, 0, newArr, 0, written)
      underlying = new FixedByteWriter(newArr, written)
    }
  }

  private[io] def arrayToWrite(bytes: Int): Array[Byte] = {
    resizeIfNeeded(bytes)
    underlying.arrayToWrite(bytes)
  }

  private[io] def getAndIncrementIndex(numBytes: Int): Int =
    underlying.getAndIncrementIndex(numBytes)

  /**
   * Copies the contents of this writer into a `Buf` of the exact size needed.
   */
  def owned(): Buf = {
    // trim the buffer to the size of the contents
    Buf.ByteArray.Owned(underlying.array, 0, underlying.size - underlying.remaining)
  }
}
