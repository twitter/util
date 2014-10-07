package com.twitter.io

import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.Arrays

/**
 * Buf represents a fixed, immutable byte buffer. Buffers may be
 * sliced and concatenated, and thus be used to implement
 * bytestreams.
*/
trait Buf { outer =>
  /**
   * Write the entire contents of the buffer into the given array at
   * the given offset. Partial writes aren't supported directly
   * through this API; they easily performed by first slicing the
   * buffer.
   * @throws IllegalArgumentException when `output` is too small to
   * contain all the data.
   */
  @throws(classOf[IllegalArgumentException])
  def write(output: Array[Byte], off: Int): Unit

  /**
   * The number of bytes in the buffer
   */
  def length: Int

  /**
   * Returns a new buffer representing a slice of this buffer, delimited
   * by the indices `i` inclusive and `j` exclusive: `[i, j)`. Out of bounds
   * indices are truncated. Negative indices are not accepted.
   */
  def slice(i: Int, j: Int): Buf

  /**
   * Concatenate this buffer with the given buffer.
   */
  def concat(right: Buf): Buf =
    if (right.isEmpty) outer else ConcatBuf(Vector(outer)).concat(right)

  override def hashCode = Buf.hash(this)

  override def equals(other: Any): Boolean = other match {
    case other: Buf => Buf.equals(this, other)
    case _ => false
  }

  def isEmpty = length == 0
}

private[io] case class ConcatBuf(chain: Vector[Buf]) extends Buf {
  require(chain.length > 0)

  override def concat(right: Buf): Buf = right match {
    case buf if buf.isEmpty => this
    case ConcatBuf(rightChain) => ConcatBuf(chain ++ rightChain)
    case buf => ConcatBuf(chain :+ right)
  }

  def length: Int = {
    var i = 0
    var sum = 0
    while (i < chain.length) {
      sum += chain(i).length
      i += 1
    }
    sum
  }

  def write(output: Array[Byte], off: Int) = {
    require(length <= output.length - off)
    var offset = off
    chain foreach { buf =>
      buf.write(output, offset)
      offset += buf.length
    }
  }

  /**
   * @note we are foregoing clarity for performance
   *       slice only entails 3 necessary allocations
   */
  def slice(i: Int, j: Int): Buf = {
    if (i == j) return Buf.Empty
    require(0 <= i && i < j)
    val first = chain.head

    var begin = i
    var end = j
    var start, startBegin, startEnd, finish, finishBegin, finishEnd = -1
    var cur = 0
    while (cur < chain.length && finish == -1) {
      val buf = chain(cur)
      val len = buf.length
      if (begin >= 0 && begin < len) {
        start = cur
        startBegin = begin
        startEnd = end
      }
      if (end <= len) {
        finish = cur
        finishBegin = math.max(0, begin)
        finishEnd = end
      }
      begin -= len
      end -= len
      cur += 1
    }
    if (start == -1) Buf.Empty
    else if (start == finish || (start == (cur - 1) && finish == -1)) {
      chain(start).slice(startBegin, startEnd)
    } else if (finish == -1) {
      val untrimmedFirst = chain(start)
      val first: Buf = if (startBegin == 0 && startEnd >= untrimmedFirst.length) null
      else untrimmedFirst.slice(startBegin, startEnd)
      ConcatBuf(if (first == null) chain.slice(start, length)
      else first +: chain.slice(start + 1, length))
    } else {
      val untrimmedFirst = chain(start)
      val first: Buf = if (startBegin == 0 && startEnd >= untrimmedFirst.length) null
      else untrimmedFirst.slice(startBegin, startEnd)

      val untrimmedLast = chain(finish)
      val last: Buf = if (finishBegin == 0 && finishEnd >= untrimmedLast.length) null
      else untrimmedLast.slice(finishBegin, finishEnd)

      ConcatBuf(if (first == null && last == null) chain.slice(start, finish + 1)
      else if (first == null) chain.slice(start, finish) :+ last
      else if (last == null) first +: chain.slice(start + 1, finish + 1)
      else first +: chain.slice(start + 1, finish) :+ last)
    }
  }
}

object Buf {
  private class NoopBuf extends Buf {
    def write(buf: Array[Byte], off: Int) = ()
    override val isEmpty = true
    def length = 0
    def slice(i: Int, j: Int): Buf = {
      require(i >=0 && j >= 0, "Index out of bounds")
      this
    }
    override def concat(right: Buf) = right
  }

