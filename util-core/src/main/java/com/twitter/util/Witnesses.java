package com.twitter.util;

import scala.runtime.BoxedUnit;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A Java adaptation of {@link com.twitter.util.Witness} companion object.
 */
public final class Witnesses {
  private Witnesses() { }

  /**
   * @see com.twitter.util.Witness$#apply(java.util.concurrent.atomic.AtomicReference) ;
   */
  public static <T> Witness<T> newWitness(AtomicReference<T> reference) {
    return Witness$.MODULE$.apply(reference);
  }

  /**
   * @see com.twitter.util.Witness$#apply(Promise)
   */
  public static <T> Witness<T> newWitness(Promise<T> promise) {
    return Witness$.MODULE$.apply(promise);
  }

  /**
   * @see com.twitter.util.Witness$#apply(scala.Function1)
   */
  public static <T> Witness<T> newWitness(Function<T, BoxedUnit> function) {
    return Witness$.MODULE$.apply(function);
  }

  /**
   * @see com.twitter.util.Witness$#apply(Updatable)
   */
  public static <T> Witness<T> newWitness(Updatable<T> updatable) {
    return Witness$.MODULE$.apply(updatable);
  }
}
