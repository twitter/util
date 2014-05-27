package com.twitter.io

import java.util.Arrays
import java.nio.ByteBuffer
import java.nio.charset.Charset

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
  override def concat(right: Buf): Buf = right match {
    case buf if buf.isEmpty => this
    case ConcatBuf(rigthChain) => ConcatBuf(chain ++ rigthChain)
    case buf => ConcatBuf(chain :+ right)
  }

  def length: Int = chain.map(_.length).sum

  def write(output: Array[Byte], off: Int) = {
    require(length <= output.length - off)
    var offset = off
    chain foreach { buf =>
      buf.write(output, offset)
      offset += buf.length
    }
  }

  def slice(i: Int, j: Int): Buf = {
    require(0 <= i && i <= j)
    var begin = i
    var end = j
    var res = Buf.Empty
    chain foreach { buf =>
      val buf1 = buf.slice(begin max 0, end max 0)
      if (!buf1.isEmpty)
        res = res concat buf1
      begin -= buf.length
      end -= buf.length
    }
    res
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
   * A buffer representing "end-of-file".
   */
  val Eof: Buf = new NoopBuf

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
      else ByteArray(bytes, begin+i, (begin+j) min end)
    }

    val length = end-begin

    override def toString = "ByteArray("+length+")"

    override def equals(other: Any): Boolean = other match {
      case other: ByteArray
          if other.begin == 0 && other.end == other.bytes.length &&
          begin == 0 && end == bytes.length =>
        Arrays.equals(bytes, other.bytes)
      case other => super.equals(other)
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
   * Convert the Buf to a Java NIO ByteBuffer.
   */
  def toByteBuffer(buf: Buf): ByteBuffer = buf match {
    case ByteArray(bytes, i, j) =>
      ByteBuffer.wrap(bytes, i, j-i)
    case buf =>
      val bytes = new Array[Byte](buf.length)
      buf.write(bytes, 0)
      ByteBuffer.wrap(bytes)
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
   */
  object Utf8 {
    private val utf8 = Charset.forName("UTF-8")

    def apply(s: String): Buf = ByteArray(s.getBytes(utf8))

    def unapply(buf: Buf): Option[String] = buf match {
      case ba: ByteArray =>
        val s = new String(ba.bytes, ba.begin, ba.end-ba.begin, utf8)
        Some(s)
      case buf =>
        val bytes = new Array[Byte](buf.length)
        buf.write(bytes, 0)
        Some(new String(bytes, utf8))
    }
  }
}
