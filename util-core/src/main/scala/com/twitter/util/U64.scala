// Copyright 2010 Twitter, Inc.
//
// Unsigned math on (64-bit) longs.

package com.twitter.util

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}
import scala.language.implicitConversions

@deprecated("Use Java 8 unsigned Long APIs instead", "2018-02-13")
class RichU64Long(l64: Long) {
  import U64._

  def u64_<(y: Long): Boolean = u64_lt(l64, y)

  /** exclusive: (a, b) */
  def u64_within(a: Long, b: Long): Boolean = u64_lt(a, l64) && u64_lt(l64, b)

  /** inclusive: [a, b] */
  def u64_contained(a: Long, b: Long): Boolean = !u64_lt(l64, a) && !u64_lt(b, l64)

  def u64ToBigInt: BigInt = u64ToBigint(l64)

  def u64_/(y: Long): Long =
    (u64ToBigInt / u64ToBigint(y)).longValue

  def u64_%(y: Long): Long =
    (u64ToBigInt % u64ToBigint(y)).longValue

  // Other arithmetic operations don't need unsigned equivalents
  // with 2s complement.

  def toU64ByteArray: Array[Byte] = {
    val bytes = new ByteArrayOutputStream(8)
    new DataOutputStream(bytes).writeLong(l64)
    bytes.toByteArray
  }

  def toU64HexString = (new RichU64ByteArray(toU64ByteArray)).toU64HexString
}

@deprecated("Use Java 8 unsigned Long APIs instead", "2018-02-13")
class RichU64ByteArray(bytes: Array[Byte]) {
  private def deSign(b: Byte): Int = {
    if (b < 0) {
      b + 256
    } else {
      b
    }
  }

  // Note: this is padded, so we have lexical ordering.
  def toU64HexString = bytes.map(deSign).map("%02x".format(_)).reduceLeft(_ + _)
  def toU64Long = new DataInputStream(new ByteArrayInputStream(bytes)).readLong
}

/**
 * RichU64String parses string as a 64-bit unsigned hexadecimal long,
 * outputting a signed Long or a byte array. Valid input is 1-16 hexadecimal
 * digits (0-9, a-z, A-Z).
 *
 * @throws NumberFormatException if string is not a non-negative hexadecimal
 * string
 */
@deprecated("Use Java 8 unsigned Long APIs instead", "2018-02-13")
class RichU64String(string: String) {
  private[this] def validateHexDigit(c: Char): Unit = {
    if (!(('0' <= c && c <= '9') ||
        ('a' <= c && c <= 'f') ||
        ('A' <= c && c <= 'F'))) {
      throw new NumberFormatException("For input string: \"" + string + "\"")
    }
  }

  if (string.length > 16) {
    throw new NumberFormatException("Number longer than 16 hex characters")
  } else if (string.isEmpty) {
    throw new NumberFormatException("Empty string")
  }
  string.foreach(validateHexDigit)

  def toU64ByteArray: Array[Byte] = {
    val padded = "0" * (16 - string.length()) + string
    (0 until 16 by 2)
      .map(i => {
        val parsed = Integer.parseInt(padded.slice(i, i + 2), 16)
        assert(parsed >= 0)
        parsed.toByte
      })
      .toArray
  }

  def toU64Long: Long = (new RichU64ByteArray(toU64ByteArray)).toU64Long
}

@deprecated("Use Java 8 unsigned Long APIs instead", "2018-02-13")
object U64 {
  private val bigInt0x8000000000000000L = (0x7FFFFFFFFFFFFFFFL: BigInt) + 1

  val U64MAX = 0xFFFFFFFFFFFFFFFFL
  val U64MIN = 0L

  def u64ToBigint(x: Long): BigInt =
    if ((x & 0x8000000000000000L) != 0L)
      ((x & 0x7FFFFFFFFFFFFFFFL): BigInt) + bigInt0x8000000000000000L
    else
      x: BigInt

  // compares x < y
  def u64_lt(x: Long, y: Long): Boolean =
    if (x < 0 == y < 0)
      x < y // signed comparison, straightforward!
    else
      x > y // x is less if it doesn't have its high bit set (<0)

  implicit def longToRichU64Long(x: Long): RichU64Long = new RichU64Long(x)

  implicit def byteArrayToRichU64ByteArray(bytes: Array[Byte]): RichU64ByteArray =
    new RichU64ByteArray(bytes)

  implicit def stringToRichU64String(string: String): RichU64String = new RichU64String(string)
}
