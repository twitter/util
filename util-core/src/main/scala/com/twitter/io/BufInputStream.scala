package com.twitter.io

import java.io.InputStream

class BufInputStream(val buf: Buf) extends InputStream {

  private[this] val length = buf.length

  /**
   * The index of the next byte to be read.
   */
  private[this] var index: Int = 0

  /**
   * A saved version of `index`
   */
  private[this] var mrk: Int = 0

  // Returns an estimate of the number of bytes that can be read (or
  // skipped over) from this input stream without blocking by the next
  // invocation of a method for this input stream.
  override def available(): Int = synchronized { length - index }

  // Closing a BufInputStream has no effect.
  override def close() {}

  // Marks the current position in this input stream.
  override def mark(readlimit: Int) = synchronized { mrk = index }

  // Tests if this input stream supports the mark and reset methods.
  override def markSupported(): Boolean = true

  // Reads the next byte of data from the input stream.
  def read(): Int = synchronized {
    if (index >= length) -1
    else {
      val b = buf.get(index)
      index += 1
      b & 0xFF
    }
  }

  /**
   *  Reads up to len bytes of data from the input stream into an
   *  array of bytes.
   */
  override def read(b: Array[Byte], off: Int, len: Int): Int = synchronized {
    if (off < 0)
      throw new IllegalArgumentException("Offset must not be negative")
    else if (len < 0)
      throw new IllegalArgumentException("Length must not be negative")
    else if (len > b.length - off)
      throw new IllegalArgumentException(
        "Length must not exceed the destination array size - offset"
      )

    if (index >= length) -1
    else if (len == 0) 0
    else {
      val n = math.min(len, available)
      buf.slice(index, index + n).write(b, off)
      index += n
      n
    }
  }

  /**
   * Repositions this stream to the position at the time the mark
   * method was last called on this input stream.
   */
  override def reset() = synchronized { index = mrk }

  /**
   * Skips over and discards n bytes of data from this input stream.
   */
  override def skip(n: Long): Long = synchronized {
    if (n <= 0L) 0L
    else {
      val skipped = math.min(n, available)
      index += skipped.toInt
      skipped
    }
  }
}
