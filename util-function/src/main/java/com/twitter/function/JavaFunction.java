package com.twitter.function;

/**
 * Used for Java 8 interop.
 * This is a SAM version of com.twitter.util.Function.
 *
 * See `com.twitter.util.Function.func`.
 */
@FunctionalInterface
public interface JavaFunction<T, R> {
    public R apply(T value);
}
