package com.twitter.function;

/**
 * Used for Java 8 interop.
 * This is a SAM version of com.twitter.util.ExceptionalFunction0.
 *
 * See `com.twitter.util.Function.exfunc0`.
 */
@FunctionalInterface
public interface ExceptionalSupplier<T> {
    public T apply() throws Throwable;
}
