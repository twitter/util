package com.twitter.concurrent

import com.twitter.io.{Buf, Reader}
import com.twitter.util.{Future, Promise, Return, Throw}
import scala.collection.mutable

/**
 * A representation of a lazy (and possibly infinite) sequence of asynchronous
 * values. We provide combinators for non-blocking computation over the sequence
 * of values.
 *
 * It is composable with Future, Seq and Option.
 *
 * {{{
 * val ids = Seq(123, 124, ...)
 * val users = fromSeq(ids).flatMap(id => fromFuture(getUser(id)))
 *
 * // Or as a for-comprehension...
 *
 * val users = for {
 *   id <- fromSeq(ids)
 *   user <- fromFuture(getUser(id))
 * } yield user
 * }}}
 *
 * All of its operations are lazy and don't force evaluation, unless otherwise
 * noted.
 *
 * The stream is persistent and can be shared safely by multiple threads.
 */
sealed abstract class AsyncStream[+A] {
  import AsyncStream._

  /**
   * Returns true if there are no elements in the stream.
   */
  def isEmpty: Future[Boolean] = this match {
    case Empty => Future.True
    case Embed(fas) => fas.flatMap(_.isEmpty)
    case _ => Future.False
  }

  /**
   * Returns the head of this stream if not empty.
   */
  def head: Future[Option[A]] = this match {
    case Empty => Future.None
    case FromFuture(fa) => fa.map(Some(_))
    case Cons(fa, _) => fa.map(Some(_))
    case Embed(fas) => fas.flatMap(_.head)
  }

  /**
   * Note: forces the first element of the tail.
   */
  def tail: Future[Option[AsyncStream[A]]] = this match {
    case Empty | FromFuture(_) => Future.None
    case Cons(_, more) => Future.value(Some(more()))
    case Embed(fas) => fas.flatMap(_.tail)
  }

  /**
   * The head and tail of this stream, if not empty. Note the tail thunk which
   * preserves the tail's laziness.
   *
   * {{{
   * empty.uncons     == Future.None
   * (a +:: m).uncons == Future.value(Some(a, () => m))
   * }}}
   */
  def uncons: Future[Option[(A, () => AsyncStream[A])]] = this match {
    case Empty => Future.None
    case FromFuture(fa) => fa.map(a => Some((a, () => empty)))
    case Cons(fa, more) => fa.map(a => Some((a, more)))
    case Embed(fas) => fas.flatMap(_.uncons)
  }

  /**
   * Note: forces the stream. For infinite streams, the future never resolves.
   */
  def foreach(f: A => Unit): Future[Unit] =
    foldLeft(()) { (_, a) => f(a) }

  /**
   * Execute the specified effect as each element of the resulting
   * stream is demanded. This method does '''not''' force the
   * stream. Since the head of the stream is not lazy, the effect will
   * happen to the first item in the stream (if any) right away.
   *
   * The effects will occur as the '''resulting''' stream is demanded
   * and will not occur if the original stream is demanded.
   *
   * This is useful for e.g. counting the number of items that were
   * consumed from a stream by a consuming process, regardless of
   * whether the entire stream is consumed.
   */
  def withEffect(f: A => Unit): AsyncStream[A] =
    map { a => f(a); a }

  /**
   * Maps each element of the stream to a Future action, resolving them from
   * head to tail. The resulting Future completes when the action completes
   * for the last element.
   *
   * Note: forces the stream. For infinite streams, the future never resolves.
   */
  def foreachF(f: A => Future[Unit]): Future[Unit] =
    foldLeftF(()) { (_, a) => f(a) }

