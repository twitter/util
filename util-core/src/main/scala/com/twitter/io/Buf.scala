package com.twitter.io

import java.nio.charset.{Charset, StandardCharsets => JChar}

/**
 * Buf represents a fixed, immutable byte buffer. Buffers may be
 * sliced and concatenated, and thus be used to implement
 * bytestreams.
 */
abstract class Buf { outer =>

  // Cached hash code for the Buf.
  // Note: there is an opportunity for a race in computing cachedHashCode
  // but since the computed hash code is deterministic the worst case
  // scenario is duplication of work.
  private[this] var cachedHashCode = 0

  /**
   * Write the entire contents of the buffer into the given array at
   * the given offset. Partial writes aren't supported directly
   * through this API; they can be accomplished by first [[slice slicing]] the
   * buffer.
   * @throws IllegalArgumentException when `output` is too small to
   * contain all the data.
   *
   * @note [[Buf]] implementors should use the helper [[checkWriteArgs]].
   */
  @throws(classOf[IllegalArgumentException])
  def write(output: Array[Byte], off: Int): Unit

  /**
   * The number of bytes in the buffer
   */
  def length: Int

  /**
   * Returns a new buffer representing a slice of this buffer, delimited
   * by the indices `from` inclusive and `until` exclusive: `[from, until)`.
   * Out of bounds indices are truncated. Negative indices are not accepted.
   *
   * @note [[Buf]] implementors should use the helpers [[checkSliceArgs]],
   *       [[isSliceEmpty]], and [[isSliceIdentity]].
   */
  def slice(from: Int, until: Int): Buf

  /**
   * Concatenate this buffer with the given buffer.
   */
  def concat(right: Buf): Buf =
    if (right.isEmpty) outer
    else Buf(Vector(outer, right))

  override def equals(other: Any): Boolean = other match {
    case other: Buf => Buf.equals(this, other)
    case _ => false
  }

  final override def hashCode: Int = {
    // A magic number of 0 signifies that the hash code has not been computed.
    // Note: 0 may be the legitimate hash code of a Buf, in which case the
    // hash code will be recomputed every invocation of Buf.hashCode. However,
    // this is very unlikely.
    if (cachedHashCode == 0) {
      cachedHashCode = computeHashCode
    }
    cachedHashCode
  }

  def isEmpty: Boolean = length == 0

  /** Helper to support 0-copy coercion to Buf.ByteArray. */
  protected def unsafeByteArrayBuf: Option[Buf.ByteArray]

  /** May require copying. */
  protected def unsafeByteArray: Array[Byte] = unsafeByteArrayBuf match {
    case Some(Buf.ByteArray.Owned(bytes, 0, end)) if end == bytes.length =>
      bytes
    case _ =>
      copiedByteArray
  }

  /** Compute the 32-bit FNV-1 hash code of this buf. */
  protected def computeHashCode: Int = Buf.hash(this)

  /** Definitely requires copying. */
  protected def copiedByteArray: Array[Byte] = {
    val bytes = new Array[Byte](length)
    write(bytes, 0)
    bytes
  }

  /** Helps implementations validate the arguments to [[slice]]. */
  protected[this] def checkSliceArgs(from: Int, until: Int): Unit = {
    if (from < 0)
      throw new IllegalArgumentException(s"'from' must be non-negative: $from")
    if (until < 0)
      throw new IllegalArgumentException(s"'until' must be non-negative: $until")
  }

  /** Helps implementations of [[slice]]. */
  protected[this] def isSliceEmpty(from: Int, until: Int): Boolean =
    until <= from || from >= length

  /** Helps implementations of [[slice]]. */
  protected[this] def isSliceIdentity(from: Int, until: Int): Boolean =
    from == 0 && until >= length

  /** Helps implementations validate the arguments to [[write]]. */
  protected[this] def checkWriteArgs(
    outputLen: Int,
    outputOff: Int
  ): Unit = {
    if (outputOff < 0)
      throw new IllegalArgumentException(s"offset must be non-negative: $outputOff")
    val len = length
    if (len > outputLen - outputOff)
      throw new IllegalArgumentException(
        s"Output too small, capacity=${outputLen-outputOff}, need=$len")
  }

}

