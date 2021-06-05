package com.twitter.util.jackson

import jakarta.validation.{ConstraintValidator, ConstraintValidatorContext}
import scala.util.control.NoStackTrace

class ThrowsRuntimeExceptionConstraintValidator
    extends ConstraintValidator[ThrowsRuntimeExceptionConstraint, Any] {
  override def isValid(
    obj: Any,
    constraintValidatorContext: ConstraintValidatorContext
  ): Boolean = {
    throw new RuntimeException("validator foo error") with NoStackTrace
  }
}
