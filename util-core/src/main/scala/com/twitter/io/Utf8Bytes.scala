package com.twitter.io

import java.nio.charset.StandardCharsets

object Utf8Bytes {

  /**
   * Create a [[Utf8Bytes]] instance from a byte array.  The array passed in is copied to ensure it can not be later
   * mutated.
   *
   * @note No validation is done on the passed bytes to ensure that they are valid UTF-8.  If invalid bytes are present
   *       they will be converted to the default UTF-8 replacement string.
   * @note As an optimization, if it's known that the byte[] will never be mutated once passed to the [[Utf8Bytes]]
   *       instance, use `Utf8Bytes(Buf.ByteArray.Owned(bytes))` to create an instance without copying the byte[].
   */
  def apply(bytes: Array[Byte]): Utf8Bytes = apply(Buf.ByteArray.Shared(bytes))

  /**
   * Create a [[Utf8Bytes]] instance from a [[String]].  The string is first encoded into UTF-8 bytes.
   */
  def apply(str: String): Utf8Bytes = apply(Buf.Utf8(str))

  /**
   * Create a [[Utf8Bytes]] instance from a [[Buf]].
   *
   * @note No validation is done on the passed bytes to ensure that they are valid UTF-8.  If invalid bytes are present
   *       they will be converted to the default UTF-8 replacement string.
   * @note This is the most efficient way to create an instance.  It neither has to encode a string to bytes nor do a
   *       defensive copy, since [[Buf]] instances are already immutable.
   */
  def apply(buf: Buf): Utf8Bytes = new Utf8Bytes(buf)

  val empty: Utf8Bytes = apply(Buf.Empty)
}

/**
 * An immutable representation of a UTF-8 encoded string.  The representation is lazily converted to a [[String]] when
 * operations require it.
 *
 * @note This class is thread-safe.
 */
final class Utf8Bytes private (
  private[this] val buf: Buf)
    extends CharSequence {

  private[this] var str: String = _

  /**
   * The underlying byte content of this instance.
   */
  def asBuf: Buf = buf

  override def toString: String = {
    // multiple threads may race to set `str`, however doing so is safe
    if (str == null) {
      str = Buf.decodeString(buf, StandardCharsets.UTF_8)
    }
    str
  }

  override def equals(obj: Any): Boolean =
    obj match {
      case other: AnyRef if this.eq(other) => true
      case other: Utf8Bytes => buf == other.asBuf
      case _ => false
    }

  override def hashCode(): Int = buf.hashCode

  // note, all methods below must operate on chars, not bytes, so they must operate on the string representation
  // rather than the byte representation
  override def length(): Int = toString.length

  override def charAt(index: Int): Char = toString.charAt(index)

  override def subSequence(start: Int, end: Int): CharSequence = toString.subSequence(start, end)
}
