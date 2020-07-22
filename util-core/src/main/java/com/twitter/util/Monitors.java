package com.twitter.util;

import java.util.function.Supplier;

import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import static com.twitter.util.Function.func0;

/**
 * Java friendly APIs for the com.twitter.util.Monitor companion
 * object.
 */
public final class Monitors {
  private Monitors() { }

  /**
   * The singleton `Monitor` instance that is the Monitor companion object.
   */
  public static Monitor instance() {
    return Monitor$.MODULE$;
  }

  /**
   * Equivalent to calling `Monitor.get`.
   */
  public static Monitor get() {
    return Monitor$.MODULE$.get();
  }

  /**
   * Equivalent to calling `Monitor.set`.
   */
  public static void set(Monitor monitor) {
    Monitor$.MODULE$.set(monitor);
  }

  /**
   * Equivalent to calling `Monitor.using`.
   */
  public static <T> T using(Monitor monitor, Supplier<T> supplier) {
    return Monitor$.MODULE$.using(monitor, func0(supplier));
  }

  /**
   * Equivalent to calling `Monitor.restoring`.
   */
  public static <T> T restoring(Supplier<T> supplier) {
    return Monitor$.MODULE$.restoring(func0(supplier));
  }

  /**
   * Equivalent to `Monitor.catcher`.
   */
  public static PartialFunction<Throwable, BoxedUnit> catcher() {
    return Monitor$.MODULE$.catcher();
  }

  /**
   * Equivalent to calling `Monitor.isActive`.
   */
  public static boolean isActive() {
    return Monitor$.MODULE$.isActive();
  }

}
