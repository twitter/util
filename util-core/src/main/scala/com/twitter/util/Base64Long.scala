package com.twitter.util

/**
 * Efficient conversion of Longs to base 64 encoded strings.
 *
 * This is intended for use in e.g. cache keys.
 */
object Base64Long {
  val StandardBase64Alphabet: Int => Char = Array[Char](
    'A','B','C','D','E','F','G','H',
    'I','J','K','L','M','N','O','P',
    'Q','R','S','T','U','V','W','X',
    'Y','Z','a','b','c','d','e','f',
    'g','h','i','j','k','l','m','n',
    'o','p','q','r','s','t','u','v',
    'w','x','y','z','0','1','2','3',
    '4','5','6','7','8','9','+','/'
  )

  /**
   * The bit width of a base-64 digit.
   */
  private[this] val DigitWidth = 6

  /**
   * Mask for the least-significant digit.
   */
  private[this] val DigitMask = (1 << DigitWidth) - 1

  /**
   * The amount to shift right for the first base-64 digit in a Long.
   */
  private[this] val StartingBitPosition = 60

  /**
   * Enable re-use of the StringBuilder for toBase64(Long): String
   */
  private[this] val threadLocalBuilder = new ThreadLocal[StringBuilder] {
    override def initialValue = new StringBuilder
  }

  /**
   * Convert this Long to a base 64 String, using the standard base 64 alphabet.
   */
  def toBase64(l: Long): String = {
    val b = threadLocalBuilder.get()
    b.clear()
    toBase64(b, l)
    b.toString()
  }

  /**
   * Append a base-64 encoded Long to a StringBuilder.
   *
   * The Base64 encoding uses the standard Base64 alphabet (with '+' and '/'). It does not pad the
   * result. The representation is just like base 10 or base 16, where leading zero digits are
   * omitted.
   *
   * The number is treated as unsigned, so there is never a leading negative sign, and the
   * representations of negative numbers are larger than positive numbers.
   */
  def toBase64(builder: StringBuilder, l: Long, alphabet: Int => Char = StandardBase64Alphabet) {
    if (l == 0) {
      // Special case for zero: Just like in decimal, if the number is
      // zero, represent it as a single zero digit.
      builder.append(alphabet(0))
    } else {
      var bitPosition = StartingBitPosition
      while ((l >>> bitPosition) == 0) {
        bitPosition -= DigitWidth
      }
      // Copy in the 6-bit segments, one at a time.
      while (bitPosition >= 0) {
        val shifted = l >>> bitPosition
        // The offset into the digit array is the 6 bits that are b
        // bits from the right of the number we're encoding.
        val digitValue = (shifted & DigitMask).toInt
        builder.append(alphabet(digitValue))
        bitPosition -= DigitWidth
      }
    }
  }
}
