package com.twitter.util.validation.internal.validators

import com.twitter.util.validation.constraints.OneOf
import com.twitter.util.validation.constraintvalidation.TwitterConstraintValidatorContext
import jakarta.validation.{ConstraintValidator, ConstraintValidatorContext}

private[validation] class OneOfConstraintValidator extends ConstraintValidator[OneOf, Any] {

  @volatile private[this] var oneOf: OneOf = _
  @volatile private[this] var values: Set[String] = _

  override def initialize(constraintAnnotation: OneOf): Unit = {
    this.oneOf = constraintAnnotation
    this.values = constraintAnnotation.value().toSet
  }

  override def isValid(
    obj: Any,
    constraintValidatorContext: ConstraintValidatorContext
  ): Boolean = obj match {
    case null => true
    case arrayValue: Array[_] =>
      validationResult(arrayValue, values, constraintValidatorContext)
    case traversableValue: Iterable[_] =>
      validationResult(traversableValue, values, constraintValidatorContext)
    case anyValue =>
      validationResult(Seq(anyValue.toString), values, constraintValidatorContext)
  }

  /* Private */

  private[this] def validationResult(
    value: Iterable[_],
    oneOfValues: Set[String],
    constraintValidatorContext: ConstraintValidatorContext
  ): Boolean = {
    val invalidValues = findInvalidValues(value, oneOfValues)
    // an empty value is not one of the given values
    val valid = if (value.isEmpty) false else invalidValues.isEmpty
    if (!valid) {
      TwitterConstraintValidatorContext
        .addExpressionVariable("validatedValue", mkString(value))
        .withMessageTemplate(oneOf.message())
        .addConstraintViolation(constraintValidatorContext)
    }

    valid
  }

  private[this] def findInvalidValues(
    value: Iterable[_],
    oneOfValues: Set[String]
  ): Set[String] = {
    val valueAsStrings = value.map(_.toString).toSet
    valueAsStrings.diff(oneOfValues)
  }
}
