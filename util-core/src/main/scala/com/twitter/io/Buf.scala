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
   */
  def write(buf: Array[Byte], off: Int)

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
    if (right == Buf.Empty) outer else new Buf {
     private[this] val left = outer
    def slice(i: Int, j: Int): Buf = {
      left.slice(i min left.length, j min left.length) concat
        right.slice((i-left.length) max 0, (j-left.length) max 0)
    }

    def write(buf: Array[Byte], off: Int) {
      require(length <= buf.length-off)
      left.write(buf, off)
      right.write(buf, off+outer.length)
    }

    def length = left.length + right.length
  }

  override def equals(other: Any): Boolean = other match {
    case other: Buf => Buf.equals(this, other)
    case _ => false
  }
}

object Buf {
  private class NoopBuf extends Buf {
    def write(buf: Array[Byte], off: Int) = ()
    def length = 0
    def slice(i: Int, j: Int): Buf = {
      require(i >=0 && j >= 0, "Index out of bounds")
      this
    }
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

      if (j <= i || i > length) Buf.Empty
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
        Some(new String(bytes, utf8))
    }
  }
}
