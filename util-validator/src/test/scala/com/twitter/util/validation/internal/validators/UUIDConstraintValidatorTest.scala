package com.twitter.util.validation.internal.validators

import com.twitter.util.reflect.{Types => ReflectTypes}
import com.twitter.util.validation.ScalaValidator
import com.twitter.util.validation.caseclasses._
import com.twitter.util.validation.constraints.UUID
import jakarta.validation.ConstraintViolation
import org.scalacheck.Gen
import scala.reflect.runtime.universe._

class UUIDConstraintValidatorTest extends ConstraintValidatorTest {

  protected val validator: ScalaValidator = ScalaValidator()
  protected val testFieldName: String = "uuid"

  test("pass validation for valid uuid") {
    val passValue = Gen.uuid

    forAll(passValue) { value =>
      validate[UUIDExample](value.toString).isEmpty should be(true)
    }
  }

  test("fail validation for invalid uuid") {
    val passValue = Gen.alphaStr

    forAll(passValue) { value =>
      val violations = validate[UUIDExample](value)
      violations.size should equal(1)
      violations.head.getMessage should equal("must be a valid UUID")
      violations.head.getPropertyPath.toString should equal(testFieldName)
    }
  }

  private def validate[T: TypeTag](value: String): Set[ConstraintViolation[T]] = {
    super.validate[UUID, T](ReflectTypes.runtimeClass[T], testFieldName, value)
  }
}