/**
 * A Buf that concats multiple Bufs together
 */
@deprecated("Use `Buf.apply` instead", "2016-12-21")
case class ConcatBuf(chain: Vector[Buf])
  extends Buf.Composite {
  require(chain.nonEmpty)

  def bufs: IndexedSeq[Buf] = chain
  protected def computedLength: Int = throw new UnsupportedOperationException

  override def isEmpty: Boolean = {
    var i = 0
    while (i < chain.length) {
      if (!chain(i).isEmpty) return false
      i += 1
    }
    true
  }

  override def length: Int = {
    var i = 0
    var sum = 0
    while (i < chain.length) {
      sum += chain(i).length
      i += 1
    }
    sum
  }
}

/**
 * Buf wrapper-types (like Buf.ByteArray and Buf.ByteBuffer) provide Shared and
 * Owned APIs, each of which with construction & extraction utilities.
 *
 * The Owned APIs may provide direct access to a Buf's underlying
 * implementation; and so mutating the data structure invalidates a Buf's
 * immutability constraint. Users must take care to handle this data
 * immutably.
 *
 * The Shared variants, on the other hand, ensure that the Buf shares no state
 * with the caller (at the cost of additional allocation).
 *
 * Note: There is a Java-friendly API for this object: [[com.twitter.io.Buf]].
 */
object Buf {

  private class NoopBuf extends Buf {
    override def toString: String = "Buf.Empty"
    def write(buf: Array[Byte], off: Int): Unit =
      checkWriteArgs(buf.length, off)
    override val isEmpty = true
    def length: Int = 0
    def slice(from: Int, until: Int): Buf = {
      checkSliceArgs(from, until)
      this
    }
    override def concat(right: Buf): Buf = right
    protected def unsafeByteArrayBuf: Option[Buf.ByteArray] = None
  }

  /**
   * An empty buffer.
   */
  val Empty: Buf = new NoopBuf

  /**
   * Create a `Buf` out of the given `Bufs`.
   */
  def apply(bufs: Iterable[Buf]): Buf = {
    val builder = Vector.newBuilder[Buf]
    var length = 0
    bufs.foreach { b =>
      val len = b.length
      length += len
      if (len > 0) builder += b
    }

    val filtered = builder.result()
    if (length == 0)
      Buf.Empty
    else if (filtered.size == 1)
      filtered.head
    else
      new Composite.Impl(filtered, length)
  }

  /**
   * A `Buf` which is composed of other `Bufs`.
   *
   * @see [[Buf.apply]] for creating new instances.
   */
  sealed abstract class Composite extends Buf {
    def bufs: IndexedSeq[Buf]
    protected def computedLength: Int

    def length: Int = computedLength

    override def toString: String = s"Buf.Composite(length=$length)"

    def write(output: Array[Byte], off: Int): Unit = {
      if (length > output.length - off)
        throw new IllegalArgumentException(
          s"Output too small, capacity=${output.length-off}, need=$length")
      var offset = off
      var i = 0
      while (i < bufs.length) {
        val buf = bufs(i)
        buf.write(output, offset)
        offset += buf.length
        i += 1
      }
    }

    def slice(from: Int, until: Int): Buf = {
      checkSliceArgs(from, until)

      if (isSliceEmpty(from, until)) return Buf.Empty
      else if (isSliceIdentity(from, until)) return this

      var begin = from
      var end = until
      var start, startBegin, startEnd, finish, finishBegin, finishEnd = -1
      var cur = 0
      while (cur < bufs.length && finish == -1) {
        val buf = bufs(cur)
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
        bufs(start).slice(startBegin, startEnd)
      } else if (finish == -1) {
        val untrimmedFirst = bufs(start)
        val first: Buf =
          if (startBegin == 0 && startEnd >= untrimmedFirst.length) null
          else untrimmedFirst.slice(startBegin, startEnd)
        Buf(
          if (first == null) bufs.slice(start, length)
          else first +: bufs.slice(start + 1, length))
      } else {
        val untrimmedFirst = bufs(start)
        val first: Buf =
          if (startBegin == 0 && startEnd >= untrimmedFirst.length) null
          else untrimmedFirst.slice(startBegin, startEnd)

        val untrimmedLast = bufs(finish)
        val last: Buf =
          if (finishBegin == 0 && finishEnd >= untrimmedLast.length) null
          else untrimmedLast.slice(finishBegin, finishEnd)

        Buf(
          if (first == null && last == null) bufs.slice(start, finish + 1)
          else if (first == null) bufs.slice(start, finish) :+ last
          else if (last == null) first +: bufs.slice(start + 1, finish + 1)
          else first +: bufs.slice(start + 1, finish) :+ last)
      }
    }

