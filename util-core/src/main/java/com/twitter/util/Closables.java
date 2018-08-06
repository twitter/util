package com.twitter.util;

import scala.collection.JavaConverters;
import scala.runtime.BoxedUnit;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Java compatibility layer for {@link com.twitter.util.Closable}.
 */
public final class Closables {

  /**
   * @see com.twitter.util.Closable$#nop()
   */
  public static final Closable NOP = Closable$.MODULE$.nop();

  private Closables() { }

  /**
   * @see com.twitter.util.Closable$#close(scala.AnyRef)
   */
  public static Future<BoxedUnit> close(Object o) {
    return Closable$.MODULE$.close(o);
  }

  /**
   * @see com.twitter.util.Closable$#close(scala.AnyRef, com.twitter.util.Time)
   */
  public static Future<BoxedUnit> close(Object o, Time deadline) {
    return Closable$.MODULE$.close(o, deadline);
  }

  /**
   * @see com.twitter.util.Closable$#close(scala.AnyRef, com.twitter.util.Duration)
   */
  public static Future<BoxedUnit> close(Object o, Duration after) {
    return Closable$.MODULE$.close(o, after);
  }

  /**
   * @see com.twitter.util.Closable$#all(scala.collection.Seq)
   */
  public static Closable all(Closable... closables) {
    return Closable$.MODULE$.all(
      JavaConverters.asScalaBuffer(Arrays.asList(closables))
    );
  }

  /**
   * @see com.twitter.util.Closable$#sequence(scala.collection.Seq)
   */
  public static Closable sequence(Closable... closables) {
    return Closable$.MODULE$.sequence(
      JavaConverters.asScalaBuffer(Arrays.asList(closables))
    );
  }

  /**
   * @see com.twitter.util.Closable$#make(scala.Function1)
   */
  public static Closable newClosable(Function<Time, Future<BoxedUnit>> function) {
    return Closable$.MODULE$.make(function);
  }

  /**
   * @see com.twitter.util.Closable$#ref(java.util.concurrent.atomic.AtomicReference)
   */
  public static Closable newClosable(AtomicReference<Closable> reference) {
    return Closable$.MODULE$.ref(reference);
  }

  /**
   * @see com.twitter.util.Closable$#closeOnCollect(Closable, Object)
   */
  public static void closeOnCollect(Closable closable, Object object) {
    Closable$.MODULE$.closeOnCollect(closable, object);
  }
}
