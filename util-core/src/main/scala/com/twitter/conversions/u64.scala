package com.twitter.conversions

object u64 {

  /**
   * Parses this HEX string as an unsigned 64-bit long value. Be careful, this can throw
   * [[NumberFormatException]].
   *
   * @see [[java.lang.Long.parseUnsignedLong()]]
   */
  implicit class StringOps(val self: String) extends AnyVal {
    def toU64Long: Long = java.lang.Long.parseUnsignedLong(self, 16)
  }

  /**
   * Converts this unsigned 64-bit long value into a 16-character HEX string.
   */
  implicit class LongOps(val self: Long) extends AnyVal {
    def toU64HexString: String = "%016x".format(self)
  }
}
