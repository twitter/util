package com.twitter.util.validation.internal.validators.notempty

import jakarta.validation.{ConstraintValidator, ConstraintValidatorContext}
import jakarta.validation.constraints.NotEmpty

/**
 * Intended to work exactly like [[org.hibernate.validator.internal.constraintvalidators.bv.notempty.NotEmptyValidatorForArray]]
 * but for Scala [[Iterable]] types.
 *
 * Check that the [[Iterable]] is not null and not empty.
 *
 * @see [[org.hibernate.validator.internal.constraintvalidators.bv.notempty.NotEmptyValidatorForArray]]
 */
private[validation] class NotEmptyValidatorForIterable
    extends ConstraintValidator[NotEmpty, Iterable[_]] {
  override def isValid(
    obj: Iterable[_],
    constraintValidatorContext: ConstraintValidatorContext
  ): Boolean = {
    if (obj == null) false
    else obj.nonEmpty
  }
}
