package com.twitter.util

import java.util.{List => JList, Map => JMap}
import scala.collection.JavaConverters.{
  asScalaBufferConverter,
  mapAsJavaMapConverter,
  mapAsScalaMapConverter,
  seqAsJavaListConverter
}

/**
 * Twitter Future utility methods for ease of use from java
 */
object Futures {
  /* The following joins are generated with this code:
  scala -e '
  val meths = for (end <- ''b'' to ''v''; ps = ''a'' to end) yield
      """/**
   * Join %d futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
def join[%s](%s): Future[(%s)] = join(Seq(%s)).map { _ => (%s) }""".format(
        ps.size,
        ps.map (_.toUpper) mkString ",",
        ps.map(p => "%c: Future[%c]".format(p, p.toUpper)) mkString ",",
        ps.map(_.toUpper) mkString ",",
        ps mkString ",",
        ps.map(p => "Await.result("+p+")") mkString ","
      )

  meths foreach println
  '
   */

  /**
   * Join 2 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B](a: Future[A], b: Future[B]): Future[(A, B)] = Future.join(Seq(a, b)).map { _ =>
    (Await.result(a), Await.result(b))
  }

  /**
   * Join 3 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C](a: Future[A], b: Future[B], c: Future[C]): Future[(A, B, C)] =
    Future.join(Seq(a, b, c)).map { _ =>
      (Await.result(a), Await.result(b), Await.result(c))
    }

  /**
   * Join 4 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D]
  ): Future[(A, B, C, D)] = Future.join(Seq(a, b, c, d)).map { _ =>
    (Await.result(a), Await.result(b), Await.result(c), Await.result(d))
  }

  /**
   * Join 5 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E]
  ): Future[(A, B, C, D, E)] = Future.join(Seq(a, b, c, d, e)).map { _ =>
    (Await.result(a), Await.result(b), Await.result(c), Await.result(d), Await.result(e))
  }

  /**
   * Join 6 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F]
  ): Future[(A, B, C, D, E, F)] = Future.join(Seq(a, b, c, d, e, f)).map { _ =>
    (
      Await.result(a),
      Await.result(b),
      Await.result(c),
      Await.result(d),
      Await.result(e),
      Await.result(f)
    )
  }

  /**
   * Join 7 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F],
    g: Future[G]
  ): Future[(A, B, C, D, E, F, G)] = Future.join(Seq(a, b, c, d, e, f, g)).map { _ =>
    (
      Await.result(a),
      Await.result(b),
      Await.result(c),
      Await.result(d),
      Await.result(e),
      Await.result(f),
      Await.result(g)
    )
  }

  /**
   * Join 8 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F],
    g: Future[G],
    h: Future[H]
  ): Future[(A, B, C, D, E, F, G, H)] = Future.join(Seq(a, b, c, d, e, f, g, h)).map { _ =>
    (
      Await.result(a),
      Await.result(b),
      Await.result(c),
      Await.result(d),
      Await.result(e),
      Await.result(f),
      Await.result(g),
      Await.result(h)
    )
  }

  /**
   * Join 9 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F],
    g: Future[G],
    h: Future[H],
    i: Future[I]
  ): Future[(A, B, C, D, E, F, G, H, I)] = Future.join(Seq(a, b, c, d, e, f, g, h, i)).map { _ =>
    (
      Await.result(a),
      Await.result(b),
      Await.result(c),
      Await.result(d),
      Await.result(e),
      Await.result(f),
      Await.result(g),
      Await.result(h),
      Await.result(i)
    )
  }

  /**
   * Join 10 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F],
    g: Future[G],
    h: Future[H],
    i: Future[I],
    j: Future[J]
  ): Future[(A, B, C, D, E, F, G, H, I, J)] = Future.join(Seq(a, b, c, d, e, f, g, h, i, j)).map {
    _ =>
      (
        Await.result(a),
        Await.result(b),
        Await.result(c),
        Await.result(d),
        Await.result(e),
        Await.result(f),
        Await.result(g),
        Await.result(h),
        Await.result(i),
        Await.result(j)
      )
  }

  /**
   * Join 11 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F],
    g: Future[G],
    h: Future[H],
    i: Future[I],
    j: Future[J],
    k: Future[K]
  ): Future[(A, B, C, D, E, F, G, H, I, J, K)] =
    Future.join(Seq(a, b, c, d, e, f, g, h, i, j, k)).map { _ =>
      (
        Await.result(a),
        Await.result(b),
        Await.result(c),
        Await.result(d),
        Await.result(e),
        Await.result(f),
        Await.result(g),
        Await.result(h),
        Await.result(i),
        Await.result(j),
        Await.result(k)
      )
    }

  /**
   * Join 12 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F],
    g: Future[G],
    h: Future[H],
    i: Future[I],
    j: Future[J],
    k: Future[K],
    l: Future[L]
  ): Future[(A, B, C, D, E, F, G, H, I, J, K, L)] =
    Future.join(Seq(a, b, c, d, e, f, g, h, i, j, k, l)).map { _ =>
      (
        Await.result(a),
        Await.result(b),
        Await.result(c),
        Await.result(d),
        Await.result(e),
        Await.result(f),
        Await.result(g),
        Await.result(h),
        Await.result(i),
        Await.result(j),
        Await.result(k),
        Await.result(l)
      )
    }

  /**
   * Join 13 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F],
    g: Future[G],
    h: Future[H],
    i: Future[I],
    j: Future[J],
    k: Future[K],
    l: Future[L],
    m: Future[M]
  ): Future[(A, B, C, D, E, F, G, H, I, J, K, L, M)] =
    Future.join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m)).map { _ =>
      (
        Await.result(a),
        Await.result(b),
        Await.result(c),
        Await.result(d),
        Await.result(e),
        Await.result(f),
        Await.result(g),
        Await.result(h),
        Await.result(i),
        Await.result(j),
        Await.result(k),
        Await.result(l),
        Await.result(m)
      )
    }

  /**
   * Join 14 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F],
    g: Future[G],
    h: Future[H],
    i: Future[I],
    j: Future[J],
    k: Future[K],
    l: Future[L],
    m: Future[M],
    n: Future[N]
  ): Future[(A, B, C, D, E, F, G, H, I, J, K, L, M, N)] =
    Future.join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n)).map { _ =>
      (
        Await.result(a),
        Await.result(b),
        Await.result(c),
        Await.result(d),
        Await.result(e),
        Await.result(f),
        Await.result(g),
        Await.result(h),
        Await.result(i),
        Await.result(j),
        Await.result(k),
        Await.result(l),
        Await.result(m),
        Await.result(n)
      )
    }

  /**
   * Join 15 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F],
    g: Future[G],
    h: Future[H],
    i: Future[I],
    j: Future[J],
    k: Future[K],
    l: Future[L],
    m: Future[M],
    n: Future[N],
    o: Future[O]
  ): Future[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] =
    Future.join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o)).map { _ =>
      (
        Await.result(a),
        Await.result(b),
        Await.result(c),
        Await.result(d),
        Await.result(e),
        Await.result(f),
        Await.result(g),
        Await.result(h),
        Await.result(i),
        Await.result(j),
        Await.result(k),
        Await.result(l),
        Await.result(m),
        Await.result(n),
        Await.result(o)
      )
    }

  /**
   * Join 16 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F],
    g: Future[G],
    h: Future[H],
    i: Future[I],
    j: Future[J],
    k: Future[K],
    l: Future[L],
    m: Future[M],
    n: Future[N],
    o: Future[O],
    p: Future[P]
  ): Future[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)] =
    Future.join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p)).map { _ =>
      (
        Await.result(a),
        Await.result(b),
        Await.result(c),
        Await.result(d),
        Await.result(e),
        Await.result(f),
        Await.result(g),
        Await.result(h),
        Await.result(i),
        Await.result(j),
        Await.result(k),
        Await.result(l),
        Await.result(m),
        Await.result(n),
        Await.result(o),
        Await.result(p)
      )
    }

  /**
   * Join 17 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F],
    g: Future[G],
    h: Future[H],
    i: Future[I],
    j: Future[J],
    k: Future[K],
    l: Future[L],
    m: Future[M],
    n: Future[N],
    o: Future[O],
    p: Future[P],
    q: Future[Q]
  ): Future[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)] =
    Future.join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q)).map { _ =>
      (
        Await.result(a),
        Await.result(b),
        Await.result(c),
        Await.result(d),
        Await.result(e),
        Await.result(f),
        Await.result(g),
        Await.result(h),
        Await.result(i),
        Await.result(j),
        Await.result(k),
        Await.result(l),
        Await.result(m),
        Await.result(n),
        Await.result(o),
        Await.result(p),
        Await.result(q)
      )
    }

  /**
   * Join 18 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F],
    g: Future[G],
    h: Future[H],
    i: Future[I],
    j: Future[J],
    k: Future[K],
    l: Future[L],
    m: Future[M],
    n: Future[N],
    o: Future[O],
    p: Future[P],
    q: Future[Q],
    r: Future[R]
  ): Future[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)] =
    Future.join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r)).map { _ =>
      (
        Await.result(a),
        Await.result(b),
        Await.result(c),
        Await.result(d),
        Await.result(e),
        Await.result(f),
        Await.result(g),
        Await.result(h),
        Await.result(i),
        Await.result(j),
        Await.result(k),
        Await.result(l),
        Await.result(m),
        Await.result(n),
        Await.result(o),
        Await.result(p),
        Await.result(q),
        Await.result(r)
      )
    }

  /**
   * Join 19 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F],
    g: Future[G],
    h: Future[H],
    i: Future[I],
    j: Future[J],
    k: Future[K],
    l: Future[L],
    m: Future[M],
    n: Future[N],
    o: Future[O],
    p: Future[P],
    q: Future[Q],
    r: Future[R],
    s: Future[S]
  ): Future[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)] =
    Future.join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s)).map { _ =>
      (
        Await.result(a),
        Await.result(b),
        Await.result(c),
        Await.result(d),
        Await.result(e),
        Await.result(f),
        Await.result(g),
        Await.result(h),
        Await.result(i),
        Await.result(j),
        Await.result(k),
        Await.result(l),
        Await.result(m),
        Await.result(n),
        Await.result(o),
        Await.result(p),
        Await.result(q),
        Await.result(r),
        Await.result(s)
      )
    }

  /**
   * Join 20 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F],
    g: Future[G],
    h: Future[H],
    i: Future[I],
    j: Future[J],
    k: Future[K],
    l: Future[L],
    m: Future[M],
    n: Future[N],
    o: Future[O],
    p: Future[P],
    q: Future[Q],
    r: Future[R],
    s: Future[S],
    t: Future[T]
  ): Future[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)] =
    Future.join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t)).map { _ =>
      (
        Await.result(a),
        Await.result(b),
        Await.result(c),
        Await.result(d),
        Await.result(e),
        Await.result(f),
        Await.result(g),
        Await.result(h),
        Await.result(i),
        Await.result(j),
        Await.result(k),
        Await.result(l),
        Await.result(m),
        Await.result(n),
        Await.result(o),
        Await.result(p),
        Await.result(q),
        Await.result(r),
        Await.result(s),
        Await.result(t)
      )
    }

  /**
   * Join 21 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F],
    g: Future[G],
    h: Future[H],
    i: Future[I],
    j: Future[J],
    k: Future[K],
    l: Future[L],
    m: Future[M],
    n: Future[N],
    o: Future[O],
    p: Future[P],
    q: Future[Q],
    r: Future[R],
    s: Future[S],
    t: Future[T],
    u: Future[U]
  ): Future[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)] =
    Future.join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u)).map { _ =>
      (
        Await.result(a),
        Await.result(b),
        Await.result(c),
        Await.result(d),
        Await.result(e),
        Await.result(f),
        Await.result(g),
        Await.result(h),
        Await.result(i),
        Await.result(j),
        Await.result(k),
        Await.result(l),
        Await.result(m),
        Await.result(n),
        Await.result(o),
        Await.result(p),
        Await.result(q),
        Await.result(r),
        Await.result(s),
        Await.result(t),
        Await.result(u)
      )
    }

  /**
   * Join 22 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V](
    a: Future[A],
    b: Future[B],
    c: Future[C],
    d: Future[D],
    e: Future[E],
    f: Future[F],
    g: Future[G],
    h: Future[H],
    i: Future[I],
    j: Future[J],
    k: Future[K],
    l: Future[L],
    m: Future[M],
    n: Future[N],
    o: Future[O],
    p: Future[P],
    q: Future[Q],
    r: Future[R],
    s: Future[S],
    t: Future[T],
    u: Future[U],
    v: Future[V]
  ): Future[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)] =
    Future.join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v)).map { _ =>
      (
        Await.result(a),
        Await.result(b),
        Await.result(c),
        Await.result(d),
        Await.result(e),
        Await.result(f),
        Await.result(g),
        Await.result(h),
        Await.result(i),
        Await.result(j),
        Await.result(k),
        Await.result(l),
        Await.result(m),
        Await.result(n),
        Await.result(o),
        Await.result(p),
        Await.result(q),
        Await.result(r),
        Await.result(s),
        Await.result(t),
        Await.result(u),
        Await.result(v)
      )
    }

  /**
   * Take a sequence of Futures, wait till they all complete
   * successfully.  The future fails immediately if any of the joined
   * Futures do, mimicking the semantics of exceptions.
   *
   * @param fs a java.util.List of Futures
   * @return a Future[Unit] whose value is populated when all of the fs return.
   */
  def join[A](fs: JList[Future[A]]): Future[Unit] = Future.join(fs.asScala)

