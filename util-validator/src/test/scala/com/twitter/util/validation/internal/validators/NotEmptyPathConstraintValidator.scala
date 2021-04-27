package com.twitter.util.validation.internal.validators

import com.twitter.util.validation.caseclasses.Path
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.{ConstraintValidator, ConstraintValidatorContext}

class NotEmptyPathConstraintValidator extends ConstraintValidator[NotEmpty, Path] {
  override def isValid(
    obj: Path,
    constraintValidatorContext: ConstraintValidatorContext
  ): Boolean = obj match {
    case Path.Empty => false
    case _ => true
  }
}

class NotEmptyAnyConstraintValidator extends ConstraintValidator[NotEmpty, Any] {
  override def isValid(
    obj: Any,
    constraintValidatorContext: ConstraintValidatorContext
  ): Boolean = obj match {
    case Path.Empty => false
    case _ => true
  }
}
