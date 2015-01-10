package com.twitter.util;

/**
 * Used for Java 8 interop.
 * This is a SAM version of c.t.u.ExceptionalFunction (returning Unit/void).
 */
// @FunctionalInterface
public interface ExceptionalJavaConsumer<T> {
    public void apply(T value) throws Throwable;
}
