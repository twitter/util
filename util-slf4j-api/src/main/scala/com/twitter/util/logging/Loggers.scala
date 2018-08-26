package com.twitter.util.logging

import org.slf4j

/**
 * For Java usability.
 *
 * @note Scala users see [[com.twitter.util.logging.Logger]]
 */
object Loggers {

  /**
   * Create a [[Logger]] for the given name.
   *
   * @param name name of the underlying `slf4j.Logger`.
   *
   * {{{
   *    val logger = Logger("name")
   * }}}
   */
  def getLogger(name: String): Logger =
    Logger(name)

  /**
   * Create a [[Logger]] named for the given class.
   *
   * @param clazz class to use for naming the underlying slf4j.Logger.
   *
   * {{{
   *    val logger = Logger(classOf[MyClass])
   * }}}
   */
  def getLogger(clazz: Class[_]): Logger =
    Logger(clazz)

  /**
   * Create a [[Logger]] wrapping the given underlying
   * [[org.slf4j.Logger]].
   *
   * @param underlying an `org.slf4j.Logger`
   *
   * {{{
   *    val logger = Logger(LoggerFactory.getLogger("name"))
   * }}}
   */
  def getLogger(underlying: slf4j.Logger): Logger =
    Logger(underlying)
}
