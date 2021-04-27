package com.twitter.util.validation.constraints;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.twitter.util.validation.internal.validators.ConsistentDateParametersValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ METHOD, ANNOTATION_TYPE })
@Retention(RUNTIME)
@Constraint(validatedBy = { ConsistentDateParametersValidator.class })
@Documented
public @interface ConsistentDateParameters {

  /** message */
  String message() default "start is not before end";

  /** groups */
  Class<?>[] groups() default { };

  /** payload */
  Class<? extends Payload>[] payload() default { };
}
