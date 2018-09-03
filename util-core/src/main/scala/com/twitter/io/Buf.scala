package com.twitter.io

import com.twitter.io.Buf.{IndexedThree, IndexedTwo}
import java.nio.ReadOnlyBufferException
import java.nio.charset.{Charset, StandardCharsets => JChar}
import scala.annotation.switch
import scala.collection.immutable.VectorBuilder

/**
 * Buf represents a fixed, immutable byte buffer with efficient
 * positional access. Buffers may be sliced and concatenated,
 * and thus be used to implement bytestreams.
 *
 * @see [[com.twitter.io.Buf.ByteArray]] for an `Array[Byte]` backed
 *     implementation.
 * @see [[com.twitter.io.Buf.ByteBuffer]] for an `nio.ByteBuffer` backed
 *     implementation.
 * @see [[com.twitter.io.Buf.apply]] for creating a `Buf` from other `Bufs`
 * @see [[com.twitter.io.Buf.Empty]] for an empty `Buf`.
 */
abstract class Buf { outer =>

  // Cached hash code for the Buf.
  // Note: there is an opportunity for a race in computing cachedHashCode
  // but since the computed hash code is deterministic the worst case
  // scenario is duplication of work.
  private[this] var cachedHashCode = 0

  /**
   * Write the entire contents of this `Buf` into the given array at
   * the given offset. Partial writes aren't supported directly
   * through this API; they can be accomplished by first [[slice slicing]] the
   * buffer.
   * This method should be preferred over the `Buf.ByteArray.extract`, `Buf.ByteArray.Owned`,
   * etc family of functions when you want to control the destination of the data, as
   * opposed to letting the `Buf` implementation manage the destination. For example, if you
   * want to manually set the first bytes of an `Array[Byte]` and then efficiently copy the
   * contents of this `Buf` to the remaining space.
   *
   * @see [[write(ByteBuffer)]] for writing to nio buffers.
   *
   * @throws IllegalArgumentException when `output` is too small to
   * contain all the data.
   *
   * @note [[Buf]] implementors should use the helper [[checkWriteArgs]].
   */
  @throws(classOf[IllegalArgumentException])
  def write(output: Array[Byte], off: Int): Unit

  /**
   * Write the entire contents of this `Buf` into the given nio buffer.
   * Partial writes aren't supported directly through this API; they can be
   * accomplished by first [[slice slicing]] the buffer.
   * This method should be preferred over `Buf.ByteBuffer.extract`, `Buf.ByteArray.Owned`,
   * etc family of functions when you want to control the destination of the data, as
   * opposed to letting the `Buf` implementation manage the destination. For example, if
   * the data is destined for an IO operation, it may be preferable to provide a direct
   * nio `ByteBuffer` to ensure the avoidance of intermediate heap-based representations.
   *
   * @see [[write(Array[Byte], Int)]] for writing to byte arrays.
   *
   * @throws java.lang.IllegalArgumentException when `output` doesn't have enough
   * space as defined by `ByteBuffer.remaining()` to hold the contents of this `Buf`.
   *
   * @throws ReadOnlyBufferException if the provided buffer is read-only.
   *
   * @note [[Buf]] implementors should use the helper [[checkWriteArgs]].
   */
  @throws(classOf[IllegalArgumentException])
  @throws(classOf[ReadOnlyBufferException])
  def write(output: java.nio.ByteBuffer): Unit

  /**
   * The number of bytes in the buffer
   */
  def length: Int

  /**
   * Returns a new buffer representing a slice of this buffer, delimited
   * by the indices `from` inclusive and `until` exclusive: `[from, until)`.
   * Out of bounds indices are truncated. Negative indices are not accepted.
   *
   * @note [[Buf]] implementors should use the helpers [[Buf.checkSliceArgs]],
   *       [[isSliceEmpty]], and [[isSliceIdentity]].
   */
  def slice(from: Int, until: Int): Buf

