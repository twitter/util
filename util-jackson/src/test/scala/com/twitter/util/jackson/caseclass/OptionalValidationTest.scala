package com.twitter.util.jackson.caseclass

import com.twitter.util.WrappedValue
import com.twitter.util.jackson.ScalaObjectMapper
import com.twitter.util.jackson.caseclass.exceptions.CaseClassMappingException
import com.twitter.util.validation.MethodValidation
import com.twitter.util.validation.constraints.OneOf
import com.twitter.util.validation.engine.MethodValidationResult
import jakarta.validation.constraints.{Min, NotEmpty}
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

private object OptionalValidationTest {
  case class State(
    @OneOf(Array("active", "inactive"))
    state: String)
      extends WrappedValue[String]

  case class Threshold(
    @NotEmpty id: Option[String],
    @Min(0) lowerBound: Int,
    @Min(0) upperBound: Int,
    state: State) {
    @MethodValidation
    def method: MethodValidationResult = MethodValidationResult.validIfTrue(
      lowerBound <= upperBound,
      "Lower Bound cannot be greater than Upper Bound"
    )
  }
}

@RunWith(classOf[JUnitRunner])
class OptionalValidationTest extends AnyFunSuite with Matchers {
  import OptionalValidationTest._

  private val defaultMapper =
    ScalaObjectMapper.builder.objectMapper

  private val nullValidationMapper =
    ScalaObjectMapper.builder.withNoValidation.objectMapper

  test("default mapper will trigger NotEmpty validation") {
    val invalid = Threshold(Some(""), 1, 4, State("active"))
    intercept[CaseClassMappingException] {
      check(defaultMapper, invalid)
    }
  }

  test("default mapper will trigger lower and upper min validation") {
    val invalid = Threshold(Some(""), -1, -1, State("active"))
    intercept[CaseClassMappingException] {
      check(defaultMapper, invalid)
    }
  }

  test("default mapper will trigger invalid state") {
    val invalid = Threshold(Some("id"), 4, 6, State("other"))
    intercept[CaseClassMappingException] {
      check(defaultMapper, invalid)
    }
  }

  test("default mapper will trigger method validation") {
    val invalid = Threshold(None, 8, 4, State("active"))
    intercept[CaseClassMappingException] {
      check(defaultMapper, invalid)
    }
  }

  test("no-op mapper will allow values that are considered invalid") {
    val invalid = Threshold(Some(""), -1, -3, State("other"))
    check(nullValidationMapper, invalid)
  }

  test("no-op mapper will never call method validation") {
    val invalid = Threshold(None, 8, 4, State("active"))
    check(nullValidationMapper, invalid)
  }

  def check[T: Manifest](mapper: ScalaObjectMapper, check: T): Unit = {
    val str = mapper.writeValueAsString(check)
    val result = mapper.parse[T](str)
    check should equal(result)
  }
}
