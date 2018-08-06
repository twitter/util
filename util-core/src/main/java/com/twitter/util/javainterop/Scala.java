package com.twitter.util.javainterop;

import scala.Option;
import scala.Tuple2;

import static scala.collection.JavaConverters.asScalaBuffer;
import static scala.collection.JavaConverters.asScalaSet;

/**
 * A collection of conversions from Java collections to their immutable
 * Scala variants.
 *
 * See scala.collection.JavaConverters if you do not need immutable
 * collections.
 */
public final class Scala {

  private Scala() { throw new IllegalStateException(); }

  /**
   * Converts a {@link java.util.List} to an immutable Scala Seq.
   *
   * See scala.collection.JavaConverters.asScalaBuffer if you do
   * not need the returned Seq to be immutable.
   *
   * @return an empty Seq if the input is null.
   */
  @SuppressWarnings("unchecked")
  public static <E> scala.collection.immutable.Seq<E> asImmutableSeq(
      java.util.List<E> jList
  ) {
    if (jList == null) {
      return scala.collection.immutable.Seq$.MODULE$.<E>empty();
    } else {
      return asScalaBuffer(jList).toList();
    }
  }

  /**
   * Converts a {@link java.util.Set} to an immutable Scala Set.
   *
   * See scala.collection.JavaConverters.asScalaSet if you do
   * not need the returned Set to be immutable.
   *
   * @return an empty Set if the input is null.
   */
  @SuppressWarnings("unchecked")
  public static <E> scala.collection.immutable.Set<E> asImmutableSet(
      java.util.Set<E> jSet
  ) {
    if (jSet == null) {
      return scala.collection.immutable.Set$.MODULE$.<E>empty();
    } else {
      return asScalaSet(jSet).toSet();
    }
  }

  /**
   * Converts a {@link java.util.Map} to an immutable Scala Map.
   *
   * See scala.collection.JavaConverters.asScalaMap if you do
   * not need the returned Map to be immutable.
   *
   * @return an empty Map if the input is null.
   */
  public static <K, V> scala.collection.immutable.Map<K, V> asImmutableMap(
      java.util.Map<K, V> jMap
  ) {
    scala.collection.immutable.Map<K, V> sMap = scala.collection.immutable.Map$.MODULE$.empty();
    if (jMap != null) {
      for (java.util.Map.Entry<K, V> entry : jMap.entrySet()) {
        sMap = sMap.$plus(new Tuple2<K, V>(entry.getKey(), entry.getValue()));
      }
    }
    return sMap;
  }

  /**
   * Converts a {@link java.util.Optional} to a Scala Option.
   */
  public static <T> Option<T> asOption(
      java.util.Optional<T> optional
  ) {
    if (optional.isPresent()) return Option.<T>apply(optional.get());
    else return Option.<T>empty();
  }

}
