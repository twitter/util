package com.twitter.util.jackson.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for {@link java.lang.annotation.Annotation} interfaces which define
 * a field that should be injected into a case class via Jackson
 * `com.fasterxml.jackson.databind.InjectableValues`.
 *
 * @see <a href="https://fasterxml.github.io/jackson-databind/javadoc/2.9/com/fasterxml/jackson/databind/InjectableValues.html">com.fasterxml.jackson.databind.InjectableValues</a>
 * @see <a href="https://twitter.github.io/finatra/user-guide/json/index.html#injectablevalues">Finatra User's Guide - JSON Injectable Values</a>
 * @see <a href="https://twitter.github.io/finatra/user-guide/http/requests.html#field-annotations">Finatra User's Guide - HTTP Request Field Annotations</a>
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectableValue {}
