package com.twitter.util.logging

import org.slf4j

/**
 * For Java usability.
 *
 * @note Scala users see [[com.twitter.util.logging.Logger]]
 * @deprecated Java users can use corresponding methods in [[com.twitter.util.logging.Logger]]
 */
@deprecated("Use com.twitter.util.logging.Logger", "2020-09-30")
object Loggers {

  /**
   * Create a [[Logger]] for the given name.
   *
   * @param name name of the underlying `slf4j.Logger`.
   *
   * {{{
   *    Logger logger = Logger.getLogger("name");
   * }}}
   */
  @deprecated("Use com.twitter.util.logging.Logger", "2020-09-30")
  def getLogger(name: String): Logger =
    Logger(name)

  /**
   * Create a [[Logger]] named for the given class.
   *
   * @param clazz class to use for naming the underlying slf4j.Logger.
   *
   * {{{
   *    Logger logger = Logger.getLogger(classOf[MyClass]);
   * }}}
   */
  @deprecated("Use com.twitter.util.logging.Logger", "2020-09-30")
  def getLogger(clazz: Class[_]): Logger =
    Logger(clazz)

  /**
   * Create a [[Logger]] wrapping the given underlying
   * `org.slf4j.Logger`.
   *
   * @param underlying an `org.slf4j.Logger`
   *
   * {{{
   *    Logger logger = Logger.getLogger(LoggerFactory.getLogger("name"));
   * }}}
   */
  @deprecated("Use com.twitter.util.logging.Logger", "2020-09-30")
  def getLogger(underlying: slf4j.Logger): Logger =
    Logger(underlying)
}
