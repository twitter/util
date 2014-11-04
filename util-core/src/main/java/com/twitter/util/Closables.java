package com.twitter.util;

import scala.collection.JavaConversions;
import scala.runtime.BoxedUnit;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Java compatibility layer for {@link com.twitter.util.Closable}.
 */
public final class Closables {

  private Closables() { }

  /**
   * @see com.twitter.util.Closable$#all(scala.collection.Seq)
   */
  public static Closable all(Closable... closables) {
    return Closable$.MODULE$.all(
      JavaConversions.asScalaBuffer(Arrays.asList(closables))
    );
  }

  /**
   * @see com.twitter.util.Closable$#sequence(scala.collection.Seq)
   */
  public static Closable sequence(Closable... closables) {
    return Closable$.MODULE$.sequence(
      JavaConversions.asScalaBuffer(Arrays.asList(closables))
    );
  }

  /**
   * @see com.twitter.util.Closable$#make(scala.Function1)
   */
  public static Closable make(Function<Time, Future<BoxedUnit>> function) {
    return Closable$.MODULE$.make(function);
  }

  /**
   * @see com.twitter.util.Closable$#ref(java.util.concurrent.atomic.AtomicReference)
   */
  public static Closable ref(AtomicReference<Closable> reference) {
    return Closable$.MODULE$.ref(reference);
  }

  /**
   * @see com.twitter.util.Closable$#closeOnCollect(Closable, Object)
   */
  public static void closeOnCollect(Closable closable, Object object) {
    Closable$.MODULE$.closeOnCollect(closable, object);
  }

  /**
   * @see com.twitter.util.Closable$#nop()
   */
  public static Closable nop() {
    return Closable$.MODULE$.nop();
  }
}
