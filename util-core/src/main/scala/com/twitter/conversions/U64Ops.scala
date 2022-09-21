package com.twitter.conversions

import scala.language.implicitConversions

object U64Ops {

  /**
   * Parses this HEX string as an unsigned 64-bit long value. Be careful, this can throw
   * `NumberFormatException`.
   *
   * See `java.lang.Long.parseUnsignedLong()` for more information.
   */
  implicit class RichU64String(val self: String) extends AnyVal {
    def toU64Long: Long = java.lang.Long.parseUnsignedLong(self, 16)
  }

  private[this] val lookupTable: Array[Char] =
    (('0' to '9') ++ ('a' to 'f')).toArray

  private[this] def hex(l: Long): String = {
    val chs = new Array[Char](16)
    chs(0) = lookupTable((l >> 60 & 0xf).toInt)
    chs(1) = lookupTable((l >> 56 & 0xf).toInt)
    chs(2) = lookupTable((l >> 52 & 0xf).toInt)
    chs(3) = lookupTable((l >> 48 & 0xf).toInt)
    chs(4) = lookupTable((l >> 44 & 0xf).toInt)
    chs(5) = lookupTable((l >> 40 & 0xf).toInt)
    chs(6) = lookupTable((l >> 36 & 0xf).toInt)
    chs(7) = lookupTable((l >> 32 & 0xf).toInt)
    chs(8) = lookupTable((l >> 28 & 0xf).toInt)
    chs(9) = lookupTable((l >> 24 & 0xf).toInt)
    chs(10) = lookupTable((l >> 20 & 0xf).toInt)
    chs(11) = lookupTable((l >> 16 & 0xf).toInt)
    chs(12) = lookupTable((l >> 12 & 0xf).toInt)
    chs(13) = lookupTable((l >> 8 & 0xf).toInt)
    chs(14) = lookupTable((l >> 4 & 0xf).toInt)
    chs(15) = lookupTable((l & 0xf).toInt)
    new String(chs, 0, 16)
  }

  /**
   * Converts this unsigned 64-bit long value into a 16-character HEX string.
   */
  implicit class RichU64Long(val self: Long) extends AnyVal {

    def toU64HexString: String = hex(self)
  }

  /**
   * Forwarder for Int, as Scala 3.0 doesn't seem to do the implicit conversion to Long anymore.
   * This is not a bug, as Scala 2.13 already had a flag ("-Ywarn-implicit-widening") to turn on warnings/errors
   * for that.
   *
   * The implicit conversion from Int to Long here doesn't lose precision and keeps backwards source compatibliity
   * with previous releases.
   */
  implicit def richU64LongFromInt(self: Int): RichU64Long =
    new RichU64Long(self.toLong)

}
