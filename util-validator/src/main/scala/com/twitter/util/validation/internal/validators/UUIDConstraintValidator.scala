package com.twitter.util.validation.internal.validators

import com.twitter.util.Try
import com.twitter.util.validation.constraints.UUID
import jakarta.validation.{ConstraintValidator, ConstraintValidatorContext}
import java.util.{UUID => JUUID}

private[validation] class UUIDConstraintValidator extends ConstraintValidator[UUID, String] {

  override def isValid(
    obj: String,
    constraintValidatorContext: ConstraintValidatorContext
  ): Boolean = {
    if (obj == null) true
    else Try(JUUID.fromString(obj)).isReturn
  }
}
