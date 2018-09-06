package com.twitter.concurrent;

import com.twitter.util.Future;

import scala.collection.JavaConverters;
import scala.collection.Seq;
import scala.collection.immutable.List;

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
    List<T> buffer = (List<T>)List.empty();
    for (T item : elems) {
      buffer = buffer.$colon$colon(item);
    }
    Spool<T> result = (Spool<T>)EMPTY;
    while(!buffer.isEmpty()){
      result = new Spool.Cons<T>(buffer.head(), Future.value(result));
      buffer = (List<T>)buffer.tail();
    }
    return result;
  }

  /**
   * Creates an empty `Spool`.
   */
  public static <T> Spool<T> newEmptySpool() {
    Collection<T> empty = Collections.emptyList();
    return Spools.newSpool(empty);
  }
}
