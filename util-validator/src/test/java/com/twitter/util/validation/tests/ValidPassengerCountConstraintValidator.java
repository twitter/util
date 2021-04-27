package com.twitter.util.validation.tests;

import com.twitter.util.validation.constraints.ValidPassengerCount;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPassengerCountConstraintValidator implements ConstraintValidator<ValidPassengerCount, Car> {

  private long max;

  @Override
  public void initialize(ValidPassengerCount constraintAnnotation) {
    this.max = constraintAnnotation.max();
  }

  @Override
  public boolean isValid(Car car, ConstraintValidatorContext context) {
    if ( car == null ) {
      return true;
    }

    return car.getPassengers().size() <= this.max;
  }
}
