package com.twitter.app;

import java.lang.annotation.*;

/**
 * An annotation to support the visibilty of global flags. Used
 * internally in the flags implementation.
 */
public @Retention(RetentionPolicy.RUNTIME) @Inherited @interface GlobalFlagVisible {}