  /**
   * Map over this stream with the given concurrency. The items will
   * likely not be processed in order. `concurrencyLevel` specifies an
   * "eagerness factor", and that many actions will be started when this
   * method is called. Forcing the stream will yield the results of
   * completed actions, and will block if none of the actions has yet
   * completed.
   *
   * This method is useful for speeding up calculations over a stream
   * where executing the actions in order is not important. To implement
   * a concurrent fold, first call `mapConcurrent` and then fold that
   * stream. Similarly, concurrent `foreachF` can be achieved by
   * applying `mapConcurrent` and then `foreach`.
   *
   * @param concurrencyLevel: How many actions to execute concurrently. This
   *   many actions will be started and "raced", with the winner being
   *   the next item available in the stream.
   */
  def mapConcurrent[B](concurrencyLevel: Int)(f: A => Future[B]): AsyncStream[B] = {
    def step(pending: Seq[Future[Option[B]]], inputs: () => AsyncStream[A]): Future[AsyncStream[B]] = {
      // We only invoke inputs().uncons if there is space for more work
      // to be started.
      val inputReady =
        if (pending.size >= concurrencyLevel) Future.never
        else inputs().uncons.flatMap {
          case None if pending.nonEmpty => Future.never
          case other => Future.value(Left(other))
        }

      // The inputReady.isDefined check is an optimization to avoid the
      // wasted allocation of calling Future.select when we know that
      // there is more work that is ready to be started.
      val workDone =
        if (pending.isEmpty || inputReady.isDefined) Future.never
        else Future.select(pending).map(Right(_))

      // Wait for either the next input to be ready (Left) or for some pending
      // work to complete (Right).
      inputReady.or(workDone).flatMap {
        case Left(None) =>
          // There is no work pending and we have exhausted the
          // inputs, so we are done.
          Future.value(empty)

        case Left(Some((a, tl))) =>
          // There is available concurrency and a new input is ready,
          // so start the work and add it to `pending`.
          step(f(a).map(Some(_)) +: pending, tl)

        case Right((Throw(t), _)) =>
          // Some work finished with failure, so terminate the stream.
          Future.exception(t)

        case Right((Return(None), newPending)) =>
          // A cons cell was forced, freeing up a spot in `pending`.
          step(newPending, inputs)

        case Right((Return(Some(a)), newPending)) =>
          // Some work finished. Eagerly start the next step, so
          // that all available inputs have work started on them. In
          // the next step, we replace the pending Future for the
          // work that just completed with a Future that will be
          // satisfied by forcing the next element of the result
          // stream. Keeping `pending` full is the mechanism that we
          // use to bound the evaluation depth at `concurrencyLevel`
          // until the stream is forced.
          val cellForced = new Promise[Option[B]]
          val rest = step(cellForced +: newPending, inputs)
          Future.value(mk(a, { cellForced.setValue(None); embed(rest) }))
      }
    }

    if (concurrencyLevel == 1) {
      mapF(f)
    } else if (concurrencyLevel < 1) {
      throw new IllegalArgumentException(
        s"concurrencyLevel must be at least one. got: $concurrencyLevel")
    } else {
      embed(step(Nil, () => this))
    }
  }

  /**
   * Given a predicate `p`, returns the longest prefix (possibly empty) of this
   * stream that satisfes `p`:
   *
   * {{{
   * AsyncStream(1, 2, 3, 4, 1).takeWhile(_ < 3) = AsyncStream(1, 2)
   * AsyncStream(1, 2, 3).takeWhile(_ < 5) = AsyncStream(1, 2, 3)
   * AsyncStream(1, 2, 3).takeWhile(_ < 0) = AsyncStream.empty
   * }}}
   */
  def takeWhile(p: A => Boolean): AsyncStream[A] =
    this match {
      case Empty => empty
      case FromFuture(fa) => Embed(fa.map { a => if (p(a)) this else empty })
      case Cons(fa, more) => Embed(fa.map { a =>
        if (p(a)) Cons(fa, () => more().takeWhile(p))
        else more().takeWhile(p)
      })
      case Embed(fas) => Embed(fas.map(_.takeWhile(p)))
    }

  /**
   * Given a predicate `p` returns the suffix remaining after `takeWhile(p)`:
   *
   * {{{
   * AsyncStream(1, 2, 3, 4, 1).dropWhile(_ < 3) = AsyncStream(3, 4, 1)
   * AsyncStream(1, 2, 3).dropWhile(_ < 5) = AsyncStream.empty
   * AsyncStream(1, 2, 3).dropWhile(_ < 0) = AsyncStream(1, 2, 3)
   * }}}
   */
  def dropWhile(p: A => Boolean): AsyncStream[A] =
    this match {
      case Empty => empty
      case FromFuture(fa) => Embed(fa.map { a => if (p(a)) empty else this })
      case Cons(fa, more) => Embed(fa.map { a =>
        if (p(a)) more().dropWhile(p)
        else Cons(fa, () => more().dropWhile(p))
      })
      case Embed(fas) => Embed(fas.map(_.dropWhile(p)))
    }