  /**
   * An empty buffer.
   */
  val Empty: Buf = new NoopBuf

  /**
   * A buffer representing an array of bytes.
   */
  class ByteArray(val bytes: Array[Byte], val begin: Int, val end: Int) extends Buf {

    def write(buf: Array[Byte], off: Int): Unit =
      System.arraycopy(bytes, begin, buf, off, length)

    def slice(i: Int, j: Int): Buf = {
      require(i >=0 && j >= 0, "Index out of bounds")

      if (j <= i || i >= length) Buf.Empty
      else if (i == 0 && j >= length) this
      else {
        val cap = math.min(j, length)
        ByteArray(bytes, begin+i, math.min(begin+cap, end))
      }
    }

    def length = end-begin

    override def toString = "ByteArray("+length+")"

    override def equals(other: Any): Boolean = {
      def bytesEq(b1: Array[Byte], off1: Int, b2: Array[Byte], off2: Int, len: Int): Boolean = {
        var i = 0
        while (i < len) {
          if (b1(off1 + i) != b2(off2 + i))
            return false
          i += 1
        }
        true
      }

      val len = length
      other match {
        case buf: ByteArray if buf.length == len =>
          bytesEq(bytes, begin, buf.bytes, buf.begin, len)
        case buf: Buf if buf.length == len =>
          val bs = new Array[Byte](len)
          buf.write(bs, 0)
          bytesEq(bytes, begin, bs, 0, len)
        case _ =>
          false
      }
    }
  }

  object ByteArray {
    /**
     * Construct a buffer representing an array of bytes
     * at the given offsets.
     */
    def apply(bytes: Array[Byte], begin: Int, end: Int): Buf =
      if (begin == end) Buf.Empty else new ByteArray(bytes, begin, end)

    /**
     * Construct a buffer representing the entire byte array.
     */
    def apply(buf: Array[Byte]): Buf =
      ByteArray(buf, 0, buf.length)

    /**
     * Construct a buffer representing the given bytes.
     */
    def apply(bytes: Byte*): Buf =
      apply(Array[Byte](bytes:_*))

    def unapply(buf: Buf): Option[(Array[Byte], Int, Int)] = buf match {
      case ba: ByteArray => Some(ba.bytes, ba.begin, ba.end)
      case _ => None
    }
  }

  /**
   * A buffer representing the remaining bytes in the
   * given ByteBuffer. The given buffer will not be
   * affected.
   *
   * Modifications to the ByteBuffer's content will be
   * visible to the resulting Buf. The ByteBuffer should
   * be immutable in practice.
   */
  class ByteBuffer(val bb: java.nio.ByteBuffer) extends Buf {
    val length = bb.remaining

    def write(output: Array[Byte], off: Int): Unit = {
      require(length <= output.length - off)
      bb.duplicate.get(output, off, length)
    }

    def slice(i: Int, j: Int): Buf = {
      require(i >=0 && j >= 0, "Index out of bounds")
      if (j <= i || i >= length) Buf.Empty
      else if (i == 0 && j >= length) this
      else {
        val dup = bb.duplicate()
        val limit = dup.position + math.min(j, length)
        if (dup.limit > limit) dup.limit(limit)
        dup.position(dup.position + i)
        ByteBuffer(dup)
      }
    }
  }

  object ByteBuffer {
    /**
     * Construct a buffer representing the given ByteBuffer.
     */
    def apply(bb: java.nio.ByteBuffer): Buf =
      if (bb.remaining == 0) Buf.Empty else new ByteBuffer(bb.duplicate())

    def unapply(buf: Buf): Option[java.nio.ByteBuffer] = buf match {
      case byteBuf: ByteBuffer => Some(byteBuf.bb.duplicate())
      case _ => None
    }
  }

  /**
   * Convert the Buf to a Java NIO ByteBuffer.
   */
  def toByteBuffer(buf: Buf): java.nio.ByteBuffer = buf match {
    case ByteArray(bytes, i, j) =>
      java.nio.ByteBuffer.wrap(bytes, i, j-i)
    case ByteBuffer(bb) =>
      bb
    case buf =>
      val bytes = new Array[Byte](buf.length)
      buf.write(bytes, 0)
      java.nio.ByteBuffer.wrap(bytes)
  }

