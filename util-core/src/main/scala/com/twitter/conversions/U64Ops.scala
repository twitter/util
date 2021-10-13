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

  /**
   * Converts this unsigned 64-bit long value into a 16-character HEX string.
   */
  implicit class RichU64Long(val self: Long) extends AnyVal {
    def toU64HexString: String = "%016x".format(self)
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
