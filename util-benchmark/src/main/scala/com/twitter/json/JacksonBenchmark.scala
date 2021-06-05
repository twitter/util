package com.twitter.json

import com.fasterxml.jackson.module.scala.{ScalaObjectMapper => JacksonScalaObjectMapper}
import com.twitter.util.StdBenchAnnotations
import com.twitter.util.jackson.ScalaObjectMapper
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import org.openjdk.jmh.annotations._

object JacksonBenchmark {
  case class TestTask(request_id: String, group_ids: Seq[String], params: TestTaskParams)

  case class TestTaskParams(
    results: TaskTaskResults,
    start_time: LocalDateTime,
    end_time: LocalDateTime,
    priority: String)

  case class TaskTaskResults(
    country_codes: Seq[String],
    group_by: Seq[String],
    format: Option[TestFormat],
    demographics: Seq[TestDemographic])

}

// ./sbt 'project util-benchmark' 'jmh:run JacksonBenchmark'
@State(Scope.Benchmark)
class JacksonBenchmark extends StdBenchAnnotations {
  import JacksonBenchmark._

  private[this] val json =
    """{
        "request_id": "00000000-1111-2222-3333-444444444444",
        "type": "impression",
        "params": {
          "priority": "normal",
          "start_time": "2013-01-01T00:02:00.000Z",
          "end_time": "2013-01-01T00:03:00.000Z",
          "results": {
            "demographics": ["age", "gender"],
            "country_codes": ["US"],
            "group_by": ["group"]
          }
        },
        "group_ids":["grp-1"]
      }"""

  private[this] val bytes = json.getBytes("UTF-8")

  /** create framework scala object mapper with all defaults */
  private[this] val mapperWithCaseClassDeserializer: ScalaObjectMapper =
    ScalaObjectMapper.builder.objectMapper

  /** create framework scala object mapper without case class deserializer */
  private[this] val mapperWithoutCaseClassDeserializer: ScalaObjectMapper = {
    // configure the underlying Jackson ScalaObjectMapper with everything but the CaseClassJacksonModule
    val underlying = new com.fasterxml.jackson.databind.ObjectMapper with JacksonScalaObjectMapper
    underlying.registerModules(ScalaObjectMapper.DefaultJacksonModules: _*)
    // do not use apply which will always install the default jackson modules on the underlying mapper
    new ScalaObjectMapper(underlying)
  }

  @Benchmark
  def withCaseClassDeserializer(): TestTask = {
    val is = new ByteArrayInputStream(bytes)
    mapperWithCaseClassDeserializer.parse[TestTask](is)
  }

  @Benchmark
  def withoutCaseClassDeserializer(): TestTask = {
    val is = new ByteArrayInputStream(bytes)
    mapperWithoutCaseClassDeserializer.parse[TestTask](is)
  }
}
