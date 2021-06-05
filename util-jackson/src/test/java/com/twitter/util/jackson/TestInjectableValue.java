package com.twitter.util.jackson;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * FOR TESTING ONLY
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestInjectableValue {
  /**
   * FOR TESTING ONLY
   * @return the value
   */
  String value() default "";
}