  /** Byte equality between two buffers. Requires copies. */
  def equals(x: Buf, y: Buf): Boolean = {
    if (x.length != y.length) return false
    val a, b = new Array[Byte](x.length)
    x.write(a, 0)
    y.write(b, 0)
    Arrays.equals(a, b)
  }

  /** The 32-bit FNV-1 of Buf */
  def hash(buf: Buf): Int = buf match {
    case ByteArray(bytes, begin, end) => hashBytes(bytes, begin, end)
    case buf =>
      val bytes = new Array[Byte](buf.length)
      buf.write(bytes, 0)
      hashBytes(bytes, 0, bytes.length)
  }

  // Adapted from util-hashing.
  private[this] val UintMax: Long = 0xFFFFFFFFL
  private[this] val Fnv1a32Prime: Int = 16777619
  private def hashBytes(bytes: Array[Byte], begin: Int, end: Int): Int = {
    var i = begin
    var h = 0x811c9dc5L
    while (i < end) {
      h = (h ^ (bytes(i)&0xff)) * Fnv1a32Prime
      i += 1
    }
    (h & UintMax).toInt
  }

  /**
   * Return a string representing the buffer
   * contents in hexadecimal.
   */
  def slowHexString(buf: Buf): java.lang.String = {
    val bytes = new Array[Byte](buf.length)
    buf.write(bytes, 0)
    val digits = for (b <- bytes) yield "%02x".format(b)
    digits mkString ""
  }

  /**
   * Create and deconstruct Utf-8 encoded buffers.
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   */
  object Utf8 extends StringCoder(Charsets.Utf8)

  /**
   * Create and deconstruct 16-bit UTF buffers.
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   */
  object Utf16 extends StringCoder(Charsets.Utf16)

  /**
   * Create and deconstruct buffers encoded by the 16-bit UTF charset
   * with big-endian byte order.
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   */
  object Utf16BE extends StringCoder(Charsets.Utf16BE)

  /**
   * Create and deconstruct buffers encoded by the 16-bit UTF charset
   * with little-endian byte order.
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   */
  object Utf16LE extends StringCoder(Charsets.Utf16LE)

  /**
   * Create and deconstruct buffers encoded by the
   * ISO Latin Alphabet No. 1 charset.
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   */
  object Iso8859_1 extends StringCoder(Charsets.Iso8859_1)

  /**
   * Create and deconstruct buffers encoded by the 7-bit ASCII,
   * also known as ISO646-US or the Basic Latin block of the
   * Unicode character set.
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   */
  object UsAscii extends StringCoder(Charsets.UsAscii)

  /**
   * a StringCoder for a given [[java.nio.charset.Charset]] provides an
   * encoder: String -> Buf and an extractor: Buf -> Option[String].
   *
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   */
  private[io] abstract class StringCoder(charset: Charset) {
    /**
     * Encode the String to its Buf representation per the charset
     */
    def apply(s: String): Buf =  {
      val enc = Charsets.encoder(charset)
      val cb = CharBuffer.wrap(s.toCharArray)
      ByteBuffer(enc.encode(cb))
    }

    /**
     * @return Some(String representation of the Buf)
     * @note This extractor does *not* return None to indicate a failed
     *       or impossible decoding. Malformed or unmappable bytes will
     *       instead be silently replaced by the replacement character
     *       ("\uFFFD") in the returned String. This behavior may change
     *       in the future.
     */
    def unapply(buf: Buf): Option[String] = {
      val dec = Charsets.decoder(charset)
      val bb = Buf.toByteBuffer(buf)
      Some(dec.decode(bb).toString)
    }
  }

  /**
   * Create and deconstruct unsigned 32-bit
   * big endian encoded buffers.
   *
   * Deconstructing will return the value
   * as well as the remaining buffer.
   */
  object U32BE {
    def apply(i: Int): Buf = {
      val arr = new Array[Byte](4)
      arr(0) = ((i >> 24) & 0xff).toByte
      arr(1) = ((i >> 16) & 0xff).toByte
      arr(2) = ((i >>  8) & 0xff).toByte
      arr(3) = ((i      ) & 0xff).toByte
      ByteArray(arr)
    }

    def unapply(buf: Buf): Option[(Int, Buf)] =
      if (buf.length < 4) None else {
        val arr = new Array[Byte](4)
        buf.slice(0, 4).write(arr, 0)
        val rem = buf.slice(4, buf.length)

        val value =
          ((arr(0) & 0xff) << 24) |
          ((arr(1) & 0xff) << 16) |
          ((arr(2) & 0xff) <<  8) |
          ((arr(3) & 0xff)      )
        Some(value, rem)
      }
    }

