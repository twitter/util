package com.twitter.util.validation.internal.validators

import com.twitter.util.validation.constraints.InvalidConstraint
import jakarta.validation.{ConstraintValidator, ConstraintValidatorContext}
import scala.util.control.NoStackTrace

class InvalidConstraintValidator extends ConstraintValidator[InvalidConstraint, Any] {
  override def isValid(
    obj: Any,
    constraintValidatorContext: ConstraintValidatorContext
  ): Boolean = throw new RuntimeException("validator foo error") with NoStackTrace
}