  /**
   * Concatenates two streams.
   *
   * Note: If this stream is infinite, we never process the concatenated
   * stream; effectively: m ++ k == m.
   *
   * @see [[concat]] for Java users.
   */
  def ++[B >: A](that: => AsyncStream[B]): AsyncStream[B] =
    concatImpl(() => that)

  protected def concatImpl[B >: A](that: () => AsyncStream[B]): AsyncStream[B] =
    this match {
      case Empty => that()
      case FromFuture(fa) =>
        Cons(fa, that)
      case Cons(fa, more) =>
        Cons(fa, () => more().concatImpl(that))
      case Embed(fas) =>
        Embed(fas.map(_.concatImpl(that)))
    }

  /**
   * @see ++
   */
  def concat[B >: A](that: => AsyncStream[B]): AsyncStream[B] = ++(that)

  /**
   * Map a function `f` over the elements in this stream and concatenate the
   * results.
   */
  def flatMap[B](f: A => AsyncStream[B]): AsyncStream[B] =
    this match {
      case Empty => empty
      case FromFuture(fa) => Embed(fa.map(f))
      case Cons(fa, more) => Embed(fa.map(f)) ++ more().flatMap(f)
      case Embed(fas) => Embed(fas.map(_.flatMap(f)))
    }

  /**
   * `stream.map(f)` is the stream obtained by applying `f` to each element of
   * `stream`.
   */
  def map[B](f: A => B): AsyncStream[B] =
    this match {
      case Empty => empty
      case FromFuture(fa) => FromFuture(fa.map(f))
      case Cons(fa, more) => Cons(fa.map(f), () => more().map(f))
      case Embed(fas) => Embed(fas.map(_.map(f)))
    }

  /**
   * Returns a stream of elements that satisfy the predicate `p`.
   *
   * Note: forces the stream up to the first element which satisfies the
   * predicate. This operation may block forever on infinite streams in which
   * no elements match.
   */
  def filter(p: A => Boolean): AsyncStream[A] =
    this match {
      case Empty => empty
      case FromFuture(fa) =>
        Embed(fa.map { a => if (p(a)) this else empty })
      case Cons(fa, more) =>
        Embed(fa.map { a =>
          if (p(a)) Cons(fa, () => more().filter(p))
          else more().filter(p)
        })
      case Embed(fas) => Embed(fas.map(_.filter(p)))
    }

  /**
   * @see filter
   */
  def withFilter(f: A => Boolean): AsyncStream[A] = filter(f)

  /**
   * Returns the prefix of this stream of length `n`, or the stream itself if
   * `n` is larger than the number of elements in the stream.
   */
  def take(n: Int): AsyncStream[A] =
    if (n < 1) empty else this match {
      case Empty => empty
      case FromFuture(_) => this
      // If we don't handle this case specially, then the next case
      // would return a stream whose full evaluation will evaulate
      // cons.tail.take(0), forcing one more effect than necessary.
      case Cons(fa, _) if n == 1 => FromFuture(fa)
      case Cons(fa, more) => Cons(fa, () => more().take(n - 1))
      case Embed(fas) => Embed(fas.map(_.take(n)))
    }

  /**
   * Returns the suffix of this stream after the first `n` elements, or
   * `AsyncStream.empty` if `n` is larger than the number of elements in the
   * stream.
   *
   * Note: this forces all of the intermediate dropped elements.
   */
  def drop(n: Int): AsyncStream[A] =
    if (n < 1) this else this match {
      case Empty | FromFuture(_) => empty
      case Cons(_, more) => more().drop(n - 1)
      case Embed(fas) => Embed(fas.map(_.drop(n)))
    }

  /**
   * Constructs a new stream by mapping each element of this stream to a
   * Future action, evaluated from head to tail.
   */
  def mapF[B](f: A => Future[B]): AsyncStream[B] =
    this match {
      case Empty => empty
      case FromFuture(fa) => FromFuture(fa.flatMap(f))
      case Cons(fa, more) => Cons(fa.flatMap(f), () => more().mapF(f))
      case Embed(fas) => Embed(fas.map(_.mapF(f)))
    }

