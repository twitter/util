package com.twitter.io

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
  class ByteArray(bytes: Array[Byte], begin: Int, end: Int) extends Buf {

    def write(buf: Array[Byte], off: Int): Unit =
      System.arraycopy(bytes, begin, buf, off, length)

    def slice(i: Int, j: Int): Buf = {
      require(i >=0 && j >= 0, "Index out of bounds")

      if (j <= i || i > length) Buf.Empty
      else ByteArray(bytes, begin+i, (begin+j) min end)
    }

    val length = end-begin
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
  }
}