  /**
   * Create and deconstruct unsigned 64-bit
   * big endian encoded buffers.
   *
   * Deconstructing will return the value
   * as well as the remaining buffer.
   */
  object U64BE {
    def apply(l: Long): Buf = {
      val arr = new Array[Byte](8)
      arr(0) = ((l >> 56) & 0xff).toByte
      arr(1) = ((l >> 48) & 0xff).toByte
      arr(2) = ((l >> 40) & 0xff).toByte
      arr(3) = ((l >> 32) & 0xff).toByte
      arr(4) = ((l >> 24) & 0xff).toByte
      arr(5) = ((l >> 16) & 0xff).toByte
      arr(6) = ((l >>  8) & 0xff).toByte
      arr(7) = ((l      ) & 0xff).toByte
      ByteArray(arr)
    }

    def unapply(buf: Buf): Option[(Long, Buf)] =
      if (buf.length < 8) None else {
        val arr = new Array[Byte](8)
        buf.slice(0, 8).write(arr, 0)
        val rem = buf.slice(8, buf.length)

        val value =
          ((arr(0) & 0xff).toLong << 56) |
          ((arr(1) & 0xff).toLong << 48) |
          ((arr(2) & 0xff).toLong << 40) |
          ((arr(3) & 0xff).toLong << 32) |
          ((arr(4) & 0xff).toLong << 24) |
          ((arr(5) & 0xff).toLong << 16) |
          ((arr(6) & 0xff).toLong <<  8) |
          ((arr(7) & 0xff).toLong      )
        Some(value, rem)
      }
    }

  /**
   * Create and deconstruct unsigned 32-bit
   * little endian encoded buffers.
   *
   * Deconstructing will return the value
   * as well as the remaining buffer.
   */
  object U32LE {
    def apply(i: Int): Buf = {
      val arr = new Array[Byte](4)
      arr(0) = ((i      ) & 0xff).toByte
      arr(1) = ((i >>  8) & 0xff).toByte
      arr(2) = ((i >> 16) & 0xff).toByte
      arr(3) = ((i >> 24) & 0xff).toByte
      ByteArray(arr)
    }

    def unapply(buf: Buf): Option[(Int, Buf)] =
      if (buf.length < 4) None else {
        val arr = new Array[Byte](4)
        buf.slice(0, 4).write(arr, 0)
        val rem = buf.slice(4, buf.length)

        val value =
          ((arr(0) & 0xff)      ) |
          ((arr(1) & 0xff) <<  8) |
          ((arr(2) & 0xff) << 16) |
          ((arr(3) & 0xff) << 24)
        Some(value, rem)
      }
    }

  /**
   * Create and deconstruct unsigned 64-bit
   * little endian encoded buffers.
   *
   * Deconstructing will return the value
   * as well as the remaining buffer.
   */
  object U64LE {
    def apply(l: Long): Buf = {
      val arr = new Array[Byte](8)
      arr(0) = ((l      ) & 0xff).toByte
      arr(1) = ((l >>  8) & 0xff).toByte
      arr(2) = ((l >> 16) & 0xff).toByte
      arr(3) = ((l >> 24) & 0xff).toByte
      arr(4) = ((l >> 32) & 0xff).toByte
      arr(5) = ((l >> 40) & 0xff).toByte
      arr(6) = ((l >> 48) & 0xff).toByte
      arr(7) = ((l >> 56) & 0xff).toByte
      ByteArray(arr)
    }

    def unapply(buf: Buf): Option[(Long, Buf)] =
      if (buf.length < 8) None else {
        val arr = new Array[Byte](8)
        buf.slice(0, 8).write(arr, 0)
        val rem = buf.slice(8, buf.length)

        val value =
          ((arr(0) & 0xff).toLong      ) |
          ((arr(1) & 0xff).toLong <<  8) |
          ((arr(2) & 0xff).toLong << 16) |
          ((arr(3) & 0xff).toLong << 24) |
          ((arr(4) & 0xff).toLong << 32) |
          ((arr(5) & 0xff).toLong << 40) |
          ((arr(6) & 0xff).toLong << 48) |
          ((arr(7) & 0xff).toLong << 56)
        Some(value, rem)
      }
  }
}