  /**
   * Concatenate this buffer with the given buffer.
   */
  final def concat(right: Buf): Buf = {
    if (this.isEmpty) right
    else if (right.isEmpty) this
    else
      this match {
        // This could be much cleaner as a Tuple match, but we want to avoid the allocation.
        case Buf.Composite.Impl(left: Vector[Buf], leftLength) =>
          right match {
            case Buf.Composite.Impl(right: Vector[Buf], rightLength) =>
              Buf.Composite.Impl(Buf.fastConcat(left, right), leftLength + rightLength)
            case Buf.Composite.Impl(right: IndexedTwo, rightLength) =>
              Buf.Composite.Impl(left :+ right.a :+ right.b, leftLength + rightLength)
            case Buf.Composite.Impl(right: IndexedThree, rightLength) =>
              Buf.Composite.Impl(left :+ right.a :+ right.b :+ right.c, leftLength + rightLength)
            case _ =>
              Buf.Composite.Impl(left :+ right, leftLength + right.length)
          }

        case Buf.Composite.Impl(left: IndexedTwo, leftLength) =>
          right match {
            case Buf.Composite.Impl(right: Vector[Buf], rightLength) =>
              Buf.Composite.Impl(left.a +: left.b +: right, leftLength + rightLength)
            case Buf.Composite.Impl(right: IndexedTwo, rightLength) =>
              Buf.Composite.Impl(
                (new VectorBuilder[Buf] += left.a += left.b += right.a += right.b).result(),
                leftLength + rightLength
              )
            case Buf.Composite.Impl(right: IndexedThree, rightLength) =>
              Buf.Composite.Impl(
                (new VectorBuilder[Buf] += left.a += left.b += right.a += right.b += right.c)
                  .result(),
                leftLength + rightLength
              )
            case _ =>
              Buf.Composite.Impl(
                new IndexedThree(left.a, left.b, right), leftLength + right.length
              )
          }

        case Buf.Composite.Impl(left: IndexedThree, leftLength) =>
          right match {
            case Buf.Composite.Impl(right: Vector[Buf], rightLength) =>
              Buf.Composite.Impl(left.a +: left.b +: left.c +: right, leftLength + rightLength)
            case Buf.Composite.Impl(right: IndexedTwo, rightLength) =>
              Buf.Composite.Impl(
                (new VectorBuilder[Buf] += left.a += left.b += left.c += right.a += right.b)
                  .result(),
                leftLength + rightLength
              )
            case Buf.Composite.Impl(right: IndexedThree, rightLength) =>
              Buf.Composite.Impl(
                (new VectorBuilder[Buf] += left.a += left.b += left.c += right.a += right.b += right.c).result(),
                leftLength + rightLength
              )
            case _ =>
              Buf.Composite.Impl(
                (new VectorBuilder[Buf] += left.a += left.b += left.c += right).result(),
                leftLength + right.length
              )
          }

        case left =>
          right match {
            case Buf.Composite.Impl(right: Vector[Buf], rightLength) =>
              Buf.Composite.Impl(left +: right, left.length + rightLength)
            case Buf.Composite.Impl(right: IndexedTwo, rightLength) =>
              Buf.Composite.Impl(new IndexedThree(left, right.a, right.b), left.length + rightLength)
            case Buf.Composite.Impl(right: IndexedThree, rightLength) =>
              Buf.Composite.Impl(
                (new VectorBuilder[Buf] += left += right.a += right.b += right.c).result(),
                left.length + rightLength
              )
            case _ =>
              Buf.Composite.Impl(new IndexedTwo(left, right), left.length + right.length)
          }
      }
  }

  /**
   * Returns the byte at the given index.
   */
  def get(index: Int): Byte

  /**
   * Process the `Buf` 1-byte at a time using the given
   * [[Buf.Processor]], starting at index `0` until
   * index [[length]]. Processing will halt if the processor
   * returns `false` or after processing the final byte.
   *
   * @return -1 if the processor processed all bytes or
   *         the last processed index if the processor returns
   *         `false`.
   *
   * @note this mimics the design of Netty 4's
   *       `io.netty.buffer.ByteBuf.forEachByte`
   */
  def process(processor: Buf.Processor): Int =
    process(0, length, processor)

