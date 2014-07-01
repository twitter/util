// Copyright 2010 Twitter, Inc.
//
// Unsigned math on (64-bit) longs.

package com.twitter.util

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

class RichU64Long(l64: Long) {
  import U64._

  def u64_<(y: Long) = u64_lt(l64, y)

  // exclusive: (a, b)
  def u64_within(a: Long, b: Long) = u64_lt(a, l64) && u64_lt(l64, b)
  // inclusive: [a, b]
  def u64_contained(a: Long, b: Long) = !u64_lt(l64, a) && !u64_lt(b, l64)

  def u64ToBigInt = u64ToBigint(l64)

  def u64_/(y: Long) =
    (u64ToBigInt / u64ToBigint(y)).longValue

  def u64_%(y: Long) =
    (u64ToBigInt % u64ToBigint(y)).longValue

  // Other arithmetic operations don't need unsigned equivalents
  // with 2s complement.

  def toU64ByteArray = {
    val bytes = new ByteArrayOutputStream(8)
    new DataOutputStream(bytes).writeLong(l64)
    bytes.toByteArray
  }

  def toU64HexString = (new RichU64ByteArray(toU64ByteArray)).toU64HexString
}

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

class RichU64String(string: String) {
  if (string.length > 16) {
    throw new NumberFormatException("Number longer than 16 hex characters")
  }

  def toU64ByteArray = {
    val padded = "0" * (16-string.length()) + string
    (0 until 16 by 2).map(i => Integer.parseInt(padded.slice(i, i + 2), 16).toByte).toArray
  }

  def toU64Long = (new RichU64ByteArray(toU64ByteArray)).toU64Long
}

object U64 {
  private val bigInt0x8000000000000000L = (0x7FFFFFFFFFFFFFFFL:BigInt) + 1

  val U64MAX = 0xFFFFFFFFFFFFFFFFL
  val U64MIN = 0L

  def u64ToBigint(x: Long): BigInt =
    if ((x & 0x8000000000000000L) != 0L)
      ((x & 0x7FFFFFFFFFFFFFFFL): BigInt) + bigInt0x8000000000000000L
    else
      x: BigInt

  // compares x < y
  def u64_lt(x: Long, y: Long) =
    if (x < 0 == y < 0)
      x < y  // signed comparison, straightforward!
    else
      x > y  // x is less if it doesn't have its high bit set (<0)

  implicit def longToRichU64Long(x: Long)                      = new RichU64Long(x)
  implicit def byteArrayToRichU64ByteArray(bytes: Array[Byte]) = new RichU64ByteArray(bytes)
  implicit def stringToRichU64String(string: String)           = new RichU64String(string)
}