    override def concat(right: Buf): Buf = right match {
      case _ if right.isEmpty => this
      // this special ConcatBuf case is needed as `this` may be a ConcatBuf
      // which could be empty. otherwise we can skip the copying of Buf.apply.
      case ConcatBuf(rightBufs) => Buf(bufs ++ rightBufs)
      case c: Composite => new Composite.Impl(bufs ++ c.bufs, length + c.length)
      case _ => new Composite.Impl(bufs :+ right, length + right.length)
    }

    protected def unsafeByteArrayBuf: Option[ByteArray] = None

    override def equals(other: Any): Boolean = other match {
      case otherBuf: Buf if length == otherBuf.length =>
        var i = 0
        var offset = 0
        while (i < bufs.length) {
          val buf = bufs(i)
          val otherSlice = otherBuf.slice(offset, offset + buf.length)
          if (!buf.equals(otherSlice)) return false
          offset += buf.length
          i += 1
        }
        true

      case _ =>
        false
    }
  }

  object Composite {
    def unapply(buf: Composite): Option[IndexedSeq[Buf]] =
      Some(buf.bufs)

    /** Basic implementation of a [[Buf]] created from n-`Bufs`. */
    private[Buf] class Impl(
        bs: IndexedSeq[Buf],
        protected val computedLength: Int)
      extends Buf.Composite {
      // ensure there is a need for a `Composite`
      if (bs.length <= 1)
        throw new IllegalArgumentException(s"Must have 2 or more bufs: $bs")
      if (computedLength <= 0)
        throw new IllegalArgumentException(s"Length must be positive: $computedLength")
      def bufs: IndexedSeq[Buf] = bs

      override def isEmpty: Boolean = false
    }
  }

  /**
   * A buffer representing an array of bytes.
   */
  class ByteArray(
      private[Buf] val bytes: Array[Byte],
      private[Buf] val begin: Int,
      private[Buf] val end: Int)
    extends Buf {

    def write(buf: Array[Byte], off: Int): Unit = {
      checkWriteArgs(buf.length, off)
      System.arraycopy(bytes, begin, buf, off, length)
    }

    def slice(from: Int, until: Int): Buf = {
      checkSliceArgs(from, until)

      if (isSliceEmpty(from, until)) Buf.Empty
      else if (isSliceIdentity(from, until)) this
      else {
        val cap = math.min(until, length)
        ByteArray.Owned(bytes, begin+from, math.min(begin+cap, end))
      }
    }

    def length: Int = end - begin

    override def toString: String = s"ByteArray(length=$length)"

    private[this] def equalsBytes(other: Array[Byte], offset: Int): Boolean = {
      var i = 0
      while (i < length) {
        if (bytes(begin+i) != other(offset+i)) {
          return false
        }
        i += 1
      }
      true
    }

    override def equals(other: Any): Boolean = other match {
      case ba: Buf.ByteArray if ba.length == length =>
        equalsBytes(ba.bytes, ba.begin)
      case buf: Buf if buf.length == length =>
        buf.unsafeByteArrayBuf match {
          case Some(ba) =>
            equalsBytes(ba.bytes, ba.begin)
          case None =>
            equalsBytes(buf.copiedByteArray, 0)
        }
      case _ => false
    }

    protected def unsafeByteArrayBuf: Option[Buf.ByteArray] = Some(this)
  }

  object ByteArray {

    /**
     * Construct a buffer representing the given bytes.
     */
    def apply(bytes: Byte*): Buf = Owned(bytes.toArray)