  /**
   * Process the `Buf` 1-byte at a time using the given
   * [[Buf.Processor]], starting at index `from` until
   * index `until`. Processing will halt if the processor
   * returns `false` or after processing the final byte.
   *
   * @return -1 if the processor processed all bytes or
   *         the last processed index if the processor returns
   *         `false`.
   *         Will return -1 if `from` is greater than or equal to
   *         `until` or [[length]].
   *         Will return -1 if `until` is greater than or equal to
   *         [[length]].
   *
   * @param from the starting index, inclusive. Must be non-negative.
   *
   * @param until the ending index, exclusive. Must be non-negative.
   *
   * @note this mimics the design of Netty 4's
   *       `io.netty.buffer.ByteBuf.forEachByte`
   */
  def process(from: Int, until: Int, processor: Buf.Processor): Int

  override def equals(other: Any): Boolean = other match {
    case other: Buf => Buf.equals(this, other)
    case _ => false
  }

  final override def hashCode: Int = {
    // A magic number of 0 signifies that the hash code has not been computed.
    // Note: 0 may be the legitimate hash code of a Buf, in which case the
    // hash code will have extra collisions with -1.
    if (cachedHashCode == 0) {
      val computed = computeHashCode
      cachedHashCode = if (computed == 0) -1 else computed
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

  /** Helps implementations of [[slice]]. */
  protected[this] def isSliceEmpty(from: Int, until: Int): Boolean =
    Buf.isSliceEmpty(from, until, length)

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
        s"Output too small, capacity=${outputLen - outputOff}, need=$len"
      )
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
 * Note: There are Java-friendly APIs for this object at `com.twitter.io.Bufs`.
 */
object Buf {

  /**
   * @return `true` if the processor would like to continue processing
   *        more bytes and `false` otherwise.
   *
   * @see [[Buf.process]]
   *
   * @note this is not a `Function1[Byte, Boolean]` despite very
   *       much fitting that interface. This was done to avoiding boxing
   *       of the `Bytes` which was quite squirrely and had an impact on
   *       performance.
   */
  abstract class Processor {
    def apply(byte: Byte): Boolean
  }

  private class NoopBuf extends Buf {
    def write(buf: Array[Byte], off: Int): Unit =
      checkWriteArgs(buf.length, off)

    def write(buffer: java.nio.ByteBuffer): Unit = ()

    override val isEmpty = true
    def length: Int = 0
    def slice(from: Int, until: Int): Buf = {
      checkSliceArgs(from, until)
      this
    }
    protected def unsafeByteArrayBuf: Option[Buf.ByteArray] = None
    def get(index: Int): Byte =
      throw new IndexOutOfBoundsException(s"Index out of bounds: $index")
    def process(from: Int, until: Int, processor: Processor): Int = {
      checkSliceArgs(from, until)
      -1
    }
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
    bufs.foreach {
      case b: Composite.Impl =>
        // Guaranteed to be non-empty by construction
        length += b.length
        builder ++= b.bufs

      case b =>
        val len = b.length
        if (len > 0) {
          length += len
          builder += b
        }
    }

    val filtered = builder.result()
    if (length == 0)
      Buf.Empty
    else if (filtered.size == 1)
      filtered.head
    else
      new Composite.Impl(filtered, length)
  }

  /** Helps Buf implementations validate the arguments to slicing functions. */
  def checkSliceArgs(from: Int, until: Int): Unit = {
    if (from < 0)
      throw new IllegalArgumentException(s"'from' must be non-negative: $from")
    if (until < 0)
      throw new IllegalArgumentException(s"'until' must be non-negative: $until")
  }

  // Helper for determining if a given slice is empty
  private def isSliceEmpty(from: Int, until: Int, underlyingLength: Int): Boolean =
    until <= from || from >= underlyingLength

  private final class IndexedTwo(val a: Buf, val b: Buf) extends IndexedSeq[Buf] {
    def apply(idx: Int): Buf = (idx: @switch) match {
      case 0 => a
      case 1 => b
      case _ => throw new IndexOutOfBoundsException(s"$idx")
    }

    def length: Int = 2
  }

  private final class IndexedThree(val a: Buf, val b: Buf, val c: Buf) extends IndexedSeq[Buf] {
    def apply(idx: Int): Buf = (idx: @switch) match {
      case 0 => a
      case 1 => b
      case 2 => c
      case _ => throw new IndexOutOfBoundsException(s"$idx")
    }

    def length: Int = 3
  }

  /**
   * A `Buf` which is composed of other `Bufs`.
   *
   * @see [[Buf.apply]] for creating new instances.
   */
  sealed abstract class Composite extends Buf {
    def bufs: IndexedSeq[Buf]
    protected def computedLength: Int

    // the factory method requires non-empty Bufs
    override def isEmpty: Boolean = false

    def length: Int = computedLength

    override def toString: String = s"Buf.Composite(length=$length)"

    def write(output: Array[Byte], off: Int): Unit = {
      checkWriteArgs(output.length, off)
      var offset = off
      var i = 0
      while (i < bufs.length) {
        val buf = bufs(i)
        buf.write(output, offset)
        offset += buf.length
        i += 1
      }
    }

    def write(buffer: java.nio.ByteBuffer): Unit = {
      checkWriteArgs(buffer.remaining, 0)
      var i = 0
      while (i < bufs.length) {
        bufs(i).write(buffer)
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
          else first +: bufs.slice(start + 1, length)
        )
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
          else first +: bufs.slice(start + 1, finish) :+ last
        )
      }
    }

    protected def unsafeByteArrayBuf: Option[ByteArray] = None

    private[this] def equalsIndexed(other: Buf): Boolean = {
      var otherIdx = 0
      var bufIdx = 0
      while (bufIdx < bufs.length) {
        val buf = bufs(bufIdx)
        val bufLen = buf.length
        var byteIdx = 0
        while (otherIdx < length && byteIdx < bufLen) {
          if (other.get(otherIdx) != buf.get(byteIdx))
            return false
          byteIdx += 1
          otherIdx += 1
        }
        bufIdx += 1
      }
      true
    }

    override def equals(other: Any): Boolean = other match {
      case otherBuf: Buf if length == otherBuf.length =>
        otherBuf match {
          case Composite(otherBufs) =>
            // this is 2 nested loops, with the outer loop tracking which
            // Buf's they are on. The inner loop compares individual bytes across
            // the Bufs "segments".
            var otherBufIdx = 0
            var bufIdx = 0
            var byteIdx = 0
            var otherByteIdx = 0
            while (bufIdx < bufs.length && otherBufIdx < otherBufs.length) {
              val buf = bufs(bufIdx)
              val otherB = otherBufs(otherBufIdx)
              while (byteIdx < buf.length && otherByteIdx < otherB.length) {
                if (buf.get(byteIdx) != otherB.get(otherByteIdx))
                  return false
                byteIdx += 1
                otherByteIdx += 1
              }
              if (byteIdx == buf.length) {
                byteIdx = 0
                bufIdx += 1
              }
              if (otherByteIdx == otherB.length) {
                otherByteIdx = 0
                otherBufIdx += 1
              }
            }
            true

          case _ =>
            otherBuf.unsafeByteArrayBuf match {
              case Some(otherBab) =>
                equalsIndexed(otherBab)
              case None =>
                equalsIndexed(otherBuf)
            }
        }

      case _ =>
        false
    }
  }

