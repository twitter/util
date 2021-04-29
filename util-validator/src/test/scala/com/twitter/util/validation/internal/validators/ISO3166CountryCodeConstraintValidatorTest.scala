package com.twitter.util.validation.internal.validators

import com.twitter.util.reflect.{Types => ReflectTypes}
import com.twitter.util.validation.ScalaValidator
import com.twitter.util.validation.caseclasses.{
  CountryCodeArrayExample,
  CountryCodeExample,
  CountryCodeInvalidTypeExample,
  CountryCodeSeqExample
}
import com.twitter.util.validation.constraints.CountryCode
import jakarta.validation.ConstraintViolation
import java.util.Locale
import org.scalacheck.Gen
import org.scalacheck.Shrink.shrinkAny
import scala.reflect.runtime.universe._

class ISO3166CountryCodeConstraintValidatorTest extends ConstraintValidatorTest {
  protected val validator: ScalaValidator = ScalaValidator()
  protected val testFieldName: String = "countryCode"

  private val countryCodes = Locale.getISOCountries.filter(_.nonEmpty).toSeq

  test("pass validation for valid country code") {
    countryCodes.foreach { value =>
      validate[CountryCodeExample](value).isEmpty should be(true)
    }
  }

  test("fail validation for invalid country code") {
    forAll(genFakeCountryCode) { value =>
      val violations = validate[CountryCodeExample](value)
      violations.size should equal(1)
      violations.head.getMessage should equal(s"$value not a valid country code")
      violations.head.getPropertyPath.toString should equal(testFieldName)
    }
  }

  test("pass validation for valid country codes in seq") {
    val passValue = Gen.nonEmptyContainerOf[Seq, String](Gen.oneOf(countryCodes))

    forAll(passValue) { value =>
      validate[CountryCodeSeqExample](value).isEmpty should be(true)
    }
  }

  test("fail validation for empty seq") {
    val emptyValue = Seq.empty
    val violations = validate[CountryCodeSeqExample](emptyValue)
    violations.size should equal(1)
    violations.head.getMessage should equal("<empty> not a valid country code")
    violations.head.getPropertyPath.toString should equal(testFieldName)
  }

  test("fail validation for invalid country codes in seq") {
    val failValue = Gen.nonEmptyContainerOf[Seq, String](genFakeCountryCode)

    forAll(failValue) { value =>
      val violations = validate[CountryCodeSeqExample](value)
      violations.size should equal(1)
      violations.head.getMessage should equal(s"${mkString(value)} not a valid country code")
      violations.head.getPropertyPath.toString should equal(testFieldName)
    }
  }

  test("pass validation for valid country codes in array") {
    val passValue = Gen.nonEmptyContainerOf[Array, String](Gen.oneOf(countryCodes))

    forAll(passValue) { value =>
      validate[CountryCodeArrayExample](value).isEmpty should be(true)
    }
  }

  test("fail validation for invalid country codes in array") {
    val failValue = Gen.nonEmptyContainerOf[Array, String](genFakeCountryCode)

    forAll(failValue) { value =>
      val violations = validate[CountryCodeArrayExample](value)
      violations.size should equal(1)
      violations.head.getMessage should equal(s"${mkString(value)} not a valid country code")
      violations.head.getPropertyPath.toString should equal(testFieldName)
    }
  }

  test("fail validation for invalid country code type") {
    val failValue = Gen.choose[Int](0, 100)

    forAll(failValue) { value =>
      val violations = validate[CountryCodeInvalidTypeExample](value)
      violations.size should equal(1)
      violations.head.getMessage should equal(s"${value.toString} not a valid country code")
      violations.head.getPropertyPath.toString should equal(testFieldName)
    }
  }

  //generate random uppercase string for fake country code
  private def genFakeCountryCode: Gen[String] = {
    Gen
      .nonEmptyContainerOf[Seq, Char](Gen.alphaUpperChar)
      .map(_.mkString)
      .filter(!countryCodes.contains(_))
  }

  private def validate[T: TypeTag](value: Any): Set[ConstraintViolation[T]] = {
    super.validate[CountryCode, T](ReflectTypes.runtimeClass[T], testFieldName, value)
  }
}
