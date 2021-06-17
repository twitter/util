package com.twitter.util.jackson

import com.twitter.io.{Buf, ClasspathResource}
import com.twitter.util.Try
import java.io.{File, InputStream}

/**
 * Uses an instance of a [[ScalaObjectMapper]] configured to not perform any type of validation.
 * Inspired by [[https://www.scala-lang.org/api/2.12.6/scala-parser-combinators/scala/util/parsing/json/JSON$.html]].
 *
 * @note This is only intended for use from Scala (not Java).
 *
 * @see [[ScalaObjectMapper]]
 */
object YAML {
  private final val Mapper: ScalaObjectMapper =
    ScalaObjectMapper.builder.withNoValidation.yamlObjectMapper

  /** Simple utility to parse a YAML string into an Option[T] type. */
  def parse[T: Manifest](input: String): Option[T] =
    Try(Mapper.parse[T](input)).toOption

  /** Simple utility to parse a YAML [[Buf]] into an Option[T] type. */
  def parse[T: Manifest](input: Buf): Option[T] =
    Try(Mapper.parse[T](input)).toOption

  /**
   * Simple utility to parse a YAML [[InputStream]] into an Option[T] type.
   * @note the caller is responsible for managing the lifecycle of the given [[InputStream]].
   */
  def parse[T: Manifest](input: InputStream): Option[T] =
    Try(Mapper.parse[T](input)).toOption

  /**
   * Simple utility to load a YAML file and parse contents into an Option[T] type.
   *
   * @note the caller is responsible for managing the lifecycle of the given [[File]].
   */
  def parse[T: Manifest](f: File): Option[T] =
    Try(Mapper.underlying.readValue[T](f)).toOption

  /** Simple utility to write a value as a YAML encoded String. */
  def write(any: Any): String =
    Mapper.writeValueAsString(any)

  object Resource {

    /**
     * Simple utility to load a YAML resource from the classpath and parse contents into an Option[T] type.
     *
     * @note `name` resolution to locate the resource is governed by [[java.lang.Class#getResourceAsStream]]
     */
    def parse[T: Manifest](name: String): Option[T] = {
      ClasspathResource.load(name) match {
        case Some(inputStream) =>
          try {
            YAML.parse[T](inputStream)
          } finally {
            inputStream.close()
          }
        case _ => None
      }
    }
  }
}
