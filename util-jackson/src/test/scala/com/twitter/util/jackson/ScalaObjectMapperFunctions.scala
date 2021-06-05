package com.twitter.util.jackson

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.{
  JsonNode,
  ObjectMapperCopier,
  ObjectMapper => JacksonObjectMapper
}
import com.twitter.util.jackson.caseclass.exceptions.{
  CaseClassFieldMappingException,
  CaseClassMappingException
}
import com.twitter.util.logging.Logging
import java.lang.reflect.{ParameterizedType, Type}
import org.scalatest.matchers.should.Matchers

trait ScalaObjectMapperFunctions extends Matchers with Logging {
  /* Abstract */
  protected def mapper: ScalaObjectMapper

  protected def parse[T: Manifest](string: String): T =
    mapper.parse[T](string)

  protected def parse[T: Manifest](jsonNode: JsonNode): T =
    mapper.parse[T](jsonNode)

  protected def parse[T: Manifest](obj: T): T =
    mapper.parse[T](mapper.writeValueAsBytes(obj))

  protected def generate(any: Any): String =
    mapper.writeValueAsString(any)

  protected def assertJson[T: Manifest](obj: T, expected: String): Unit = {
    val json = generate(obj)
    JsonDiff.assertDiff(expected, json)
    parse[T](json) should equal(obj)
  }

  protected def copy(underlying: JacksonScalaObjectMapperType): JacksonScalaObjectMapperType =
    ObjectMapperCopier.copy(underlying)

  protected def typeFromManifest(m: Manifest[_]): Type =
    if (m.typeArguments.isEmpty) {
      m.runtimeClass
    } else {
      new ParameterizedType {
        override def getRawType: Class[_] = m.runtimeClass
        override def getActualTypeArguments: Array[Type] =
          m.typeArguments.map(typeFromManifest).toArray
        override def getOwnerType: Null = null
      }
    }

  protected[this] def typeReference[T: Manifest]: TypeReference[T] = new TypeReference[T] {
    override def getType: Type = typeFromManifest(manifest[T])
  }

  protected def deserialize[T: Manifest](mapper: JacksonObjectMapper, value: String): T =
    mapper.readValue(value, typeReference[T])

  protected def clearStackTrace(
    exceptions: Seq[CaseClassFieldMappingException]
  ): Seq[CaseClassFieldMappingException] = {
    exceptions.foreach(_.setStackTrace(Array()))
    exceptions
  }

  protected def assertJsonParse[T: Manifest](json: String, withErrors: Seq[String]): Unit = {
    if (withErrors.nonEmpty) {
      val e1 = intercept[CaseClassMappingException] {
        val parsed = parse[T](json)
        println("Incorrectly parsed: " + ScalaObjectMapper().writePrettyString(parsed))
      }
      assertObjectParseException(e1, withErrors)

      // also check that we can parse into an intermediate JsonNode
      val e2 = intercept[CaseClassMappingException] {
        val jsonNode = parse[JsonNode](json)
        parse[T](jsonNode)
      }
      assertObjectParseException(e2, withErrors)
    } else parse[T](json)
  }

  private def assertObjectParseException(
    e: CaseClassMappingException,
    withErrors: Seq[String]
  ): Unit = {
    trace(e.errors.mkString("\n"))
    clearStackTrace(e.errors)

    val actualMessages = e.errors.map(_.getMessage)
    JsonDiff.assertDiff(withErrors, actualMessages)
  }
}