  /**
   * Similar to foldLeft, but produces a stream from the result of each
   * successive fold:
   *
   * {{{
   * AsyncStream(1, 2, ...).scanLeft(z)(f) == z +:: f(z, 1) +:: f(f(z, 1), 2) +:: ...
   * }}}
   *
   * Note that for an `AsyncStream as`:
   *
   * {{{
   * as.scanLeft(z)(f).last == as.foldLeft(z)(f)
   * }}}
   *
   * The resulting stream always begins with the initial value `z`,
   * not subject to the fate of the underlying future, i.e.:
   *
   * {{{
   * val never = AsyncStream.fromFuture(Future.never)
   * never.scanLeft(z)(f) == z +:: never // logical equality
   * }}}
   */
  def scanLeft[B](z: B)(f: (B, A) => B): AsyncStream[B] =
    this match {
      case Embed(fas) => Embed(fas.map(_.scanLeft(z)(f)))
      case Empty => FromFuture(Future.value(z))
      case FromFuture(fa) =>
        Cons(Future.value(z), () => FromFuture(fa.map(f(z, _))))
      case Cons(fa, more) =>
        Cons(Future.value(z), () => Embed(fa.map(a => more().scanLeft(f(z, a))(f))))
    }

  /**
   * Applies a binary operator to a start value and all elements of the stream,
   * from head to tail.
   *
   * Note: forces the stream. If the stream is infinite, the resulting future
   * is equivalent to Future.never.
   *
   * @param z the starting value.
   * @param f a binary operator applied to elements of this stream.
   */
  def foldLeft[B](z: B)(f: (B, A) => B): Future[B] = this match {
    case Empty => Future.value(z)
    case FromFuture(fa) => fa.map(f(z, _))
    case Cons(fa, more) => fa.map(f(z, _)).flatMap(more().foldLeft(_)(f))
    case Embed(fas) => fas.flatMap(_.foldLeft(z)(f))
  }

  /**
   * Like `foldLeft`, except that its result is encapsulated in a Future.
   * `foldLeftF` works from head to tail over the stream.
   *
   * Note: forces the stream. If the stream is infinite, the resulting future
   * is equivalent to Future.never.
   *
   * @param z the starting value.
   * @param f a binary operator applied to elements of this stream.
   */
  def foldLeftF[B](z: B)(f: (B, A) => Future[B]): Future[B] =
    this match {
      case Empty => Future.value(z)
      case FromFuture(fa) => fa.flatMap(a => f(z, a))
      case Cons(fa, more) => fa.flatMap(a => f(z, a)).flatMap(b => more().foldLeftF(b)(f))
      case Embed(fas) => fas.flatMap(_.foldLeftF(z)(f))
    }

  /**
   * This is a powerful and expert level function. A fold operation
   * encapsulated in a Future. Like foldRight on normal lists, it replaces
   * every cons with the folded function `f`, and the empty element with `z`.
   *
   * Note: For clarity, we imagine that surrounding a function with backticks
   * (`) allows infix usage.
   *
   * {{{
   *     (1 +:: 2 +:: 3 +:: empty).foldRight(z)(f)
   *   = 1 `f` flatMap (2 `f` flatMap (3 `f` z))
   * }}}
   *
   * Note: if `f` always forces the second parameter, for infinite streams the
   * future never resolves.
   *
   * @param z the parameter that replaces the end of the list.
   * @param f a binary operator applied to elements of this stream. Note that
   * the second paramter is call-by-name.
   */
  def foldRight[B](z: => Future[B])(f: (A, => Future[B]) => Future[B]): Future[B] =
    this match {
      case Empty => z
      case FromFuture(fa) => fa.flatMap(f(_, z))
      case Cons(fa, more) => fa.flatMap(f(_, more().foldRight(z)(f)))
      case Embed(fas) => fas.flatMap(_.foldRight(z)(f))
    }

  /**
   * Concatenate a stream of streams.
   *
   * {{{
   * val a = AsyncStream(1)
   * AsyncStream(a, a, a).flatten = AsyncStream(1, 1, 1)
   * }}}
   *
   * Java users see [[AsyncStream.flattens]].
   */
  def flatten[B](implicit ev: A <:< AsyncStream[B]): AsyncStream[B] =
    this match {
      case Empty => empty
      case FromFuture(fa) => Embed(fa.map(ev))
      case Cons(fa, more) => Embed(fa.map(ev)) ++ more().flatten
      case Embed(fas) => Embed(fas.map(_.flatten))
    }

