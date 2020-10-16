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
     * [[quoteC():String*]] and convert it into a standard unicode string.
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

    /**
     * Converts foo_bar to fooBar (first letter lowercased)
     *
     * For example:
     *
     * {{{
     *   "hello_world".toCamelCase
     * }}}
     *
     * will return the String `"helloWorld"`.
     */
    def toCamelCase: String = StringOps.toCamelCase(string)

    /**
     * Converts foo_bar to FooBar (first letter uppercased)
     *
     * For example:
     *
     * {{{
     *   "hello_world".toPascalCase
     * }}}
     *
     * will return the String `"HelloWorld"`.
     */
    def toPascalCase: String = StringOps.toPascalCase(string)

    /**
     * Converts FooBar to foo_bar (all lowercase)
     *
     * For example:
     *
     * {{{
     *   "HelloWorld".toSnakeCase
     * }}}
     *
     * will return the String `"hello_world"`.
     */
    def toSnakeCase: String = StringOps.toSnakeCase(string)
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

  private[this] val SnakeCaseRegexFirstPass = """([A-Z]+)([A-Z][a-z])""".r
  private[this] val SnakeCaseRegexSecondPass = """([a-z\d])([A-Z])""".r

  /**
   * Turn a string of format "FooBar" into snake case "foo_bar"
   *
   * @note toSnakeCase is not reversible, ie. in general the following will _not_ be true:
   *
   * {{{
   *    s == toCamelCase(toSnakeCase(s))
   * }}}
   *
   * Copied from the Lift Framework and RENAMED:
   * https://github.com/lift/framework/blob/master/core/util/src/main/scala/net/liftweb/util/StringHelpers.scala
   * Apache 2.0 License: https://github.com/lift/framework/blob/master/LICENSE.txt
   *
   * @return the underscored string
   */
  def toSnakeCase(name: String): String =
    SnakeCaseRegexSecondPass
      .replaceAllIn(
        SnakeCaseRegexFirstPass
          .replaceAllIn(name, "$1_$2"),
        "$1_$2"
      )
      .toLowerCase

  /**
   * Turns a string of format "foo_bar" into PascalCase "FooBar"
   *
   * @note Camel case may start with a capital letter (called "Pascal Case" or "Upper Camel Case") or,
   *       especially often in programming languages, with a lowercase letter. In this code's
   *       perspective, PascalCase means the first char is capitalized while camelCase means the first
   *       char is lowercased. In general both can be considered equivalent although by definition
   *       "CamelCase" is a valid camel-cased word. Hence, PascalCase can be considered to be a
   *       subset of camelCase.
   *
   * Copied from the "lift" framework and RENAMED:
   * https://github.com/lift/framework/blob/master/core/util/src/main/scala/net/liftweb/util/StringHelpers.scala
   * Apache 2.0 License: https://github.com/lift/framework/blob/master/LICENSE.txt
   * Functional code courtesy of Jamie Webb (j@jmawebb.cjb.net) 2006/11/28
   *
   * @param name the String to PascalCase
   * @return the PascalCased string
   */
  def toPascalCase(name: String): String = {
    def loop(x: List[Char]): List[Char] = (x: @unchecked) match {
      case '_' :: '_' :: rest => loop('_' :: rest)
      case '_' :: c :: rest => Character.toUpperCase(c) :: loop(rest)
      case '_' :: Nil => Nil
      case c :: rest => c :: loop(rest)
      case Nil => Nil
    }

    if (name == null) {
      ""
    } else {
      loop('_' :: name.toList).mkString
    }
  }

  /**
   * Turn a string of format "foo_bar" into camelCase with the first letter in lower case: "fooBar"
   *
   * This function is especially used to camelCase method names.
   *
   * @note Camel case may start with a capital letter (called "Pascal Case" or "Upper Camel Case") or,
   *       especially often in programming languages, with a lowercase letter. In this code's
   *       perspective, PascalCase means the first char is capitalized while camelCase means the first
   *       char is lowercased. In general both can be considered equivalent although by definition
   *       "CamelCase" is a valid camel-cased word. Hence, PascalCase can be considered to be a
   *       subset of camelCase.
   *
   * Copied from the Lift Framework and RENAMED:
   * https://github.com/lift/framework/blob/master/core/util/src/main/scala/net/liftweb/util/StringHelpers.scala
   * Apache 2.0 License: https://github.com/lift/framework/blob/master/LICENSE.txt
   *
   * @param name the String to camelCase
   * @return the camelCased string
   */
  def toCamelCase(name: String): String = {
    val tmp: String = toPascalCase(name)
    if (tmp.length == 0) {
      ""
    } else {
      tmp.substring(0, 1).toLowerCase + tmp.substring(1)
    }
  }

  implicit final class RichByteArray(val bytes: Array[Byte]) extends AnyVal {

    /**
     * Turn an `Array[Byte]` into a string of hex digits.
     */
    def hexlify: String = StringOps.hexlify(bytes, 0, bytes.length)
  }

}
