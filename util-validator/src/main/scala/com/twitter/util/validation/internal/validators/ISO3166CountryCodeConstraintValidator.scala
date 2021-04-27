package com.twitter.util.validation.internal.validators

import com.twitter.util.validation.constraints.CountryCode
import com.twitter.util.validation.constraintvalidation.TwitterConstraintValidatorContext
import jakarta.validation.{ConstraintValidator, ConstraintValidatorContext}
import java.util.Locale

private object ISO3166CountryCodeConstraintValidator {

  /** @see [[https://www.iso.org/iso-3166-country-codes.html ISO 3166]] */
  val CountryCodes: Set[String] = Locale.getISOCountries.toSet
}

private[validation] class ISO3166CountryCodeConstraintValidator
    extends ConstraintValidator[CountryCode, Any] {
  import ISO3166CountryCodeConstraintValidator._

  @volatile private[this] var countryCode: CountryCode = _

  override def initialize(constraintAnnotation: CountryCode): Unit = {
    this.countryCode = constraintAnnotation
  }

  override def isValid(
    obj: Any,
    constraintValidatorContext: ConstraintValidatorContext
  ): Boolean = obj match {
    case null => true
    case typedValue: Array[Any] =>
      validationResult(typedValue, constraintValidatorContext)
    case typedValue: Iterable[Any] =>
      validationResult(typedValue, constraintValidatorContext)
    case anyValue =>
      validationResult(Seq(anyValue.toString), constraintValidatorContext)
  }

  /* Private */

  private[this] def validationResult(
    value: Iterable[Any],
    constraintValidatorContext: ConstraintValidatorContext
  ): Boolean = {
    val invalidCountryCodes = findInvalidCountryCodes(value)
    // an empty value is not a valid country code
    val valid = if (value.isEmpty) false else invalidCountryCodes.isEmpty

    if (!valid) {
      TwitterConstraintValidatorContext
        .addExpressionVariable("validatedValue", mkString(value))
        .withMessageTemplate(countryCode.message())
        .addConstraintViolation(constraintValidatorContext)
    }

    valid
  }

  private[this] def findInvalidCountryCodes(values: Iterable[Any]): Set[String] = {
    val uppercaseCountryCodes = values.toSet.map { value: Any =>
      value.toString.toUpperCase
    }
    uppercaseCountryCodes.diff(CountryCodes)
  }
}