  /**
   * A Future of the stream realized as a list. This future completes when all
   * elements of the stream are resolved.
   *
   * Note: forces the entire stream. If one asynchronous call fails, it fails
   * the aggregated result.
   */
  def toSeq(): Future[Seq[A]] =
    observe().flatMap {
      case (s, None) => Future.value(s)
      case (_, Some(exc)) => Future.exception(exc)
    }

  /**
   * Attempts to transform the stream into a Seq, and in the case of failure,
   * `observe` returns whatever was able to be transformed up to the point of
   * failure along with the exception. As a result, this Future never fails,
   * and if there are errors they can be accessed via the Option.
   *
   * Note: forces the stream. For infinite streams, the future never resolves.
   */
  def observe(): Future[(Seq[A], Option[Throwable])] = {
    val buf: mutable.ListBuffer[A] = mutable.ListBuffer.empty

    def go(as: AsyncStream[A]): Future[Unit] =
      as match {
        case Empty => Future.Done
        case FromFuture(fa) =>
          fa.flatMap { a =>
            buf += a
            Future.Done
          }
        case Cons(fa, more) =>
          fa.flatMap { a =>
            buf += a
            go(more())
          }
        case Embed(fas) =>
          fas.flatMap(go)
      }

    go(this).transform {
      case Throw(exc) => Future.value(buf.toList -> Some(exc))
      case Return(_) => Future.value(buf.toList -> None)
    }
  }

  /**
   * Buffer the specified number of items from the stream, or all
   * remaining items if the end of the stream is reached before finding
   * that many items. In all cases, this method should act like
   * <http://www.scala-lang.org/api/current/index.html#scala.collection.GenTraversableLike@splitAt(n:Int):(Repr,Repr)>
   * and not cause evaluation of the remainder of the stream.
   */
  private[concurrent] def buffer(n: Int): Future[(Seq[A], () => AsyncStream[A])] = {
    // pre-allocate the buffer, unless it's very large
    val buffer = new mutable.ArrayBuffer[A](n.min(1024))

    def fillBuffer(sizeRemaining: Int)(s: => AsyncStream[A]): Future[(Seq[A], () => AsyncStream[A])] =
      if (sizeRemaining < 1) Future.value((buffer, () => s))
      else s match {
        case Empty => Future.value((buffer, () => s))

        case FromFuture(fa) =>
          fa.flatMap { a =>
            buffer += a
            Future.value((buffer, () => empty))
          }

        case Cons(fa, more) =>
          fa.flatMap { a =>
            buffer += a
            fillBuffer(sizeRemaining - 1)(more())
          }

        case Embed(fas) =>
          fas.flatMap(as => fillBuffer(sizeRemaining)(as))
      }

    fillBuffer(n)(this)
  }

  /**
   * Convert the stream into a stream of groups of items. This
   * facilitates batch processing of the items in the stream. In all
   * cases, this method should act like
   * <http://www.scala-lang.org/api/current/index.html#scala.collection.IterableLike@grouped(size:Int):Iterator[Repr]>
   * The resulting stream will cause this original stream to be
   * evaluated group-wise, so calling this method will cause the first
   * `groupSize` cells to be evaluated (even without examining the
   * result), and accessing each subsequent element will evaluate a
   * further `groupSize` elements from the stream.
   * @param groupSize must be a positive number, or an IllegalArgumentException will be thrown.
   */
  def grouped(groupSize: Int): AsyncStream[Seq[A]] =
    if (groupSize > 1) {
      Embed(buffer(groupSize).map {
        case (items, _) if items.isEmpty => empty
        case (items, remaining) => Cons(Future.value(items), () => remaining().grouped(groupSize))
      })
    } else if (groupSize == 1) {
      map(Seq(_))
    } else {
      throw new IllegalArgumentException(s"groupSize must be positive, but was $groupSize")
    }

  /**
   * Add up the values of all of the elements in this stream. If you
   * hold a reference to the head of the stream, this will cause the
   * entire stream to be held in memory.
   *
   * Note: forces the stream. If the stream is infinite, the resulting future
   * is equivalent to Future.never.
   */
  def sum[B >: A](implicit numeric: Numeric[B]): Future[B] =
    foldLeft(numeric.zero)(numeric.plus)

