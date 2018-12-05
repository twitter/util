package com.twitter.conversions

import scala.util.matching.Regex

object StringOps {

  // we intentionally don't unquote "\$" here, so it can be used to escape interpolation later.
  private val UNQUOTE_RE = """\\(u[\dA-Fa-f]{4}|x[\dA-Fa-f]{2}|[/rnt\"\\])""".r

  private val QUOTE_RE = "[\u0000-\u001f\u007f-\uffff\\\\\"]".r

  implicit final class RichString(val string: String) extends AnyVal {

    /**
     * For every section of a string that matches a regular expression, call
     * a function to determine a replacement (as in python's
     * `re.sub`). The function will be passed the Matcher object
     * corresponding to the substring that matches the pattern, and that
     * substring will be replaced by the function's result.
     *
     * For example, this call:
     * {{{
     * "ohio".regexSub("""h.""".r) { m => "n" }
     * }}}
     * will return the string `"ono"`.
     *
     * The matches are found using `Matcher.find()` and so
     * will obey all the normal java rules (the matches will not overlap,
     * etc).
     *
     * @param re the regex pattern to replace
     * @param replace a function that takes Regex.MatchData objects and
     *     returns a string to substitute
     * @return the resulting string with replacements made
     */
    def regexSub(re: Regex)(replace: Regex.MatchData => String): String = {
      var offset = 0
      val out = new StringBuilder()

      for (m <- re.findAllIn(string).matchData) {
        if (m.start > offset) {
          out.append(string.substring(offset, m.start))
        }

        out.append(replace(m))
        offset = m.end
      }

      if (offset < string.length) {
        out.append(string.substring(offset))
      }
      out.toString
    }

    /**
     * Quote a string so that unprintable chars (in ASCII) are represented by
     * C-style backslash expressions. For example, a raw linefeed will be
     * translated into `"\n"`. Control codes (anything below 0x20)
     * and unprintables (anything above 0x7E) are turned into either
     * `"\xHH"` or `"\\uHHHH"` expressions, depending on
     * their range. Embedded backslashes and double-quotes are also quoted.
     *
     * @return a quoted string, suitable for ASCII display
     */
    def quoteC(): String = {
      regexSub(QUOTE_RE) { m =>
        m.matched.charAt(0) match {
          case '\r' => "\\r"
          case '\n' => "\\n"
          case '\t' => "\\t"
          case '"' => "\\\""
          case '\\' => "\\\\"
          case c =>
            if (c <= 255) {
              "\\x%02x".format(c.asInstanceOf[Int])
            } else {
              "\\u%04x" format c.asInstanceOf[Int]
            }
        }
      }
    }

    /**
     * Unquote an ASCII string that has been quoted in a style like
     * [[quoteC()]] and convert it into a standard unicode string.
     * `"\\uHHHH"` and `"\xHH"` expressions are unpacked
     * into unicode characters, as well as `"\r"`, `"\n"`,
     * `"\t"`, `"\\"`, and `'\"'`.
     *
     * @return an unquoted unicode string
     */
    def unquoteC(): String = {
      regexSub(UNQUOTE_RE) { m =>
        val ch = m.group(1).charAt(0) match {
          // holy crap! this is terrible:
          case 'u' =>
            Character.valueOf(Integer.valueOf(m.group(1).substring(1), 16).asInstanceOf[Int].toChar)
          case 'x' =>
            Character.valueOf(Integer.valueOf(m.group(1).substring(1), 16).asInstanceOf[Int].toChar)
          case 'r' => '\r'
          case 'n' => '\n'
          case 't' => '\t'
          case x => x
        }
        ch.toString
      }
    }

    /**
     * Turn a string of hex digits into a byte array. This does the exact
     * opposite of `Array[Byte]#hexlify`.
     */
    def unhexlify(): Array[Byte] = {
      val buffer = new Array[Byte]((string.length + 1) / 2)
      string.grouped(2).toSeq.zipWithIndex.foreach {
        case (substr, i) =>
          buffer(i) = Integer.parseInt(substr, 16).toByte
      }
      buffer
    }
  }

  def hexlify(array: Array[Byte], from: Int, to: Int): String = {
    val out = new StringBuffer
    for (i <- from until to) {
      val b = array(i)
      val s = (b.toInt & 0xff).toHexString
      if (s.length < 2) {
        out.append('0')
      }
      out.append(s)
    }
    out.toString
  }

  implicit final class RichByteArray(val bytes: Array[Byte]) extends AnyVal {

    /**
     * Turn an `Array[Byte]` into a string of hex digits.
     */
    def hexlify: String = StringOps.hexlify(bytes, 0, bytes.length)
  }

}