  object Composite {
    def unapply(buf: Composite): Option[IndexedSeq[Buf]] =
      Some(buf.bufs)

    /** 
      * Basic implementation of a [[Buf]] created from n-`Bufs`.
      */
    private[Buf] final case class Impl(bufs: IndexedSeq[Buf], protected val computedLength: Int)
        extends Buf.Composite {

      bufs match {
        case _: Vector[Buf] =>
        case _: IndexedTwo =>
        case _: IndexedThree =>
        case _ => throw new IllegalArgumentException(s"Type not allowed: ${bufs.getClass.getName}")
      }

      // ensure there is a need for a `Composite`
      if (bufs.length <= 1)
        throw new IllegalArgumentException(s"Must have 2 or more bufs: $bufs")
      if (computedLength <= 0)
        throw new IllegalArgumentException(s"Length must be positive: $computedLength")

      // the factory method requires non-empty Bufs
      override def isEmpty: Boolean = false

      def get(index: Int): Byte = {
        var bufIdx = 0
        var byteIdx = 0
        while (bufIdx < bufs.length) {
          val buf = bufs(bufIdx)
          val bufLen = buf.length
          if (index < byteIdx + bufLen) {
            return buf.get(index - byteIdx)
          } else {
            byteIdx += bufLen
          }
          bufIdx += 1
        }
        throw new IndexOutOfBoundsException(s"Index out of bounds: $index")
      }

      def process(from: Int, until: Int, processor: Processor): Int = {
        checkSliceArgs(from, until)
        if (isSliceEmpty(from, until)) return -1
        var i = 0
        var bufIdx = 0
        var continue = true
        while (continue && i < until && bufIdx < bufs.length) {
          val buf = bufs(bufIdx)
          val bufLen = buf.length

          if (i + bufLen < from) {
            // skip ahead to the right Buf for `from`
            bufIdx += 1
            i += bufLen
          } else {
            // ensure we are positioned correctly in the first Buf
            var byteIdx =
              if (i >= from) 0
              else from - i
            val endAt = math.min(bufLen, until - i)
            while (continue && byteIdx < endAt) {
              val byte = buf.get(byteIdx)
              if (processor(byte)) {
                byteIdx += 1
              } else {
                continue = false
              }
            }
            bufIdx += 1
            i += byteIdx
          }
        }
        if (continue) -1
        else i
      }
    }
  }

