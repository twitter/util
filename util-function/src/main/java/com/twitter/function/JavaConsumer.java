package com.twitter.function;

/**
 * Used for Java 8 interop.
 * It also exists as java.util.function.Consumer, but only from Java 8 onwards.
 *
 * See `com.twitter.util.Function.cons`.
 */
@FunctionalInterface
public interface JavaConsumer<T> {
    public void apply(T value);
}
