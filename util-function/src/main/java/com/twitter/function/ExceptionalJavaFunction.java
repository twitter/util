package com.twitter.function;

/**
 * Used for Java 8 interop.
 * This is a SAM version of c.t.u.ExceptionalFunction.
 */
// @FunctionalInterface
public interface ExceptionalJavaFunction<A,B> {
    public B apply(A value) throws Throwable;
}
