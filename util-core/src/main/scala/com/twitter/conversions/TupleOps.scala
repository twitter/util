package com.twitter.conversions

import scala.collection.{SortedMap, immutable, mutable}
import scala.math.Ordering

/**
 * Implicits for converting tuples.
 *
 * @example
 * {{{
 * import com.twitter.conversions.TupleOps._
 *
 * Seq(1 -> "tada", 2 -> "lala").toKeys
 * Seq(1 -> "tada", 2 -> "lala").mapValues(_.size)
 * Seq(1 -> "tada", 2 -> "lala").groupByKeyAndReduce(_ + "," + _)
 * Seq(1 -> "tada", 2 -> "lala").sortByKey
 * }}}
 */
object TupleOps {

  implicit class RichTuples[A, B](val self: Iterable[(A, B)]) extends AnyVal {
    def toKeys: Seq[A] = TupleOps.toKeys(self)

    def toKeySet: Set[A] = TupleOps.toKeySet(self)

    def toValues: Seq[B] = TupleOps.toValues(self)

    def mapValues[C](func: B => C): Seq[(A, C)] =
      TupleOps.mapValues(self, func)

    def groupByKey: Map[A, Seq[B]] =
      TupleOps.groupByKey(self)

    def groupByKeyAndReduce(reduceFunc: (B, B) => B): Map[A, B] =
      TupleOps.groupByKeyAndReduce(self, reduceFunc)

    def sortByKey(implicit ord: Ordering[A]): Seq[(A, B)] =
      TupleOps.sortByKey(self)

    def toSortedMap(implicit ord: Ordering[A]): SortedMap[A, B] =
      TupleOps.toSortedMap(self)
  }

  /**
   * Returns a new seq of the first element of each tuple.
   *
   * @param tuples the tuples for which all the first elements will be returned
   * @return a seq of the first element of each tuple
   */
  def toKeys[A, B](tuples: Iterable[(A, B)]): Seq[A] = {
    tuples.toSeq map { case (key, value) => key }
  }

  /**
   * Returns a new set of the first element of each tuple.
   *
   * @param tuples the tuples for which all the first elements will be returned
   * @return a set of the first element of each tuple
   */
  def toKeySet[A, B](tuples: Iterable[(A, B)]): Set[A] = {
    toKeys(tuples).toSet
  }

  /**
   * Returns a new seq of the second element of each tuple.
   *
   * @param tuples the tuples for which all the second elements
   *               will be returned
   * @return a seq of the second element of each tuple
   */
  def toValues[A, B](tuples: Iterable[(A, B)]): Seq[B] = {
    tuples.toSeq map { case (key, value) => value }
  }

  /**
   * Applies `func` to the second element of each tuple.
   *
   * @param tuples the tuples to which the func is applied
   * @param func the function literal which will be applied
   *             to the second element of each tuple
   * @return a seq of `func` transformed tuples
   */
  def mapValues[A, B, C](tuples: Iterable[(A, B)], func: B => C): Seq[(A, C)] = {
    tuples.toSeq map {
      case (key, value) =>
        key -> func(value)
    }
  }

  /**
   * Groups the tuples by matching the first element (key) in each tuple
   * returning a map from the key to a seq of the according second elements.
   *
   * For example:
   *
   * {{{
   *    groupByKey(Seq(1 -> "a", 1 -> "b", 2 -> "ab"))
   * }}}
   *
   * will return Map(2 -> Seq("ab"), 1 -> Seq("a", "b"))
   *
   * @param tuples the tuples or organize into a grouped map
   * @return a map of matching first elements to a sequence of
   *         their second elements
   */
  def groupByKey[A, B](tuples: Iterable[(A, B)]): Map[A, Seq[B]] = {
    val mutableMapBuilder = mutable.Map.empty[A, mutable.Builder[B, Seq[B]]]
    for ((a, b) <- tuples) {
      val seqBuilder = mutableMapBuilder.getOrElseUpdate(a, immutable.Seq.newBuilder[B])
      seqBuilder += b
    }

    val mapBuilder = immutable.Map.newBuilder[A, Seq[B]]
    for ((k, v) <- mutableMapBuilder) {
      mapBuilder += ((k, v.result()))
    }

    mapBuilder.result()
  }

  /**
   * First groups the tuples by matching first elements creating a
   * map of the first elements to a seq of the according second elements,
   * then reduces the seq to a single value according to the
   * applied `reduceFunc`.
   *
   * For example:
   *
   * {{{
   *   groupByKeyAndReduce(Seq(1 -> 5, 1 -> 6, 2 -> 7), _ + _)
   * }}}
   *
   * will return Map(2 -> 7, 1 -> 11)
   *
   * @param tuples the tuples or organize into a grouped map
   * @param reduceFunc the function to apply to the seq of second
   *                   elements that have been grouped together
   * @return a map of matching first elements to a value after
   *         reducing the grouped values with the `reduceFunc`
   */
  def groupByKeyAndReduce[A, B](tuples: Iterable[(A, B)], reduceFunc: (B, B) => B): Map[A, B] = {
    // use map instead of mapValues(deprecated since 2.13.0) for cross-building.
    groupByKey(tuples).map {
      case (k, values) =>
        k -> values.reduce(reduceFunc)
    }
  }

  /**
   * Sorts the tuples by the first element in each tuple.
   *
   * @param tuples the tuples to sort
   * @param ord the order in which to sort the tuples
   * @return the sorted tuples
   */
  def sortByKey[A, B](tuples: Iterable[(A, B)])(implicit ord: Ordering[A]): Seq[(A, B)] = {
    tuples.toSeq sortBy { case (key, value) => key }
  }

  /**
   * Sorts the tuples by the first element in each tuple and
   * returns a SortedMap.
   *
   * @param tuples the tuples to sort
   * @param ord the order in which to sort the tuples
   * @return a SortedMap of the tuples
   */
  def toSortedMap[A, B](tuples: Iterable[(A, B)])(implicit ord: Ordering[A]): SortedMap[A, B] = {
    SortedMap(tuples.toSeq: _*)
  }

}
