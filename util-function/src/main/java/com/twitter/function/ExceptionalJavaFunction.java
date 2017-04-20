package com.twitter.function;

/**
 * Used for Java 8 interop.
 * This is a SAM version of com.twitter.util.ExceptionalFunction.
 *
 * See `com.twitter.util.Function.exfunc`.
 */
@FunctionalInterface
public interface ExceptionalJavaFunction<A,B> {
    public B apply(A value) throws Throwable;
}
