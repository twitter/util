package com.twitter.inject.flag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.google.inject.BindingAnnotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotates parsed flags.
 *
 * @see <a href="https://twitter.github.io/finatra/user-guide/getting-started/flags.html#flag-annotation"></a>
 * @see <a href="https://twitter.github.io/finatra/user-guide/getting-started/flags.html"></a>
 */
@Retention(RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@BindingAnnotation
public @interface Flagged {

    /** Name of the flag */
    String value();
}
