package com.twitter.util.validation.internal.validators

import com.twitter.util.validation.constraints.ConsistentDateParameters
import jakarta.validation.constraintvalidation.{SupportedValidationTarget, ValidationTarget}
import jakarta.validation.{ConstraintValidator, ConstraintValidatorContext}
import java.time.LocalDate

@SupportedValidationTarget(Array(ValidationTarget.PARAMETERS))
class ConsistentDateParametersValidator
    extends ConstraintValidator[ConsistentDateParameters, Array[AnyRef]] {
  override def isValid(
    value: Array[AnyRef],
    constraintValidatorContext: ConstraintValidatorContext
  ): Boolean = {
    if (value.length != 2) throw new IllegalArgumentException("Unexpected method signature")
    else if (value(0) == null || value(1) == null) true
    else if (!value(0).isInstanceOf[LocalDate] || !value(1).isInstanceOf[LocalDate])
      throw new IllegalArgumentException("Unexpected method signature")
    else value(0).asInstanceOf[LocalDate].isBefore(value(1).asInstanceOf[LocalDate])
  }
}