    /**
     * Safely coerce a buffer to a Buf.ByteArray, potentially without copying its underlying
     * data.
     */
    def coerce(buf: Buf): Buf.ByteArray = buf match {
      case buf: Buf.ByteArray => buf
      case _ => buf.unsafeByteArrayBuf match {
        case Some(b) => b
        case None =>
          val bytes = buf.copiedByteArray
          new ByteArray(bytes, 0, bytes.length)
      }
    }

    /** Owned non-copying constructors/extractors for Buf.ByteArray. */
    object Owned {

      /**
       * Construct a buffer representing the provided array of bytes
       * at the given offsets.
       */
      def apply(bytes: Array[Byte], begin: Int, end: Int): Buf =
        if (begin == end) Buf.Empty
        else new ByteArray(bytes, begin, end)

      /** Construct a buffer representing the provided array of bytes. */
      def apply(bytes: Array[Byte]): Buf = apply(bytes, 0, bytes.length)

      /** Extract the buffer's underlying offsets and array of bytes. */
      def unapply(buf: ByteArray): Option[(Array[Byte], Int, Int)] =
        Some((buf.bytes, buf.begin, buf.end))

      /**
       * Get a reference to a Buf's data as an array of bytes.
       *
       * A copy may be performed if necessary.
       */
      def extract(buf: Buf): Array[Byte] = Buf.ByteArray.coerce(buf) match {
        case Buf.ByteArray.Owned(bytes, 0, end) if end == bytes.length =>
          bytes
        case Buf.ByteArray.Shared(bytes) =>
          // If the unsafe version included offsets, we need to create a new array
          // containing only the relevant bytes.
          bytes
      }
    }

    /** Safe copying constructors / extractors for Buf.ByteArray. */
    object Shared {

      /** Construct a buffer representing a copy of an array of bytes at the given offsets. */
      def apply(bytes: Array[Byte], begin: Int, end: Int): Buf =
        if (begin == end) Buf.Empty
        else {
          val copy = java.util.Arrays.copyOfRange(bytes, begin, end-begin)
          new ByteArray(copy, 0, end-begin)
        }

      /** Construct a buffer representing a copy of the entire byte array. */
      def apply(bytes: Array[Byte]): Buf = apply(bytes, 0, bytes.length)

      /** Extract a copy of the buffer's underlying array of bytes. */
      def unapply(ba: ByteArray): Option[Array[Byte]] = Some(ba.copiedByteArray)

      /** Get a copy of a a Buf's data as an array of bytes. */
      def extract(buf: Buf): Array[Byte] = Buf.ByteArray.coerce(buf).copiedByteArray
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
  class ByteBuffer(private[Buf] val underlying: java.nio.ByteBuffer) extends Buf {
    def length: Int = underlying.remaining

    override def toString: String = s"ByteBuffer(length=$length)"

    def write(output: Array[Byte], off: Int): Unit = {
      checkWriteArgs(output.length, off)
      underlying.duplicate.get(output, off, length)
    }

    def slice(from: Int, until: Int): Buf = {
      checkSliceArgs(from, until)
      if (isSliceEmpty(from, until)) Buf.Empty
      else if (isSliceIdentity(from, until)) this
      else {
        val dup = underlying.duplicate()
        val limit = dup.position + math.min(until, length)
        if (dup.limit > limit) dup.limit(limit)
        dup.position(dup.position + from)
        new ByteBuffer(dup)
      }
    }

    override def equals(other: Any): Boolean = other match {
      case ByteBuffer(otherBB) =>
        underlying.equals(otherBB)
      case buf: Buf => Buf.equals(this, buf)
      case _ => false
    }

    protected def unsafeByteArrayBuf: Option[Buf.ByteArray] =
      if (underlying.hasArray) {
        val array = underlying.array
        val begin = underlying.arrayOffset + underlying.position
        val end = begin + underlying.remaining
        Some(new ByteArray(array, begin, end))
      } else None
  }

  object ByteBuffer {

    /** Extract a read-only view of the underlying [[java.nio.ByteBuffer]]. */
    def unapply(buf: ByteBuffer): Option[java.nio.ByteBuffer] =
      Some(buf.underlying.asReadOnlyBuffer)