  /**
   * Eagerly consume the entire stream and return the number of elements
   * that are in it. If you hold a reference to the head of the stream,
   * this will cause the entire stream to be held in memory.
   *
   * Note: forces the stream. If the stream is infinite, the resulting future
   * is equivalent to Future.never.
   */
  def size: Future[Int] = foldLeft(0)((n, _) => n + 1)

  /**
   * Force the entire stream. If you hold a reference to the head of the
   * stream, this will cause the entire stream to be held in memory. The
   * resulting Future will be satisfied once the entire stream has been
   * consumed.
   *
   * This is useful when you want the side-effects of consuming the
   * stream to occur, but do not need to do anything with the resulting
   * values.
   */
  def force: Future[Unit] = foreach { _ => }
}

object AsyncStream {
  private case object Empty extends AsyncStream[Nothing]
  private case class Embed[A](fas: Future[AsyncStream[A]]) extends AsyncStream[A]
  private case class FromFuture[A](fa: Future[A]) extends AsyncStream[A]
  private class Cons[A](val fa: Future[A], next: () => AsyncStream[A])
    extends AsyncStream[A] {
    private[this] lazy val _more: AsyncStream[A] = next()
    def more(): AsyncStream[A] = _more
  }

  object Cons {
    def apply[A](fa: Future[A], next: () => AsyncStream[A]): AsyncStream[A] =
      new Cons(fa, next)

    def unapply[A](as: Cons[A]): Option[(Future[A], () => AsyncStream[A])] =
      // note: pattern match returns the memoized value
      Some((as.fa, () => as.more()))
  }

  implicit class Ops[A](tail: => AsyncStream[A]) {
    /**
     * Right-associative infix Cons constructor.
     *
     * Note: Because of https://issues.scala-lang.org/browse/SI-1980 we can't
     * define +:: as a method on AsyncStream without losing tail laziness.
     */
    def +::[B >: A](b: B): AsyncStream[B] = mk(b, tail)
  }

  def empty[A]: AsyncStream[A] = Empty.asInstanceOf[AsyncStream[A]]

  /**
   * Var-arg constructor for AsyncStreams.
   *
   * {{{
   * AsyncStream(1,2,3)
   * }}}
   *
   * Note: we can't annotate this with varargs because of
   * https://issues.scala-lang.org/browse/SI-8743. This seems to be fixed in a
   * more recent scala version so we will revisit this soon.
   */
  def apply[A](as: A*): AsyncStream[A] = fromSeq(as)

  /**
   * An AsyncStream with a single element.
   */
  def of[A](a: A): AsyncStream[A] = FromFuture(Future.value(a))

  /**
   * Like `Ops.+::`.
   */
  def mk[A](a: A, tail: => AsyncStream[A]): AsyncStream[A] =
    Cons(Future.value(a), () => tail)

  /**
   * Transformation (or lift) from [[Seq]] into `AsyncStream`.
   */
  def fromSeq[A](seq: Seq[A]): AsyncStream[A] = seq match {
    case Nil => empty
    case _ if seq.hasDefiniteSize && seq.tail.isEmpty => of(seq.head)
    case _ => seq.head +:: fromSeq(seq.tail)
  }

  /**
   * Transformation (or lift) from [[Future]] into `AsyncStream`.
   */
  def fromFuture[A](f: Future[A]): AsyncStream[A] =
    FromFuture(f)

  /**
   * Transformation (or lift) from [[Option]] into `AsyncStream`.
   */
  def fromOption[A](o: Option[A]): AsyncStream[A] =
    o match {
      case None => empty
      case Some(a) => of(a)
    }

  /**
   * Transformation (or lift) from [[Reader]] into `AsyncStream[Buf]`, where each [[Buf]]
   * has size up to `chunkSize`.
   */
  def fromReader(r: Reader, chunkSize: Int = Int.MaxValue): AsyncStream[Buf] =
    fromFuture(r.read(chunkSize)).flatMap {
      case Some(buf) => buf +:: fromReader(r, chunkSize)
      case None => AsyncStream.empty[Buf]
    }

  /**
   * Lift from [[Future]] into `AsyncStream` and then flatten.
   */
  private[concurrent] def embed[A](fas: Future[AsyncStream[A]]): AsyncStream[A] =
    Embed(fas)

  /**
   * Java friendly [[AsyncStream.flatten]].
   */
  def flattens[A](as: AsyncStream[AsyncStream[A]]): AsyncStream[A] =
    as.flatten
}
