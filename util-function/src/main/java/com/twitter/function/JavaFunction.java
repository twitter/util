package com.twitter.function;

/**
 * Used for Java 8 interop.
 * This is a SAM version of c.t.u.Function.
 * It also exists as java.util.function.Function, but only from Java 8 onwards.
 * (Since twitter util must be backwards-compatible, we cannot use j.u.Function)
 */
// @FunctionalInterface
public interface JavaFunction<T, R> {
    public R apply(T value);
}
