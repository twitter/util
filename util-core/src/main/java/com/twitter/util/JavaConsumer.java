package com.twitter.util;

/**
 * Used for Java 8 interop.
 * It also exists as java.util.function.Consumer, but only from Java 8 onwards.
 * (Since twitter util must be backwards-compatible, we cannot use j.u.f.Function)
 */
// @FunctionalInterface
public interface JavaConsumer<T> {
    public void apply(T value);
}
