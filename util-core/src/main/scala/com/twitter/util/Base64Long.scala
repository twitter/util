package com.twitter.util

/**
 * Efficient conversion between Longs and base 64 encoded strings.
 *
 * This is intended for use in e.g. cache keys.
 */
object Base64Long {

  /**
   * The bit width of a base-64 digit.
   */
  private[this] val DigitWidth = 6

  /**
   * The number of characters in the base 64 alphabet.
   */
  private[this] val AlphabetSize = 1 << DigitWidth

  /**
   * Mask for the least-significant digit.
   */
  private[this] val DigitMask = AlphabetSize - 1

  /**
   * The amount to shift right for the first base-64 digit in a Long.
   */
  private[this] val StartingBitPosition =
    AlphabetSize - (AlphabetSize % DigitWidth)

  /**
   * Convert an ordered sequence of 64 characters into a base 64 alphabet.
   */
  def alphabet(chars: Iterable[Char]): PartialFunction[Int, Char] = {
    require(
      chars.size == AlphabetSize,
      s"base-64 alphabet must have exactly $AlphabetSize characters"
    )
    val result = chars.toArray
    require(
      java.util.Arrays.equals(result.distinct, result),
      s"base-64 alphabet must not repeat characters"
    )
    result
  }

  val StandardBase64Alphabet: PartialFunction[Int, Char] =
    alphabet("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/")

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

  /**
   * Invert a base 64 alphabet, creating a function suitable for use in
   * `fromBase64`.
   */
  def invertAlphabet(forward: Int => Char): PartialFunction[Char, Int] = {
    val chars = 0.until(AlphabetSize).map(forward)

    if (chars.forall(_ <= 0xff)) {
      // If all of the characters fit in a byte, then pack the mapping
      // into an array indexed by the value of the char.
      new PartialFunction[Char, Int] {
        private[this] val maxChar = chars.max
        private[this] val reverse = Array.fill[Byte](maxChar.toInt + 1)(-1)
        0.until(AlphabetSize).foreach { i =>
          reverse(forward(i)) = i.toByte
        }
        def isDefinedAt(c: Char): Boolean = (c <= maxChar) && (reverse(c) != -1)
        def apply(c: Char): Int = reverse(c)
      }
    } else {
      0.until(AlphabetSize).map(i => forward(i) -> i).toMap
    }
  }

  private[this] val StandardBase64AlphabetInverted =
    invertAlphabet(StandardBase64Alphabet)

  /**
   * Decode a sequence of characters representing a base-64-encoded
   * Long, as produced by [[toBase64]].  An empty range of characters
   * will yield 0L. Leading "zero" digits will not cause overflow.
   *
   * @param alphabet defines the mapping between characters in the
   *   string and 6-bit numbers.
   *
   * @throws IllegalArgumentException if any characters in the specified
   *   range are not in the specified base-64 alphabet.
   *
   * @throws ArithmeticException if the resulting value overflows a Long.
   */
  def fromBase64(
    chars: CharSequence,
    start: Int,
    end: Int,
    alphabet: PartialFunction[Char, Int] = StandardBase64AlphabetInverted
  ): Long = {
    var i: Int = start
    var result: Long = 0
    while (i < end) {
      val c = chars.charAt(i)
      if (alphabet.isDefinedAt(c)) {
        val shifted = result * AlphabetSize
        if (shifted >>> DigitWidth != result) {
          throw new ArithmeticException("long overflow")
        }
        result = shifted | (alphabet(c) & DigitMask)
        i += 1
      } else {
        throw new IllegalArgumentException(s"Invalid base 64 character at $i: ${chars.charAt(i)}")
      }
    }
    result
  }

  /**
   * Decode a full CharSequence as a base-64-encoded Long, using the
   * standard base-64 alphabet.
   */
  def fromBase64(chars: CharSequence): Long =
    fromBase64(chars, 0, chars.length, StandardBase64AlphabetInverted)
}
