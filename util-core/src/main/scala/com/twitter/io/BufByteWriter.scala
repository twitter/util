package com.twitter.io

/**
 * A `ByteWriter` that results in an owned `Buf`
 */
trait BufByteWriter extends ByteWriter {

  /**
   * In keeping with [[Buf]] terminology, creates a potential zero-copy [[Buf]]
   * which has a reference to the same region as the [[ByteWriter]]. That is, if
   * any methods called on the builder are propagated to the returned [[Buf]].
   * By definition, the builder owns the region it is writing to so this is
   * the natural way to coerce a builder to a [[Buf]].
   */
  def owned(): Buf
}

object BufByteWriter {

  /**
   * Creates a fixed sized [[BufByteWriter]] that writes to `bytes`
   */
  def apply(bytes: Array[Byte]): BufByteWriter = {
    new FixedBufByteWriter(bytes)
  }

  /**
   * Creates a fixed size [[BufByteWriter]].
   */
  def fixed(size: Int): BufByteWriter = {
    require(size >= 0)
    apply(new Array[Byte](size))
  }

  /**
   * Creates a [[BufByteWriter]] that grows as needed.
   * The maximum size is Int.MaxValue - 2 (maximum array size).
   *
   * @param estimatedSize the starting size of the backing buffer, in bytes.
   *                      If no size is given, 256 bytes will be used.
   */
  def dynamic(estimatedSize: Int = 256): BufByteWriter = {
    require(estimatedSize > 0 && estimatedSize <= DynamicBufByteWriter.MaxBufferSize)
    new DynamicBufByteWriter(new Array[Byte](estimatedSize))
  }
}
