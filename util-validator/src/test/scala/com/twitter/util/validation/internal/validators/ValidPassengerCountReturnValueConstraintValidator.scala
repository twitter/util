package com.twitter.util.validation.internal.validators

import com.twitter.util.validation.caseclasses.CarWithPassengerCount
import com.twitter.util.validation.constraints.ValidPassengerCountReturnValue
import jakarta.validation.constraintvalidation.{SupportedValidationTarget, ValidationTarget}
import jakarta.validation.{ConstraintValidator, ConstraintValidatorContext}

@SupportedValidationTarget(Array(ValidationTarget.ANNOTATED_ELEMENT))
class ValidPassengerCountReturnValueConstraintValidator
    extends ConstraintValidator[ValidPassengerCountReturnValue, CarWithPassengerCount] {

  @volatile private[this] var maxPassengers: Long = _

  override def initialize(constraintAnnotation: ValidPassengerCountReturnValue): Unit = {
    this.maxPassengers = constraintAnnotation.max()
  }

  override def isValid(
    obj: CarWithPassengerCount,
    constraintValidatorContext: ConstraintValidatorContext
  ): Boolean = obj.passengers.nonEmpty && obj.passengers.size <= this.maxPassengers
}
