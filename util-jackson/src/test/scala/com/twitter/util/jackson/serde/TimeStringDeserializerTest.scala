package com.twitter.util.jackson.serde

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.util.jackson.ScalaObjectMapper
import com.twitter.util.jackson.caseclass.exceptions.CaseClassMappingException
import com.twitter.util.{Time, TimeFormat}
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

private object TimeStringDeserializerTest {
  case class WithoutJsonFormat(time: Time)
  case class WithJsonFormat(@JsonFormat(pattern = "yyyy-MM-dd") time: Time)
  case class WithJsonFormatAndTimezone(
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "America/Los_Angeles") time: Time)
  case class WithEpochJsonFormat(
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "s") time: Time)
}

@RunWith(classOf[JUnitRunner])
class TimeStringDeserializerTest extends AnyFunSuite with BeforeAndAfterAll with Matchers {
  import TimeStringDeserializerTest._

  private[this] final val Input1 =
    """
      |{
      |  "time": "2019-06-17T15:45:00.000+0000"
      |}
    """.stripMargin

  private[this] final val Input2 =
    """
      |{
      |  "time": "2019-06-17"
      |}
    """.stripMargin

  private[this] final val Input3 =
    """
      |{
      |  "time": "2019-06-17 16:30:00"
      |}
    """.stripMargin

  private[this] final val EpochInput =
    """
      |{
      |  "time": 1560786300
      |}
    """.stripMargin

  private[this] val jacksonObjectMapper = new ObjectMapper()
  private[this] val objectMapper = ScalaObjectMapper()

  override def beforeAll(): Unit = {
    val modules = Seq(DefaultScalaModule, DefaultSerdeModule)
    modules.foreach(jacksonObjectMapper.registerModule)
  }

  test("should deserialize date without JsonFormat") {
    val expected = 1560786300000L
    val jacksonActual: WithoutJsonFormat =
      jacksonObjectMapper.readValue(Input1, classOf[WithoutJsonFormat])
    jacksonActual.time.inMillis shouldEqual expected

    val frameworkActual = objectMapper.parse[WithoutJsonFormat](Input1)
    frameworkActual.time.inMillis shouldEqual expected
  }

  test("should deserialize date with JsonFormat") {
    val expected: Time = new TimeFormat("yyyy-MM-dd").parse("2019-06-17")
    val jacksonActual: WithJsonFormat =
      jacksonObjectMapper.readValue(Input2, classOf[WithJsonFormat])
    jacksonActual.time shouldEqual expected

    val frameworkActual = objectMapper.parse[WithJsonFormat](Input2)
    frameworkActual.time shouldEqual expected
  }

  test("should deserialize date with JsonFormat and timezone") {
    val expected = 1560814200000L
    val jacksonActual: WithJsonFormatAndTimezone =
      jacksonObjectMapper.readValue(Input3, classOf[WithJsonFormatAndTimezone])
    jacksonActual.time.inMillis shouldEqual expected

    val frameworkActual = objectMapper.parse[WithJsonFormatAndTimezone](Input3)
    frameworkActual.time.inMillis shouldEqual expected
  }

  test("should return a MappingException for empty value") {
    val jacksonActual: WithoutJsonFormat =
      jacksonObjectMapper.readValue("{}", classOf[WithoutJsonFormat])
    jacksonActual should equal(WithoutJsonFormat(null)) // underlying mapper allows value to be null

    intercept[CaseClassMappingException] {
      objectMapper.parse[WithoutJsonFormat]("{}")
    }
  }

  test("should support JsonFormat with epoch timestamp") {
    val expected = 1560786300000L
    val jacksonActual: WithEpochJsonFormat =
      jacksonObjectMapper.readValue(EpochInput, classOf[WithEpochJsonFormat])
    jacksonActual.time.inMillis shouldEqual expected

    val frameworkActual = objectMapper.parse[WithEpochJsonFormat](EpochInput)
    frameworkActual.time.inMillis shouldEqual expected
  }
}
