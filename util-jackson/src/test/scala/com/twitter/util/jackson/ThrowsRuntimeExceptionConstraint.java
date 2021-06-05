package com.twitter.util.jackson;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = {ThrowsRuntimeExceptionConstraintValidator.class})
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
public @interface ThrowsRuntimeExceptionConstraint {

  /** Every constraint annotation must define a message element of type String. */
  String message() default "";

  /**
   * Every constraint annotation must define a groups element that specifies the processing groups
   * with which the constraint declaration is associated.
   */
  Class<?>[] groups() default {};

  /**
   * Constraint annotations must define a payload element that specifies the payload with which
   * the constraint declaration is associated.
   */
  Class<? extends Payload>[] payload() default {};
}
