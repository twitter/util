package com.twitter.util.validation.internal.validators

import com.twitter.util.reflect.{Types => ReflectTypes}
import com.twitter.util.validation.ScalaValidator
import com.twitter.util.validation.caseclasses._
import com.twitter.util.validation.constraints.OneOf
import jakarta.validation.ConstraintViolation
import org.scalacheck.Gen
import scala.reflect.runtime.universe._

class OneOfConstraintValidatorTest extends ConstraintValidatorTest {

  protected val validator: ScalaValidator = ScalaValidator()
  protected val testFieldName: String = "enumValue"

  val oneOfValues: Set[String] = Set("a", "B", "c")

  test("pass validation for single value") {
    oneOfValues.foreach { value =>
      validate[OneOfExample](value).isEmpty should be(true)
    }
  }

  test("fail validation for single value") {
    val failValue = Gen.alphaStr.filter(!oneOfValues.contains(_))

    forAll(failValue) { value =>
      val violations = validate[OneOfExample](value)
      violations.size should equal(1)
      violations.head.getMessage.endsWith("not one of [a, B, c]") should be(true)
      violations.head.getPropertyPath.toString should equal(testFieldName)
    }
  }

  test("pass validation for seq") {
    val passValue = Gen.nonEmptyContainerOf[Seq, String](Gen.oneOf(oneOfValues.toSeq))

    forAll(passValue) { value =>
      // shrinks end up with collection of empty string
      if (value != null && !value.exists(_.nonEmpty) && value.nonEmpty) {
        validate[OneOfSeqExample](value)
      }
    }
  }

  test("fail validation for empty seq") {
    val emptySeq = Seq.empty

    val violations = validate[OneOfSeqExample](emptySeq)
    violations.size should equal(1)
    violations.head.getMessage.endsWith("not one of [a, B, c]") should be(true)
    violations.head.getPropertyPath.toString should equal(testFieldName)
  }

  test("fail validation for invalid value in seq") {
    val failValue =
      Gen.nonEmptyContainerOf[Seq, String](Gen.alphaStr.filter(!oneOfValues.contains(_)))

    forAll(failValue) { value =>
      val violations = validate[OneOfSeqExample](value)
      violations.size should equal(1)
      violations.head.getMessage.endsWith("not one of [a, B, c]") should be(true)
      violations.head.getPropertyPath.toString should equal(testFieldName)
    }
  }

  test("fail validation for invalid type") {
    val failValue = Gen.choose(0, 100)

    forAll(failValue) { value =>
      val violations = validate[OneOfInvalidTypeExample](value)
      violations.size should equal(1)
      violations.head.getMessage.endsWith("not one of [a, B, c]") should be(true)
      violations.head.getPropertyPath.toString should equal(testFieldName)
    }
  }

  private def validate[T: TypeTag](value: Any): Set[ConstraintViolation[T]] =
    super.validate[OneOf, T](ReflectTypes.runtimeClass[T], testFieldName, value)
}
