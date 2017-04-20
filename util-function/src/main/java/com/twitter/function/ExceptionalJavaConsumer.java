package com.twitter.function;

/**
 * Used for Java 8 interop.
 * This is a SAM version of com.twitter.util.ExceptionalFunction (returning Unit/void).
 *
 * See `com.twitter.util.Function.excons`.
 */
@FunctionalInterface
public interface ExceptionalJavaConsumer<T> {
    public void apply(T value) throws Throwable;
}
