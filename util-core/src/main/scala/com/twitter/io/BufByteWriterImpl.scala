package com.twitter.io

import com.twitter.io.ByteWriter.OverflowException
import java.nio.{ByteBuffer, CharBuffer}
import java.nio.charset.{Charset, CoderResult, CodingErrorAction}

/**
 * Abstract implementation of the `BufByteWriter` that is specialized to write to an
 * underlying Array[Byte].
 */
private sealed abstract class AbstractBufByteWriterImpl
    extends AbstractByteWriter
    with BufByteWriter {

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
   * Offset in bytes of next write. Visible for testing only.
   */
  private[io] def index: Int

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
   * Write all the bytes from `buf` into the running buffer.
   */
  def writeBytes(buf: Buf): this.type = {
    val len = buf.length
    val arr = arrayToWrite(numBytes = len)
    val index = getAndIncrementIndex(numBytes = len)
    buf.write(arr, index)
    this
  }

  final def writeString(string: CharSequence, charset: Charset): this.type = {
    val charsetEncoder = charset.newEncoder()
    charsetEncoder
      .onMalformedInput(CodingErrorAction.REPLACE)
      .onUnmappableCharacter(CodingErrorAction.REPLACE)
      .reset()

    // If we are a fixed size buffer we get the rest of the array and try to fill it.
    // If we are a resizing buffer make sure we have room for the largest possible representation.
    val bufferLength = this match {
      case fixed: FixedBufByteWriter =>
        fixed.remaining

      case _: DynamicBufByteWriter =>
        math.ceil(string.length * charsetEncoder.maxBytesPerChar()).toInt
    }

    val arr = arrayToWrite(bufferLength)
    val bb = ByteBuffer.wrap(arr, index, bufferLength)
    val startPosition = bb.position()

    // Write the string
    checkCoderResult(string, charsetEncoder.encode(CharBuffer.wrap(string), bb, true))
    // Flush the coder
    checkCoderResult(string, charsetEncoder.flush(bb))

    // increment our writer index
    getAndIncrementIndex(bb.position() - startPosition)
    this
  }

  private[this] def checkCoderResult(string: CharSequence, result: CoderResult): Unit = {
    if (result.isOverflow)
      throw new OverflowException(s"insufficient space to write ${string.length} length string")
    else if (!result.isUnderflow())
      result.throwException()
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
    arr(index) = ((s >> 8) & 0xff).toByte
    arr(index + 1) = ((s) & 0xff).toByte
    this
  }

  /**
   * Write 16 bits from `s` in little-endian order. The remaining
   * 16 bits are ignored.
   */
  def writeShortLE(s: Int): this.type = {
    val arr = arrayToWrite(numBytes = 2)
    val index = getAndIncrementIndex(numBytes = 2)
    arr(index) = ((s) & 0xff).toByte
    arr(index + 1) = ((s >> 8) & 0xff).toByte
    this
  }

  /**
   * Write 24 bits from `m` in big-endian order. The remaining
   * 8 bits are ignored.
   */
  def writeMediumBE(m: Int): this.type = {
    val arr = arrayToWrite(numBytes = 3)
    val index = getAndIncrementIndex(numBytes = 3)
    arr(index) = ((m >> 16) & 0xff).toByte
    arr(index + 1) = ((m >> 8) & 0xff).toByte
    arr(index + 2) = ((m) & 0xff).toByte
    this
  }

  /**
   * Write 24 bits from `m` in little-endian order. The remaining
   * 8 bits are ignored.
   */
  def writeMediumLE(m: Int): this.type = {
    val arr = arrayToWrite(numBytes = 3)
    val index = getAndIncrementIndex(numBytes = 3)
    arr(index) = ((m) & 0xff).toByte
    arr(index + 1) = ((m >> 8) & 0xff).toByte
    arr(index + 2) = ((m >> 16) & 0xff).toByte
    this
  }

  /**
   * Write 32 bits from `i` in big-endian order.
   */
  def writeIntBE(i: Long): this.type = {
    val arr = arrayToWrite(numBytes = 4)
    val index = getAndIncrementIndex(numBytes = 4)
    arr(index) = ((i >> 24) & 0xff).toByte
    arr(index + 1) = ((i >> 16) & 0xff).toByte
    arr(index + 2) = ((i >> 8) & 0xff).toByte
    arr(index + 3) = ((i) & 0xff).toByte
    this
  }

  /**
   * Write 32 bits from `i` in little-endian order.
   */
  def writeIntLE(i: Long): this.type = {
    val arr = arrayToWrite(numBytes = 4)
    val index = getAndIncrementIndex(numBytes = 4)
    arr(index) = ((i) & 0xff).toByte
    arr(index + 1) = ((i >> 8) & 0xff).toByte
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
    arr(index) = ((l >> 56) & 0xff).toByte
    arr(index + 1) = ((l >> 48) & 0xff).toByte
    arr(index + 2) = ((l >> 40) & 0xff).toByte
    arr(index + 3) = ((l >> 32) & 0xff).toByte
    arr(index + 4) = ((l >> 24) & 0xff).toByte
    arr(index + 5) = ((l >> 16) & 0xff).toByte
    arr(index + 6) = ((l >> 8) & 0xff).toByte
    arr(index + 7) = ((l) & 0xff).toByte
    this
  }

  /**
   * Write 64 bits from `l` in little-endian order.
   */
  def writeLongLE(l: Long): this.type = {
    val arr = arrayToWrite(numBytes = 8)
    val index = getAndIncrementIndex(numBytes = 8)
    arr(index) = ((l) & 0xff).toByte
    arr(index + 1) = ((l >> 8) & 0xff).toByte
    arr(index + 2) = ((l >> 16) & 0xff).toByte
    arr(index + 3) = ((l >> 24) & 0xff).toByte
    arr(index + 4) = ((l >> 32) & 0xff).toByte
    arr(index + 5) = ((l >> 40) & 0xff).toByte
    arr(index + 6) = ((l >> 48) & 0xff).toByte
    arr(index + 7) = ((l >> 56) & 0xff).toByte
    this
  }
}

/**
 * A fixed size [[BufByteWriter]].
 */
private final class FixedBufByteWriter(arr: Array[Byte], private[io] var index: Int = 0)
    extends AbstractBufByteWriterImpl {
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
private object DynamicBufByteWriter {
  val MaxBufferSize: Int = Int.MaxValue - 2
}

private final class DynamicBufByteWriter(arr: Array[Byte]) extends AbstractBufByteWriterImpl {
  import ByteWriter.OverflowException
  import DynamicBufByteWriter._

  private var underlying: FixedBufByteWriter = new FixedBufByteWriter(arr)

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
        throw new OverflowException(
          s"maximum dynamic buffer size is $MaxBufferSize." +
            s" Insufficient space to write $requiredRemainingBytes bytes"
        )

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
      underlying = new FixedBufByteWriter(newArr, written)
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
