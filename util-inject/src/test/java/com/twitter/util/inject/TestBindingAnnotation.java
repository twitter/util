package com.twitter.util.inject;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.google.inject.BindingAnnotation;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * HELPER ANNOTATION FOR TESTS.
 * <p>
 * Note: This is not to be included in any published test:jar.
 */
@BindingAnnotation
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface TestBindingAnnotation {}