    /** Coerce a generic buffer to a Buf.ByteBuffer, potentially without copying data. */
    def coerce(buf: Buf): ByteBuffer = buf match {
      case buf: ByteBuffer => buf
      case _ =>
        val bb = buf.unsafeByteArrayBuf match {
          case Some(ByteArray.Owned(bytes, begin, end)) =>
            java.nio.ByteBuffer.wrap(bytes, begin, end-begin)
          case None =>
            java.nio.ByteBuffer.wrap(buf.copiedByteArray)
        }
        new ByteBuffer(bb)
    }

    /** Owned non-copying constructors/extractors for Buf.ByteBuffer. */
    object Owned {

      // N.B. We cannot use ByteBuffer.asReadOnly to ensure correctness because
      // it prevents direct access to its underlying byte array.

      /**
       * Create a Buf.ByteBuffer by directly wrapping the provided [[java.nio.ByteBuffer]].
       */
      def apply(bb: java.nio.ByteBuffer): Buf =
        if (bb.remaining == 0) Buf.Empty
        else new ByteBuffer(bb)

      /** Extract the buffer's underlying [[java.nio.ByteBuffer]]. */
      def unapply(buf: ByteBuffer): Option[java.nio.ByteBuffer] = Some(buf.underlying)

      /**
       * Get a reference to a Buf's data as a ByteBuffer.
       *
       * A copy may be performed if necessary.
       */
      def extract(buf: Buf): java.nio.ByteBuffer = Buf.ByteBuffer.coerce(buf).underlying
    }

    /** Safe copying constructors/extractors for Buf.ByteBuffer. */
    object Shared {
      private[this] def copy(orig: java.nio.ByteBuffer): java.nio.ByteBuffer = {
        val copy = java.nio.ByteBuffer.allocate(orig.remaining)
        copy.put(orig.duplicate)
        copy.flip()
        copy
      }

      def apply(bb: java.nio.ByteBuffer): Buf = Owned(copy(bb))
      def unapply(buf: ByteBuffer): Option[java.nio.ByteBuffer] = Owned.unapply(buf).map(copy)
      def extract(buf: Buf): java.nio.ByteBuffer = copy(Owned.extract(buf))
    }
  }

  /**
   * Byte equality between two buffers. May copy.
   *
   * Relies on Buf.ByteArray.equals.
   */
  def equals(x: Buf, y: Buf): Boolean = {
    if (x.length != y.length) return false
    Buf.ByteArray.coerce(x).equals(Buf.ByteArray.coerce(y))
  }

  /** The 32-bit FNV-1 of Buf */
  def hash(buf: Buf): Int = finishHash(hashBuf(buf))

  // Adapted from util-hashing.
  private[this] val UintMax: Long = 0xFFFFFFFFL
  private[this] val Fnv1a32Prime: Int = 16777619
  private[this] val Fnv1a32Init: Long = 0x811c9dc5L
  private[this] def finishHash(hash: Long): Int = (hash & UintMax).toInt
  private[this] def hashBuf(buf: Buf, init: Long = Fnv1a32Init): Long = buf match {
    case b if b.isEmpty => init

    case buf: Buf.Composite =>
      var i = 0
      var h = init
      while (i < buf.bufs.length) {
        h = hashBuf(buf.bufs(i), h)
        i += 1
      }
      h

    case _ =>
      val ba = Buf.ByteArray.coerce(buf)
      var i = ba.begin
      var h = init
      while (i < ba.end) {
        h = (h ^ (ba.bytes(i) & 0xff)) * Fnv1a32Prime
        i += 1
      }
      h
  }

  /**
   * Return a string representing the buffer
   * contents in hexadecimal.
   */
  def slowHexString(buf: Buf): String = {
    val ba = Buf.ByteArray.coerce(buf)
    val digits = new StringBuilder(2 * ba.length)
    var i = ba.begin
    while (i < ba.end) {
      digits ++= f"${ba.bytes(i)}%02x"
      i += 1
    }
    digits.toString
  }

  /**
   * Create and deconstruct Utf-8 encoded buffers.
   *
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   */
  object Utf8 extends StringCoder(JChar.UTF_8)

  /**
   * Create and deconstruct 16-bit UTF buffers.
   *
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   */
  object Utf16 extends StringCoder(JChar.UTF_16)

