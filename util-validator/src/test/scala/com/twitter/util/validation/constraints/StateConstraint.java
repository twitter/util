package com.twitter.util.validation.constraints;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.twitter.util.validation.internal.validators.StateConstraintValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = { StateConstraintValidator.class })
public @interface StateConstraint {

  /** Every constraint annotation must define a message element of type String. */
  String message() default "Please register with state CA";

  /**
   * Every constraint annotation must define a groups element that specifies the processing groups
   * with which the constraint declaration is associated.
   */
  Class<?>[] groups() default {};

  /**
   * Constraint annotations must define a payload element that specifies the payload with which
   * the constraint declaration is associated. *
   */
  Class<? extends Payload>[] payload() default {};
}