  /**
   * A buffer representing an array of bytes.
   */
  class ByteArray(
    private[Buf] val bytes: Array[Byte],
    private[Buf] val begin: Int,
    private[Buf] val end: Int
  ) extends Buf {

    def get(index: Int): Byte = {
      val off = begin + index
      if (off >= end)
        throw new IndexOutOfBoundsException(s"Index out of bounds: $index")
      bytes(off)
    }

    def process(from: Int, until: Int, processor: Processor): Int = {
      checkSliceArgs(from, until)
      if (isSliceEmpty(from, until)) return -1
      var i = from
      var continue = true
      val endAt = math.min(until, length)
      while (continue && i < endAt) {
        val byte = bytes(begin + i)
        if (processor(byte))
          i += 1
        else
          continue = false
      }
      if (continue) -1
      else i
    }

    def write(buf: Array[Byte], off: Int): Unit = {
      checkWriteArgs(buf.length, off)
      System.arraycopy(bytes, begin, buf, off, length)
    }

    def write(buffer: java.nio.ByteBuffer): Unit = {
      checkWriteArgs(buffer.remaining, 0)
      buffer.put(bytes, begin, length)
    }

    def slice(from: Int, until: Int): Buf = {
      checkSliceArgs(from, until)
      if (isSliceEmpty(from, until)) Buf.Empty
      else if (isSliceIdentity(from, until)) this
      else {
        val cap = math.min(until, length)
        ByteArray.Owned(bytes, begin + from, math.min(begin + cap, end))
      }
    }

    def length: Int = end - begin

    override def toString: String = s"ByteArray(length=$length)"

    private[this] def equalsBytes(other: Array[Byte], offset: Int): Boolean = {
      var i = 0
      while (i < length) {
        if (bytes(begin + i) != other(offset + i)) {
          return false
        }
        i += 1
      }
      true
    }

    override def equals(other: Any): Boolean = other match {
      case c: Buf.Composite =>
        c == this
      case other: Buf if other.length == length =>
        other match {
          case ba: Buf.ByteArray =>
            equalsBytes(ba.bytes, ba.begin)
          case _ =>
            other.unsafeByteArrayBuf match {
              case Some(bs) =>
                equalsBytes(bs.bytes, bs.begin)
              case None =>
                val processor = new Processor {
                  private[this] var pos = 0
                  def apply(b: Byte): Boolean = {
                    if (b == bytes(begin + pos)) {
                      pos += 1
                      true
                    } else {
                      false
                    }
                  }
                }
                other.process(processor) == -1
            }
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
      case _ =>
        buf.unsafeByteArrayBuf match {
          case Some(b) => b
          case None =>
            val bytes = buf.copiedByteArray
            new ByteArray(bytes, 0, bytes.length)
        }
    }

    /** Owned non-copying constructors/extractors for Buf.ByteArray. */
    object Owned {

      /**
       * Construct a buffer representing the provided array of bytes, delimited
       * by the indices `from` inclusive and `until` exclusive: `[begin, end)`.
       * Out of bounds indices are truncated. Negative indices are not accepted.
       */
      def apply(bytes: Array[Byte], begin: Int, end: Int): Buf = {
        checkSliceArgs(begin, end)
        if (isSliceEmpty(begin, end, bytes.length)) Buf.Empty
        else new ByteArray(bytes, begin, math.min(end, bytes.length))
      }

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

      /**
       * Construct a buffer representing a copy of the array of bytes, delimited
       * by the indices `from` inclusive and `until` exclusive: `[begin, end)`.
       * Out of bounds indices are truncated. Negative indices are not accepted.
       */
      def apply(bytes: Array[Byte], begin: Int, end: Int): Buf = {
        checkSliceArgs(begin, end)
        if (isSliceEmpty(begin, end, bytes.length)) Buf.Empty
        else {
          val copy = java.util.Arrays.copyOfRange(bytes, begin, math.min(end, bytes.length))
          new ByteArray(copy, 0, copy.length)
        }
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

    def get(index: Int): Byte =
      underlying.get(underlying.position() + index)

    def process(from: Int, until: Int, processor: Processor): Int = {
      checkSliceArgs(from, until)
      if (isSliceEmpty(from, until)) return -1
      val pos = underlying.position()
      var i = from
      var continue = true
      val endAt = math.min(until, length)
      while (continue && i < endAt) {
        val byte = underlying.get(pos + i)
        if (processor(byte))
          i += 1
        else
          continue = false
      }
      if (continue) -1
      else i
    }

    override def toString: String = s"ByteBuffer(length=$length)"

    def write(output: Array[Byte], off: Int): Unit = {
      checkWriteArgs(output.length, off)
      underlying.duplicate.get(output, off, length)
    }

    def write(buffer: java.nio.ByteBuffer): Unit = {
      checkWriteArgs(buffer.remaining, 0)
      buffer.put(underlying.duplicate)
    }

    def slice(from: Int, until: Int): Buf = {
      checkSliceArgs(from, until)
      if (isSliceEmpty(from, until)) Buf.Empty
      else if (isSliceIdentity(from, until)) this
      else {
        val dup = underlying.duplicate()
        val limit = dup.position() + math.min(until, length)
        if (dup.limit() > limit) dup.limit(limit)
        dup.position(dup.position() + from)
        new ByteBuffer(dup)
      }
    }

    override def equals(other: Any): Boolean = other match {
      case ByteBuffer(otherBB) => underlying.equals(otherBB)
      case buf: Buf => Buf.equals(this, buf)
      case _ => false
    }

    protected def unsafeByteArrayBuf: Option[Buf.ByteArray] =
      if (underlying.hasArray) {
        val array = underlying.array
        val begin = underlying.arrayOffset + underlying.position()
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
            java.nio.ByteBuffer.wrap(bytes, begin, end - begin)
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
   * Byte equality between two buffers.
   */
  def equals(x: Buf, y: Buf): Boolean = {
    if (x eq y) return true

    val len = x.length
    if (len != y.length) return false

    // Prefer Composite's equals implementation to minimize overhead
    x match {
      case _: Composite => return x == y
      case _ => ()
    }
    y match {
      case _: Composite => return y == x
      case _ => ()
    }

    val processor = new Processor {
      private[this] var pos = 0
      def apply(b: Byte): Boolean = {
        if (b == y.get(pos)) {
          pos += 1
          true
        } else {
          false
        }
      }
    }
    x.process(processor) == -1
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

    case c: Buf.Composite =>
      var i = 0
      var h = init
      while (i < c.bufs.length) {
        h = hashBuf(c.bufs(i), h)
        i += 1
      }
      h

    case _ =>
      // use an explicit class in order to have fast access to `hash`
      // without boxing.
      class HashingProcessor(var hash: Long) extends Processor {
        def apply(byte: Byte): Boolean = {
          hash = (hash ^ (byte & 0xff)) * Fnv1a32Prime
          true
        }
      }
      val processor = new HashingProcessor(init)
      buf.process(processor)
      processor.hash
  }

  /**
   * Return a string representing the buffer
   * contents in hexadecimal.
   */
  def slowHexString(buf: Buf): String = {
    val len = buf.length
    val digits = new StringBuilder(2 * len)
    var i = 0
    while (i < len) {
      digits ++= f"${buf.get(i)}%02x"
      i += 1
    }
    digits.toString
  }

  /**
   * Create and deconstruct Utf-8 encoded buffers.
   *
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   *
   * @note See `com.twitter.io.Bufs.UTF_8` for a Java-friendly API.
   */
  object Utf8 extends StringCoder(JChar.UTF_8)

  /**
   * Create and deconstruct 16-bit UTF buffers.
   *
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   *
   * @note See `com.twitter.io.Bufs.UTF_16` for a Java-friendly API.
   */
  object Utf16 extends StringCoder(JChar.UTF_16)

  /**
   * Create and deconstruct buffers encoded by the 16-bit UTF charset
   * with big-endian byte order.
   *
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   *
   * @note See `com.twitter.io.Bufs.UTF_16BE` for a Java-friendly API.
   */
  object Utf16BE extends StringCoder(JChar.UTF_16BE)

  /**
   * Create and deconstruct buffers encoded by the 16-bit UTF charset
   * with little-endian byte order.
   *
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   *
   * @note See `com.twitter.io.Bufs.UTF_16LE` for a Java-friendly API.
   */
  object Utf16LE extends StringCoder(JChar.UTF_16LE)

  /**
   * Create and deconstruct buffers encoded by the
   * ISO Latin Alphabet No. 1 charset.
   *
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   *
   * @note See `com.twitter.io.Bufs.ISO_8859_1` for a Java-friendly API.
   */
  object Iso8859_1 extends StringCoder(JChar.ISO_8859_1)

  /**
   * Create and deconstruct buffers encoded by the 7-bit ASCII,
   * also known as ISO646-US or the Basic Latin block of the
   * Unicode character set.
   *
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   *
   * @note See `com.twitter.io.Bufs.US_ASCII` for a Java-friendly API.
   */
  object UsAscii extends StringCoder(JChar.US_ASCII)

  /**
   * A [[StringCoder]] for a given [[java.nio.charset.Charset]] provides an
   * [[apply(String) encoder]]: `String` to [[Buf]]
   * and an [[unapply(Buf) extractor]]: [[Buf]] to `Option[String]`.
   *
   * @note Malformed and unmappable input is silently replaced
   *       see [[java.nio.charset.CodingErrorAction.REPLACE]]
   *
   * @see [[Utf8]] for UTF-8 encoding and decoding, and `Bufs.UTF8` for
   *      Java users. Constants exist for other standard charsets as well.
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
    def unapply(buf: Buf): Option[String] = Some(decodeString(buf, charset))
  }

  /**
   * Decode a `Buf` using the specified `Charset`
   */
  def decodeString(buf: Buf, charset: Charset): String = {
    // Coercing to a `ByteArray` gives us to best chance of not copying the bytes.
    val byteArray = Buf.ByteArray.coerce(buf)
    new String(byteArray.bytes, byteArray.begin, byteArray.length, charset)
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
      arr(2) = ((i >> 8) & 0xff).toByte
      arr(3) = (i & 0xff).toByte
      ByteArray.Owned(arr)
    }

    def unapply(buf: Buf): Option[(Int, Buf)] =
      if (buf.length < 4) None
      else {
        val arr = new Array[Byte](4)
        buf.slice(0, 4).write(arr, 0)
        val rem = buf.slice(4, buf.length)

        val value =
          ((arr(0) & 0xff) << 24) |
            ((arr(1) & 0xff) << 16) |
            ((arr(2) & 0xff) << 8) |
            (arr(3) & 0xff)
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
      arr(6) = ((l >> 8) & 0xff).toByte
      arr(7) = (l & 0xff).toByte
      ByteArray.Owned(arr)
    }

    def unapply(buf: Buf): Option[(Long, Buf)] =
      if (buf.length < 8) None
      else {
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
            ((arr(6) & 0xff).toLong << 8) |
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
      arr(0) = (i & 0xff).toByte
      arr(1) = ((i >> 8) & 0xff).toByte
      arr(2) = ((i >> 16) & 0xff).toByte
      arr(3) = ((i >> 24) & 0xff).toByte
      ByteArray.Owned(arr)
    }

    def unapply(buf: Buf): Option[(Int, Buf)] =
      if (buf.length < 4) None
      else {
        val arr = new Array[Byte](4)
        buf.slice(0, 4).write(arr, 0)
        val rem = buf.slice(4, buf.length)

        val value =
          (arr(0) & 0xff) |
            ((arr(1) & 0xff) << 8) |
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
      arr(0) = (l & 0xff).toByte
      arr(1) = ((l >> 8) & 0xff).toByte
      arr(2) = ((l >> 16) & 0xff).toByte
      arr(3) = ((l >> 24) & 0xff).toByte
      arr(4) = ((l >> 32) & 0xff).toByte
      arr(5) = ((l >> 40) & 0xff).toByte
      arr(6) = ((l >> 48) & 0xff).toByte
      arr(7) = ((l >> 56) & 0xff).toByte
      ByteArray.Owned(arr)
    }

    def unapply(buf: Buf): Option[(Long, Buf)] =
      if (buf.length < 8) None
      else {
        val arr = new Array[Byte](8)
        buf.slice(0, 8).write(arr, 0)
        val rem = buf.slice(8, buf.length)

        val value =
          (arr(0) & 0xff).toLong |
            ((arr(1) & 0xff).toLong << 8) |
            ((arr(2) & 0xff).toLong << 16) |
            ((arr(3) & 0xff).toLong << 24) |
            ((arr(4) & 0xff).toLong << 32) |
            ((arr(5) & 0xff).toLong << 40) |
            ((arr(6) & 0xff).toLong << 48) |
            ((arr(7) & 0xff).toLong << 56)
        Some((value, rem))
      }
  }

  // Modeled after the Vector.++ operator, but use indexes instead of `for`
  // comprehensions for the optimized paths to save allocations and time.
  // See Scala ticket SI-7725 for details.
  private def fastConcat(head: Vector[Buf], tail: Vector[Buf]): Vector[Buf] = {
    val tlen = tail.length
    val hlen = head.length

    if (tlen <= TinyIsFaster || tlen < (hlen / ConcatFasterFactor)) {
      var i = 0
      var acc = head
      while (i < tlen) {
        acc :+= tail(i)
        i += 1
      }
      acc
    } else if (hlen <= TinyIsFaster || hlen < (tlen / ConcatFasterFactor)) {
      var i = head.length - 1
      var acc = tail
      while (i >= 0) {
        acc = head(i) +: acc
        i -= 1
      }
      acc
    } else {
      val builder = new VectorBuilder[Buf]
      val headIt = head.iterator
      while (headIt.hasNext) builder += headIt.next()
      val tailIt = tail.iterator
      while (tailIt.hasNext) builder += tailIt.next()
      builder.result()
    }
  }

  // Length at which eC append/prepend operations are always faster
  private[this] val TinyIsFaster = 2
  // Length ratio at which eC append/prepend operations are slower than making a new collection
  private[this] val ConcatFasterFactor = 32
}
