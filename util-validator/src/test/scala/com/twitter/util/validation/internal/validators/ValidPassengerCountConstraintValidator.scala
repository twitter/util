package com.twitter.util.validation.internal.validators

import com.twitter.util.validation.caseclasses.Car
import com.twitter.util.validation.constraints.ValidPassengerCount
import jakarta.validation.{ConstraintValidator, ConstraintValidatorContext}

class ValidPassengerCountConstraintValidator extends ConstraintValidator[ValidPassengerCount, Car] {

  @volatile private[this] var maxPassengers: Long = _

  override def initialize(constraintAnnotation: ValidPassengerCount): Unit = {
    this.maxPassengers = constraintAnnotation.max()
  }

  override def isValid(
    car: Car,
    constraintValidatorContext: ConstraintValidatorContext
  ): Boolean =
    if (car == null) true
    else car.passengers.nonEmpty && car.passengers.size <= this.maxPassengers
}
