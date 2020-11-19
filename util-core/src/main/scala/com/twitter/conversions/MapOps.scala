package com.twitter.conversions

import scala.collection.{SortedMap, immutable, mutable}

/**
 * Implicits for converting [[Map]]s.
 *
 * @example
 * {{{
 * import com.twitter.conversions.MapOps._
 *
 * Map(1 -> "a").mapKeys { _.toString }
 * Map(1 -> "a").invert
 * Map(1 -> "a", 2 -> "b").filterValues { _ == "b" }
 * Map(2 -> "b", 1 -> "a").toSortedMap
 * }}}
 */
object MapOps {

  implicit class RichMap[K, V](val self: Map[K, V]) extends AnyVal {
    def mapKeys[T](func: K => T): Map[T, V] = MapOps.mapKeys(self, func)

    def invert: Map[V, Seq[K]] = MapOps.invert(self)

    def invertSingleValue: Map[V, K] = MapOps.invertSingleValue(self)

    def filterValues(func: V => Boolean): Map[K, V] =
      MapOps.filterValues(self, func)

    def filterNotValues(func: V => Boolean): Map[K, V] =
      MapOps.filterNotValues(self, func)

    def filterNotKeys(func: K => Boolean): Map[K, V] =
      MapOps.filterNotKeys(self, func)

    def toSortedMap(implicit ordering: Ordering[K]): SortedMap[K, V] =
      MapOps.toSortedMap(self)
  }

  /**
   * Transforms the keys of the map according to the given `func`.
   *
   * @param inputMap the map to transform the keys
   * @param func the function literal which will be applied to the
   *             keys of the map
   * @return the map with transformed keys and original values
   */
  def mapKeys[K, V, T](inputMap: Map[K, V], func: K => T): Map[T, V] = {
    for ((k, v) <- inputMap) yield {
      func(k) -> v
    }
  }

  /**
   * Inverts the map so that the input map's values are the distinct keys and
   * the corresponding keys, represented in a sequence, as the values.
   *
   * @param inputMap the map to invert
   * @return the inverted map
   */
  def invert[K, V](inputMap: Map[K, V]): Map[V, Seq[K]] = {
    val invertedMapWithBuilderValues = mutable.Map.empty[V, mutable.Builder[K, Seq[K]]]
    for ((k, v) <- inputMap) {
      val valueBuilder = invertedMapWithBuilderValues.getOrElseUpdate(v, Seq.newBuilder[K])
      valueBuilder += k
    }

    val invertedMap = immutable.Map.newBuilder[V, Seq[K]]
    for ((k, valueBuilder) <- invertedMapWithBuilderValues) {
      invertedMap += (k -> valueBuilder.result)
    }

    invertedMap.result()
  }

  /**
   * Inverts the map so that every input map value becomes the key and the
   * input map key becomes the value.
   *
   * @param inputMap the map to invert
   * @return the inverted map
   */
  def invertSingleValue[K, V](inputMap: Map[K, V]): Map[V, K] = {
    inputMap map { _.swap }
  }

  /**
   * Filters the pairs in the map which values satisfy the predicate
   * represented by `func`.
   *
   * @param inputMap the map which will be filtered
   * @param func the predicate that needs to be satisfied to
   *             select a key-value pair
   * @return the filtered map
   */
  def filterValues[K, V](inputMap: Map[K, V], func: V => Boolean): Map[K, V] = {
    inputMap filter {
      case (_, value) =>
        func(value)
    }
  }

  /**
   * Filters the pairs in the map which values do NOT satisfy the
   * predicate represented by `func`.
   *
   * @param inputMap the map which will be filtered
   * @param func the predicate that needs to be satisfied to NOT
   *             select a key-value pair
   * @return the filtered map
   */
  def filterNotValues[K, V](inputMap: Map[K, V], func: V => Boolean): Map[K, V] =
    filterValues(inputMap, (v: V) => !func(v))

  /**
   * Filters the pairs in the map which keys do NOT satisfy the
   * predicate represented by `func`.
   *
   * @param inputMap the map which will be filtered
   * @param func the predicate that needs to be satisfied to NOT
   *             select a key-value pair
   * @return the filtered map
   */
  def filterNotKeys[K, V](inputMap: Map[K, V], func: K => Boolean): Map[K, V] = {
    // use filter instead of filterKeys(deprecated since 2.13.0) for cross-building.
    inputMap.filter { case (key, _) => !func(key) }
  }

  /**
   * Sorts the map by the keys and returns a SortedMap.
   *
   * @param inputMap the map to sort
   * @param ordering the order in which to sort the map
   * @return a SortedMap
   */
  def toSortedMap[K, V](inputMap: Map[K, V])(implicit ordering: Ordering[K]): SortedMap[K, V] = {
    SortedMap[K, V](inputMap.toSeq: _*)
  }

}