  /**
   * "Select" off the first future to be satisfied.  Return this as a
   * result, with the remainder of the Futures as a sequence.
   *
   * @param fs a java.util.List
   * @return a `Future[Tuple2[Try[A], java.util.List[Future[A]]]]` representing the first future
   * to be satisfied and the rest of the futures.
   */
  def select[A](fs: JList[Future[A]]): Future[(Try[A], JList[Future[A]])] =
    Future.select(fs.asScala).map {
      case (first, rest) =>
        (first, rest.asJava)
    }

  /**
   * Collect the results from the given futures into a new future of
   * Seq[A]. If one or more of the given futures is exceptional, the resulting
   * future result will be the first exception encountered.
   *
   * @param fs a java.util.List of Futures
   * @return a `Future[java.util.List[A]]` containing the collected values from fs.
   */
  def collect[A](fs: JList[Future[A]]): Future[JList[A]] =
    Future.collect(fs.asScala).map { _.asJava }

  /**
   * Collect the results from the given map `fs` of futures into a new future
   * of map. If one or more of the given Futures is exceptional, the resulting Future
   * result will the first exception encountered.
   */
  def collect[A, B](fs: JMap[A, Future[B]]): Future[JMap[A, B]] =
    Future.collect(fs.asScala.toMap).map { _.asJava }

  /**
   * Collect the results from the given futures into a new future of List[Try[A]]
   *
   * @param fs a java.util.List of Futures
   * @return a `Future[java.util.List[Try[A]]]` containing the collected values from fs.
   */
  def collectToTry[A](fs: JList[Future[A]]): Future[JList[Try[A]]] =
    Future.collectToTry(fs.asScala).map { _.asJava }

  /**
   * Flattens a nested future.  Same as ffa.flatten, but easier to call from Java.
   */
  def flatten[A](ffa: Future[Future[A]]): Future[A] = ffa.flatten

  /**
   * Lowers a Future[Try[T]] into a Future[T].
   */
  def lowerFromTry[T](f: Future[Try[T]]): Future[T] = f.lowerFromTry
}
