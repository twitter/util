package com.twitter.util

/**
 * A Diff stores the necessary instructions required to bring two
 * version of a data structure into agreement.
 */
trait Diff[CC[_], T] {
  /**
   * Patch up the given collection so that it matches
   * its source.
   */
  def patch(coll: CC[T]): CC[T]
  
  /**
   * Map the values present in the diff. The following 
   * invariant must hold:
   *
   * {{{
   * val diff: Diff[CC, T]
   * val left, right: CC[T]
   *
   * Diffable.diff(left, right).map(f).patch(left.map(f)) === right.map(f)
   * }}}
   */
  def map[U](f: T => U): Diff[CC, U]
}

/**
 * A type class that tells how to compute a [[Diff]] between
 * two versions of a collection `CC[T]`.
 */
trait Diffable[CC[_]] {
  /**
   * Compute a [[Diff]] that may later be used to bring two
   * versions of a data structure into agreement; that is:
   * 
   * {{{
   * Diffable.diff(a, b).patch(a) == b
   * }}}
   *
   * `diff` respects equality such that
   *
   * {{{
   * Diffable.diff(a, b).patch(a') == b
   * }}}
   *
   * when `a == a'`.
   *
   * Behavior is undefined whenever `a != a'`.
   */
  def diff[T](left: CC[T], right: CC[T]): Diff[CC, T]
  
  /**
   * The identity collection CC[T].
   */
  def empty[T]: CC[T]
}

/**
 * Diffable defines common type class instances for [[Diffable]].
 */
object Diffable {
  private case class SetDiff[T](add: Set[T], remove: Set[T]) extends Diff[Set, T] {
    def patch(coll: Set[T]): Set[T] =
      coll ++ add -- remove

    def map[U](f: T => U): SetDiff[U] =
      SetDiff(add.map(f), remove.map(f))

    override def toString = s"Diff(+$add, -$remove)"
  }
  
  private case class SeqDiff[T](limit: Int, insert: Map[Int, T]) extends Diff[Seq, T] {
    def patch(coll: Seq[T]): Seq[T] = {
      val out = new Array[Any](limit)
      coll.copyToArray(out, 0, limit)

      for ((i, v) <- insert)
        out(i) = v

      out.toSeq.asInstanceOf[Seq[T]]
    }

    def map[U](f: T => U): SeqDiff[U] =
      SeqDiff(limit, insert.mapValues(f))
  }

  implicit val ofSet: Diffable[Set] = new Diffable[Set] {
    def diff[T](left: Set[T], right: Set[T]) = SetDiff(right--left, left--right)
    def empty[T] = Set.empty
  }

  implicit val ofSeq: Diffable[Seq] = new Diffable[Seq] {
    def diff[T](left: Seq[T], right: Seq[T]): SeqDiff[T] =
      if (left.length < right.length) {
        val SeqDiff(_, insert) = diff(left, right.take(left.length))
        SeqDiff(right.length, insert ++ ((left.length until right.length) map { i => (i -> right(i)) }))
      } else if (left.length > right.length) {
        diff(left.take(right.length), right)
      } else {
        val insert = for (((x, y), i) <- left.zip(right).zipWithIndex if x != y)
          yield (i -> y)
        SeqDiff(left.length, insert.toMap)
      }

    def empty[T] = Seq.empty
  }

  /**
   * Compute a [[Diff]] that may later be used to bring two
   * versions of a data structure into agreement; that is:
   * 
   * {{{
   * Diffable.diff(left, right).patch(left) == right
   * }}}
   */
  def diff[CC[_]: Diffable, T](left: CC[T], right: CC[T]): Diff[CC, T] = 
    implicitly[Diffable[CC]].diff(left, right)

  /**
   * The identity collection CC[T].
   */
  def empty[CC[_]: Diffable, T]: CC[T] =
    implicitly[Diffable[CC]].empty
}
