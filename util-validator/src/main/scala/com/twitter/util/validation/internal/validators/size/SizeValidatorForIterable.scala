package com.twitter.util.validation.internal.validators.size

import jakarta.validation.constraints.Size
import jakarta.validation.{ConstraintValidator, ConstraintValidatorContext}

/**
 * Intended to work exactly like [[org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForArray]]
 * but for Scala [[Iterable]] types.
 *
 * Check that the length of an [[Iterable]] is between `min` and `max`.
 *
 * @see [[org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForArray]]
 */
private[validation] class SizeValidatorForIterable extends ConstraintValidator[Size, Iterable[_]] {

  @volatile private[this] var min: Int = _
  @volatile private[this] var max: Int = _

  override def initialize(constraintAnnotation: Size): Unit = {
    this.min = constraintAnnotation.min()
    this.max = constraintAnnotation.max()
    validateParameters()
  }

  override def isValid(
    obj: Iterable[_],
    constraintValidatorContext: ConstraintValidatorContext
  ): Boolean = {
    if (obj == null) true
    else obj.size >= min && obj.size <= max
  }

  private def validateParameters(): Unit = {
    if (min < 0) throw new IllegalArgumentException("The min parameter cannot be negative.")
    if (max < 0) throw new IllegalArgumentException("The max parameter cannot be negative.")
    if (max < min) throw new IllegalArgumentException("The length cannot be negative.")
  }
}
