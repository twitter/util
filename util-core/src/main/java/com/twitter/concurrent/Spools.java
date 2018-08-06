package com.twitter.concurrent;

import scala.collection.JavaConverters;
import scala.collection.Seq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * A Java adaptation of {@link com.twitter.concurrent.Spool} companion object.
 */
public final class Spools {
  private Spools() { }

  /**
   * @see Spool$#empty()
   */
  public static final Spool<?> EMPTY = Spool$.MODULE$.empty();

  /**
   * Creates a new `Spool` of given `elems`.
   */
  public static <T> Spool<T> newSpool(Collection<T> elems) {
    Seq<T> seq = JavaConverters.collectionAsScalaIterableConverter(elems).asScala().toSeq();
    return new Spool.ToSpool<T>(seq).toSpool();
  }

  /**
   * Creates an empty `Spool`.
   */
  public static <T> Spool<T> newEmptySpool() {
    Collection<T> empty = Collections.emptyList();
    return Spools.newSpool(empty);
  }
}
