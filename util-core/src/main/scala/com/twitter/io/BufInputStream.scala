package com.twitter.io

import java.io.InputStream

class BufInputStream(val buf: Buf) extends InputStream {
  /**
   * The slice of `buf` that hasn't been consumed.
   */
  private[this] var rest: Buf = buf

  /**
   * A saved version of `rest`
   */
  private[this] var mrk: Buf = buf

  // Returns an estimate of the number of bytes that can be read (or
  // skipped over) from this input stream without blocking by the next
  // invocation of a method for this input stream.
  override def available(): Int = synchronized { rest.length }

  // Closing a BufInputStream has no effect.
  override def close() {}

  // Marks the current position in this input stream.
  override def mark(readlimit: Int) = synchronized { mrk = rest }

  // Tests if this input stream supports the mark and reset methods.
  override def markSupported(): Boolean = true

  // Reads the next byte of data from the input stream.
  def read(): Int = synchronized {
    if (rest.length <= 0)
      return -1

    val b = new Array[Byte](1)
    rest.slice(0, 1).write(b, 0)
    rest = rest.slice(1, rest.length)
    b(0) & 0xFF
  }

  /**
   *  Reads up to len bytes of data from the input stream into an
   *  array of bytes.
   */
  override def read(b: Array[Byte], off: Int, len: Int): Int = synchronized {
    if (rest.length <= 0)
      return -1

    if (len == 0)
      return 0

    val n = len min rest.length
    rest.slice(0, n).write(b, off)
    rest = rest.slice(n, rest.length)
    n
  }

  /**
   * Repositions this stream to the position at the time the mark
   * method was last called on this input stream.
   */
  override def reset() = synchronized { rest = mrk }

  /**
   * Skips over and discards n bytes of data from this input stream.
   */
  override def skip(n: Long): Long = synchronized {
    if (n <= 0)
      return 0

    val skipped = n min rest.length
    rest = rest.slice(skipped.toInt, rest.length)
    skipped
  }
}
