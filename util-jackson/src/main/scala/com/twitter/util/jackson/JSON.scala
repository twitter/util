package com.twitter.util.jackson

import com.twitter.util.Try

/**
 * Uses an instance of a [[ScalaObjectMapper]] configured to not perform any type of validation.
 * Inspired by [[https://www.scala-lang.org/api/2.12.6/scala-parser-combinators/scala/util/parsing/json/JSON$.html]].
 *
 * @note This is only intended for use from Scala (not Java).
 *
 * @see [[ScalaObjectMapper]]
 */
object JSON {
  private final val Mapper: ScalaObjectMapper =
    ScalaObjectMapper.builder.withNoValidation.objectMapper

  /** Simple utility to parse a JSON string into an Option[T] type. */
  def parse[T: Manifest](input: String): Option[T] =
    Try(Mapper.parse[T](input)).toOption

  /** Simple utility to write a value as a JSON encoded String. */
  def write(any: Any): String =
    Mapper.writeValueAsString(any)

  /** Simple utility to pretty print a JSON encoded String from the given instance. */
  def prettyPrint(any: Any): String =
    Mapper.writePrettyString(any)
}
