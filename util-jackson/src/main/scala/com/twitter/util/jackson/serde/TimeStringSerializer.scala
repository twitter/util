package com.twitter.util.jackson.serde

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ser.ContextualSerializer
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer
import com.fasterxml.jackson.databind.{BeanProperty, JsonSerializer, SerializerProvider}
import com.twitter.util.{Time, TimeFormat}
import java.util.{Locale, TimeZone}

object TimeStringSerializer {
  // TODO: this should be symmetric to the deserializer but we introduced this originally
  //  without contextual information and thus all strings were serialized with the default
  //  format of the object mapper ("yyyy-MM-dd HH:mm:ss Z"). This will require a migration.
  //  private[this] val DefaultTimeFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
  private[this] val DefaultTimeFormat = "yyyy-MM-dd HH:mm:ss Z"

  def apply(): TimeStringSerializer = this(DefaultTimeFormat)

  def apply(pattern: String): TimeStringSerializer =
    this(pattern, None, TimeZone.getTimeZone("UTC"))

  def apply(pattern: String, locale: Option[Locale], timezone: TimeZone): TimeStringSerializer =
    new TimeStringSerializer(new TimeFormat(pattern, locale, timezone))
}

/**
 * A Jackson [[JsonSerializer]] for [[com.twitter.util.Time]].
 * @param timeFormat the configured [[com.twitter.util.TimeFormat]] for this serializer.
 */
class TimeStringSerializer(private[this] val timeFormat: TimeFormat)
    extends StdScalarSerializer[Time](classOf[Time])
    with ContextualSerializer {

  override def serialize(value: Time, jgen: JsonGenerator, provider: SerializerProvider): Unit = {
    jgen.writeString(timeFormat.format(value))
  }

  /**
   * This method allows extracting the [[com.fasterxml.jackson.annotation.JsonFormat JsonFormat]]
   * annotation and create a [[com.twitter.util.TimeFormat TimeFormat]] based on the specifications
   * provided in the annotation. The implementation follows the Jackson's java8 & joda-time versions
   *
   * @param provider Serialization provider to access configuration, additional deserializers
   *                 that may be needed by this deserializer
   * @param property Method, field or constructor parameter that represents the property (and is
   *                 used to assign serialized value). Should be available; but there may be
   *                 cases where caller can not provide it and null is passed instead
   *                 (in which case impls usually pass 'this' serializer as is)
   *
   * @return Serializer to use for serializing values of specified property; may be this
   *         instance or a new instance.
   *
   * @see https://github.com/FasterXML/jackson-modules-java8/blob/master/datetime/src/main/java/com/fasterxml/jackson/datatype/jsr310/ser/JSR310FormattedSerializerBase.java#L114
   */
  override def createContextual(
    provider: SerializerProvider,
    property: BeanProperty
  ): JsonSerializer[_] = {
    val serializerOption: Option[TimeStringSerializer] = for {
      jsonFormat <- Option(findFormatOverrides(provider, property, handledType()))
      serializer <- Option(
        TimeStringSerializer(
          jsonFormat.getPattern,
          Option(jsonFormat.getLocale),
          Option(jsonFormat.getTimeZone).getOrElse(TimeZone.getTimeZone("UTC"))
        )) if jsonFormat.hasPattern
    } yield {
      serializer
    }
    serializerOption.getOrElse(this)
  }

}
