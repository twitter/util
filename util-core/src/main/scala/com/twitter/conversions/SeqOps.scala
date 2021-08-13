package com.twitter.conversions

import scala.annotation.tailrec

abstract class SeqUtilCompanion {
  def hasKnownSize[A](self: Seq[A]): Boolean
}

/**
 * Implicits for converting [[Seq]]s.
 *
 * @example
 * {{{
 * import com.twitter.conversions.SeqOps._
 *
 * Seq("a", "and").createMap(_.size, _)
 * Seq("a", "and").groupBySingleValue(_.size)
 * Seq("a", "and").findItemAfter("a")
 *
 * var numStrings = 0
 * Seq(1, 2) foreachPartial {
 *   case e: Int =>
 *     numStrings += 1
 * }
 * }}}
 */
object SeqOps {

  implicit class RichSeq[A](val self: Seq[A]) extends AnyVal {

    def createMap[K, V](keys: A => K, values: A => V): Map[K, V] =
      SeqOps.createMap(self, keys, values)

    def createMap[K, V](values: A => V): Map[A, V] =
      SeqOps.createMap(self, values)

    def foreachPartial(pf: PartialFunction[A, Unit]): Unit =
      SeqOps.foreachPartial(self, pf)

    /**
     * Creates a map from the given sequence by mapping each transformed
     * element (the key) by applying the function `keys` to the sequence and
     * using the element in the sequence as the value. Chooses the last
     * element in the seq when a key collision occurs.
     *
     * @param keys the function that transforms a given element in the seq to a key
     * @return a map of the given sequence
     */
    def groupBySingleValue[B](keys: A => B): Map[B, A] =
      createMap(keys, identity)

    def findItemAfter(itemToFind: A): Option[A] =
      SeqOps.findItemAfter(self, itemToFind)
  }

  /**
   * Creates a map from the given sequence by applying the functions `keys`
   * and `values` to the sequence.
   *
   * @param sequence the seq to convert to a map
   * @param keys the function that transforms a given element in the seq to a key
   * @param values the function that transforms a given element in the seq to a value
   * @return a map of the given sequence
   */
  def createMap[A, K, V](sequence: Seq[A], keys: A => K, values: A => V): Map[K, V] = {
    sequence.iterator.map { elem =>
      keys(elem) -> values(elem)
    }.toMap
  }

  /**
   * Creates a map from the given sequence by mapping each element of the
   * sequence (the key) to the transformed element (the value) by applying
   * the function `values`.
   *
   * @param sequence the seq to convert to a map
   * @param values the function that transforms a given element in the seq to a value
   * @return a map of the given sequence
   */
  def createMap[A, K, V](sequence: Seq[A], values: A => V): Map[A, V] = {
    sequence.iterator.map { elem =>
      elem -> values(elem)
    }.toMap
  }

  /**
   * Applies the given partial function to the sequence.
   *
   * @param sequence the seq onto which the partial function is applied
   * @param pf the partial function to apply to the seq
   */
  def foreachPartial[A](sequence: Seq[A], pf: PartialFunction[A, Unit]): Unit = {
    sequence.foreach { elem =>
      if (pf.isDefinedAt(elem)) {
        pf(elem)
      }
    }
  }

  /**
   * Finds the element after the given element to find.
   *
   * @param sequence the seq to scan for the element
   * @param itemToFind the element before the one returned
   * @return an Option of the element after the item to find
   */
  def findItemAfter[A](sequence: Seq[A], itemToFind: A): Option[A] = {
    @tailrec
    def recurse(itemToFind: A, seq: Seq[A]): Option[A] = seq match {
      case Seq(x, xs @ _*) if x == itemToFind => xs.headOption
      case Seq(x, xs @ _*) => recurse(itemToFind, xs)
      case Seq() => None
    }
    recurse(itemToFind, sequence)
  }

}
