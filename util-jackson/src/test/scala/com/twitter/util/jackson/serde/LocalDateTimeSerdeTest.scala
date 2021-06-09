package com.twitter.util.jackson.serde

import com.fasterxml.jackson.databind.{SerializationFeature, ObjectMapper => JacksonObjectMapper}
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.{
  DefaultScalaModule,
  ScalaObjectMapper => JacksonScalaObjectMapper
}
import com.twitter.util.jackson.ScalaObjectMapper
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

case class MyClass(dateTime: LocalDateTime)

private object LocalDateTimeSerdeTest {
  val UTCZoneId: ZoneId = ZoneId.of("UTC")
  val ISO8601DateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ISO_OFFSET_DATE_TIME // "2014-05-30T03:57:59.302Z"

  // 1st one for the LocalDate, the 2nd one for LocalDateTime
  // @JsonFormat(pattern = "yyyy-MM-dd")
  // @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
}

@RunWith(classOf[JUnitRunner])
class LocalDateTimeSerdeTest extends AnyFunSuite with Matchers {
  import LocalDateTimeSerdeTest._

  private final val NowUtc: LocalDateTime = LocalDateTime.now(UTCZoneId)
  private[this] val myClass1 = MyClass(dateTime = NowUtc)

  // Most {@code java.time} types are serialized as numbers (integers or decimals as appropriate) if the
  // {@link com.fasterxml.jackson.databind.SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} feature is enabled, and otherwise are serialized in
  // standard <a href="http://en.wikipedia.org/wiki/ISO_8601" target="_blank">ISO-8601</a> string representation. ISO-8601 specifies formats
  // for representing offset dates and times, zoned dates and times, local dates and times, periods, durations, zones, and more. All
  // {@code java.time} types have built-in translation to and from ISO-8601 formats.

  // Granularity of timestamps is controlled through the companion features
  // {@link com.fasterxml.jackson.databind.SerializationFeature#WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS} and
  // {@link com.fasterxml.jackson.databind.DeserializationFeature#READ_DATE_TIMESTAMPS_AS_NANOSECONDS}. For serialization, timestamps are
  // written as fractional numbers (decimals), where the number is seconds and the decimal is fractional seconds, if
  // {@code WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS} is enabled (it is by default), with resolution as fine as nanoseconds depending on the
  // underlying JDK implementation. If {@code WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS} is disabled, timestamps are written as a whole number of
  // milliseconds. At deserialization time, decimal numbers are always read as fractional second timestamps with up-to-nanosecond resolution,
  // since the meaning of the decimal is unambiguous. The more ambiguous integer types are read as fractional seconds without a decimal point
  // if {@code READ_DATE_TIMESTAMPS_AS_NANOSECONDS} is enabled (it is by default), and otherwise they are read as milliseconds.

  // {@link LocalDate}, {@link LocalTime}, {@link LocalDateTime}, and {@link OffsetTime}, which cannot portably be converted to timestamps
  // and are instead represented as arrays when WRITE_DATES_AS_TIMESTAMPS is enabled.
  // https://github.com/FasterXML/jackson-datatype-jsr310/blob/master/src/main/java/com/fasterxml/jackson/datatype/jsr310/JavaTimeModule.java
  test("serialize as array") {
    val mapper = new JacksonObjectMapper()
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
    mapper.registerModule(new JavaTimeModule)
    val serialized = mapper.writeValueAsString(NowUtc) // [2021,5,10,0,0,0,0]
    // remove beginning [ and ending ] from the string
    val serializedParts = serialized.substring(1, serialized.length - 1).split(",")
    serializedParts.size should equal(7)
    serializedParts(0) should equal(NowUtc.getYear.toString)
    serializedParts(1) should equal(NowUtc.getMonthValue.toString)
    serializedParts(2) should equal(NowUtc.getDayOfMonth.toString)
    serializedParts(3) should equal(NowUtc.getHour.toString)
    serializedParts(4) should equal(NowUtc.getMinute.toString)
    serializedParts(5) should equal(NowUtc.getSecond.toString)
    serializedParts(6) should equal(NowUtc.getNano.toString)
  }

  test("serialize as text") {
    val mapper = new JacksonObjectMapper()
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    mapper.registerModule(new JavaTimeModule)
    val actualStr = mapper.writeValueAsString(NowUtc)
    val actualStrUnquoted =
      actualStr.substring(1, actualStr.length - 1) // drop beginning and end quotes
    val actual =
      actualStrUnquoted.substring(
        0,
        actualStrUnquoted.lastIndexOf('.')
      ) // // drop fractions of second
    val expectStr = NowUtc.toString
    val expected = expectStr.substring(0, expectStr.lastIndexOf('.')) // drop fractions of second
    expected should be(actual)
  }

  test("deserialize from text") {
    val mapper = new JacksonObjectMapper with JacksonScalaObjectMapper
    mapper.registerModule(new JavaTimeModule)
    mapper.registerModule(DefaultSerdeModule)
    mapper.readValue[LocalDateTime](quote(NowUtc.toString)) should equal(NowUtc)
  }

  test("deserialize from text with ScalaObjectMapper with SerdeModule") {
    val mapper = ScalaObjectMapper()
    mapper.parse[LocalDateTime](quote(NowUtc.toString)) should equal(NowUtc)
  }

  test("roundtrip text") {
    val mapper = new JacksonObjectMapper with JacksonScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(new JavaTimeModule)
    val json = mapper.writeValueAsString(myClass1)
    val deserializedC1 = mapper.readValue[MyClass](json)
    deserializedC1 should equal(myClass1)
  }

  test("roundtrip text with ScalaObjectMapper with SerdeModule") {
    val mapper = ScalaObjectMapper()
    val json = mapper.writeValueAsString(myClass1)
    val deserializedC1 = mapper.parse[MyClass](json)
    deserializedC1 should equal(myClass1)
  }

  test("roundtrip text with ScalaObjectMapper") {
    val mapper = ScalaObjectMapper()
    val json = mapper.writeValueAsString(myClass1)
    val deserializedC1 = mapper.parse[MyClass](json)
    deserializedC1 should equal(myClass1)
  }

  private[this] def quote(str: String): String = s"""\"$str\""""
}
