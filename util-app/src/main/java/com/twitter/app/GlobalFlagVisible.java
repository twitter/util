package com.twitter.app;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An annotation to support the visibilty of global flags. Used
 * internally in the flags implementation.
 */
public @Retention(RetentionPolicy.RUNTIME) @Inherited @interface GlobalFlagVisible {}
