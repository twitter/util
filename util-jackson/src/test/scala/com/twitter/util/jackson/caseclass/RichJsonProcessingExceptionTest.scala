package com.twitter.util.jackson.caseclass

import com.fasterxml.jackson.core.{JsonGenerator, JsonParseException, JsonParser}
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.`type`.SimpleType
import com.fasterxml.jackson.databind.exc.{
  IgnoredPropertyException,
  InvalidDefinitionException,
  MismatchedInputException
}
import com.twitter.util.jackson.CaseClassWithAllTypes
import com.twitter.util.jackson.caseclass.exceptions._
import com.twitter.util.mock.Mockito
import java.util
import java.util.Collections
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RichJsonProcessingExceptionTest extends AnyFunSuite with Matchers with Mockito {
  private[this] val genericType = SimpleType.constructUnsafe(classOf[String])
  private val message = "this is a test"

  test("JsonMappingException with cause") {
    val cause = new IllegalArgumentException(message)
    val e = new JsonMappingException(null, message, cause)

    // should be the mapping exception message
    e.errorMessage should equal(message)
  }

  test("JsonMappingException with no cause") {
    val e = new JsonMappingException(null, message)

    // should be the mapping exception message
    e.errorMessage should equal(message)
  }

  test("MismatchedInputException with cause") {
    val mockJsonParser = mock[JsonParser]

    val e = MismatchedInputException.from(
      mockJsonParser,
      genericType,
      message
    )

    // should be from the cause
    e.errorMessage should equal(message)
  }

  test("InvalidDefinitionException with no cause") {
    val mockJsonGenerator = mock[JsonGenerator]

    val e = InvalidDefinitionException.from(mockJsonGenerator, message, genericType)

    // should be from the invalid definition exception message via `getOriginalMessage`
    e.errorMessage should equal(message)
  }

  test("IgnoredPropertyException with no cause") {
    val mockJsonParser = mock[JsonParser]

    val e = IgnoredPropertyException.from(
      mockJsonParser,
      CaseClassWithAllTypes.getClass,
      "noname",
      Collections.emptySet().asInstanceOf[util.Collection[Object]])

    // should be from the ignored property exception message via `getOriginalMessage`
    e.errorMessage should equal(
      """Ignored field "noname" (class com.twitter.util.jackson.CaseClassWithAllTypes$) encountered; mapper configured not to allow this""")
  }

  test("JsonParseException with cause") {
    val cause = new IllegalArgumentException(message)
    val e = new JsonParseException(null, "this is not the message", cause)

    // should be from the cause
    e.errorMessage should equal(message)
  }

  test("JsonParseException with no cause") {
    val e = new JsonParseException(null, message)

    // should be from the parse exception message via `getOriginalMessage`
    e.errorMessage should equal(message)
  }

}