  /**
   * Create and deconstruct buffers encoded by the 16-bit UTF charset
   * with big-endian byte order.
   *
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   */
  object Utf16BE extends StringCoder(JChar.UTF_16BE)

  /**
   * Create and deconstruct buffers encoded by the 16-bit UTF charset
   * with little-endian byte order.
   *
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   */
  object Utf16LE extends StringCoder(JChar.UTF_16LE)

  /**
   * Create and deconstruct buffers encoded by the
   * ISO Latin Alphabet No. 1 charset.
   *
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   */
  object Iso8859_1 extends StringCoder(JChar.ISO_8859_1)

  /**
   * Create and deconstruct buffers encoded by the 7-bit ASCII,
   * also known as ISO646-US or the Basic Latin block of the
   * Unicode character set.
   *
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   */
  object UsAscii extends StringCoder(JChar.US_ASCII)

  /**
   * a StringCoder for a given [[java.nio.charset.Charset]] provides an
   * encoder: String -> Buf and an extractor: Buf -> Option[String].
   *
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   */
  abstract class StringCoder(charset: Charset) {

    /**
     * Encode the String to its Buf representation per the charset
     */
    def apply(s: String): Buf =
      // Note: this was faster than `String.getBytes(Charset)` in JMH tests
      Buf.ByteArray.Owned(s.getBytes(charset.name))

    /**
     * @return Some(String representation of the Buf)
     * @note This extractor does *not* return None to indicate a failed
     *       or impossible decoding. Malformed or unmappable bytes will
     *       instead be silently replaced by the replacement character
     *       ("\uFFFD") in the returned String. This behavior may change
     *       in the future.
     */
    def unapply(buf: Buf): Option[String] =
      Some(new String(Buf.ByteArray.Owned.extract(buf), charset.name))
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
      arr(3) = ( i        & 0xff).toByte
      ByteArray.Owned(arr)
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
           (arr(3) & 0xff       )
        Some((value, rem))
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
      arr(7) = ( l        & 0xff).toByte
      ByteArray.Owned(arr)
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
           (arr(7) & 0xff).toLong
        Some((value, rem))
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
      arr(0) = ( i        & 0xff).toByte
      arr(1) = ((i >>  8) & 0xff).toByte
      arr(2) = ((i >> 16) & 0xff).toByte
      arr(3) = ((i >> 24) & 0xff).toByte
      ByteArray.Owned(arr)
    }

    def unapply(buf: Buf): Option[(Int, Buf)] =
      if (buf.length < 4) None else {
        val arr = new Array[Byte](4)
        buf.slice(0, 4).write(arr, 0)
        val rem = buf.slice(4, buf.length)

        val value =
          ( arr(0) & 0xff      ) |
          ((arr(1) & 0xff) <<  8) |
          ((arr(2) & 0xff) << 16) |
          ((arr(3) & 0xff) << 24)
        Some((value, rem))
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
      arr(0) = ( l        & 0xff).toByte
      arr(1) = ((l >>  8) & 0xff).toByte
      arr(2) = ((l >> 16) & 0xff).toByte
      arr(3) = ((l >> 24) & 0xff).toByte
      arr(4) = ((l >> 32) & 0xff).toByte
      arr(5) = ((l >> 40) & 0xff).toByte
      arr(6) = ((l >> 48) & 0xff).toByte
      arr(7) = ((l >> 56) & 0xff).toByte
      ByteArray.Owned(arr)
    }

    def unapply(buf: Buf): Option[(Long, Buf)] =
      if (buf.length < 8) None else {
        val arr = new Array[Byte](8)
        buf.slice(0, 8).write(arr, 0)
        val rem = buf.slice(8, buf.length)

        val value =
           (arr(0) & 0xff).toLong        |
          ((arr(1) & 0xff).toLong <<  8) |
          ((arr(2) & 0xff).toLong << 16) |
          ((arr(3) & 0xff).toLong << 24) |
          ((arr(4) & 0xff).toLong << 32) |
          ((arr(5) & 0xff).toLong << 40) |
          ((arr(6) & 0xff).toLong << 48) |
          ((arr(7) & 0xff).toLong << 56)
        Some((value, rem))
      }
  }
}
