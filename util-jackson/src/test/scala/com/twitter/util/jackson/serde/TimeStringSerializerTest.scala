package com.twitter.util.jackson.serde

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.util.jackson.ScalaObjectMapper
import com.twitter.util.{Time, TimeFormat}
import java.util.TimeZone
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

private object TimeStringSerializerTest {
  case class WithoutJsonFormat(time: Time)
  case class WithJsonFormat(@JsonFormat(pattern = "yyyy-MM-dd") time: Time)
  // NOTE: JsonFormat.Shape is not respected, times are always written as Strings
  case class WithInvalidJsonFormat(
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "yyyy-MM-dd") time: Time)
  case class WithJsonFormatAndTimezone(
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "America/Los_Angeles") time: Time)
  // Note for serialization the pattern 's' represents the "second in minute": see: https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html
  // s	Second in minute	Number	55
  case class WithEpochJsonFormat(
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "s") time: Time)
  // S	Millisecond	Number	978
  case class WithJsonFormatNumberShape(
    @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT, pattern = "S") time: Time)
  case class WithJsonFormatNaturalShape(
    @JsonFormat(shape = JsonFormat.Shape.NATURAL, pattern = "S") time: Time)
  case class WithJsonFormatNoShape(
    @JsonFormat(shape = JsonFormat.Shape.ANY, pattern = "S") time: Time)
  case class JsonFormatNumberShape(
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "s.SS") time: Time)
  case class JsonFormatNumberShape2(
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "yyyy.MM.dd") time: Time)
  case class JsonFormatNumberShape3(
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "dd") time: Time)
  case class JsonFormatNumberShape4(
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "EEE") time: Time)
  case class JsonFormatNumberShape5(
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "yyyy,MM,dd") time: Time)

  val DefaultTimeFormat: TimeFormat = new TimeFormat("s")
  val DefaultTime: Time = DefaultTimeFormat.parse("1560786300") // "2019-06-17 15:45:00 +0000"
}

@RunWith(classOf[JUnitRunner])
class TimeStringSerializerTest extends AnyFunSuite with BeforeAndAfterAll with Matchers {
  import TimeStringSerializerTest._

  private[this] val jacksonObjectMapper = new ObjectMapper()
  private[this] val objectMapper = ScalaObjectMapper()

  override def beforeAll(): Unit = {
    val modules = Seq(DefaultScalaModule, DefaultSerdeModule)
    modules.foreach(jacksonObjectMapper.registerModule)
  }

  test("should serialize date without JsonFormat") {
    val expected: String =
      """{"time":"2019-06-17 15:45:00 +0000"}"""

    val value = WithoutJsonFormat(DefaultTime)
    jacksonObjectMapper.writeValueAsString(value) shouldEqual expected
    objectMapper.writeValueAsString(value) shouldEqual expected
  }

  test("should serialize as String with invalid JsonFormat") {
    val expected: String =
      """{"time":"2019-06-17"}"""

    val value = WithInvalidJsonFormat(DefaultTime)
    jacksonObjectMapper.writeValueAsString(value) shouldEqual expected
    objectMapper.writeValueAsString(value) shouldEqual expected
  }

  test("should serialize date with JsonFormat and timezone") {
    val expected = """{"time":"2019-06-17 23:30:00"}"""

    val time =
      new TimeFormat(
        pattern = "s",
        locale = None,
        timezone = TimeZone.getTimeZone("America/Los_Angeles")).parse("1560814200")
    val value = WithJsonFormatAndTimezone(time)
    jacksonObjectMapper.writeValueAsString(value) shouldEqual expected
    objectMapper.writeValueAsString(value) shouldEqual expected
  }

  test("should serialize date with JsonFormat") {
    val value0 = WithJsonFormat(DefaultTime)
    jacksonObjectMapper.writeValueAsString(value0) shouldEqual """{"time":"2019-06-17"}"""
    objectMapper.writeValueAsString(value0) shouldEqual """{"time":"2019-06-17"}"""

    // Note the @JsonFormat only ends up formatting the
    // "second in minute", see: https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html
    // which in this example is 0.
    val value = WithEpochJsonFormat(DefaultTime)
    jacksonObjectMapper.writeValueAsString(value) shouldEqual """{"time":"0"}"""
    objectMapper.writeValueAsString(value) shouldEqual """{"time":"0"}"""

    // Note the @JsonFormat only ends up formatting the
    // "millisecond", see: https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html
    // which in this example is 0.
    val value1 = WithJsonFormatNumberShape(DefaultTime)
    jacksonObjectMapper.writeValueAsString(value1) shouldEqual """{"time":"0"}"""
    objectMapper.writeValueAsString(value1) shouldEqual """{"time":"0"}"""

    val value2 = WithJsonFormatNaturalShape(DefaultTime)
    jacksonObjectMapper.writeValueAsString(value2) shouldEqual """{"time":"0"}"""
    objectMapper.writeValueAsString(value2) shouldEqual """{"time":"0"}"""

    val value3 = WithJsonFormatNoShape(DefaultTime)
    jacksonObjectMapper.writeValueAsString(value3) shouldEqual """{"time":"0"}"""
    objectMapper.writeValueAsString(value3) shouldEqual """{"time":"0"}"""

    val value4 = JsonFormatNumberShape(DefaultTime)
    jacksonObjectMapper.writeValueAsString(value4) shouldEqual """{"time":"0.00"}"""
    objectMapper.writeValueAsString(value4) shouldEqual """{"time":"0.00"}"""

    val value5 = JsonFormatNumberShape2(DefaultTime)
    jacksonObjectMapper.writeValueAsString(value5) shouldEqual """{"time":"2019.06.17"}"""
    objectMapper.writeValueAsString(value5) shouldEqual """{"time":"2019.06.17"}"""

    val value6 = JsonFormatNumberShape3(DefaultTime)
    jacksonObjectMapper.writeValueAsString(value6) shouldEqual """{"time":"17"}"""
    objectMapper.writeValueAsString(value6) shouldEqual """{"time":"17"}"""

    val value7 = JsonFormatNumberShape4(DefaultTime)
    jacksonObjectMapper.writeValueAsString(value7) shouldEqual """{"time":"Mon"}"""
    objectMapper.writeValueAsString(value7) shouldEqual """{"time":"Mon"}"""

    val value8 = JsonFormatNumberShape5(DefaultTime)
    jacksonObjectMapper.writeValueAsString(value8) shouldEqual """{"time":"2019,06,17"}"""
    objectMapper.writeValueAsString(value8) shouldEqual """{"time":"2019,06,17"}"""
  }
}
