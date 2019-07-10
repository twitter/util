package com.twitter.util

import com.twitter.concurrent.{Offer, Tx}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import java.util.concurrent.{CancellationException, TimeUnit, Future => JavaFuture}
import java.util.{List => JList}
import scala.collection.mutable
import scala.runtime.NonLocalReturnControl
import scala.util.control.NoStackTrace

/**
 * @see [[Futures]] for Java-friendly APIs.
 * @see The [[https://twitter.github.io/finagle/guide/Futures.html user guide]]
 *      on concurrent programming with Futures.
 */
object Future {
  val DEFAULT_TIMEOUT: Duration = Duration.Top

  /** A successfully satisfied constant `Unit`-typed `Future` of `()` */
  val Unit: Future[Unit] = const(Return.Unit)

  /** A successfully satisfied constant `Unit`-typed `Future` of `()` */
  val Done: Future[Unit] = Unit

  /**
   * A successfully satisfied constant `Future` of `Void`-type.
   * Can be useful for Java programmers.
   */
  val Void: Future[Void] = value[Void](null: Void)

  /** A successfully satisfied constant `Future` of `None` */
  val None: Future[Option[Nothing]] = new ConstFuture(Return.None)

  /** A successfully satisfied constant `Future` of `Nil` */
  val Nil: Future[Seq[Nothing]] = new ConstFuture(Return.Nil)

  /** A successfully satisfied constant `Future` of `true` */
  val True: Future[Boolean] = new ConstFuture(Return.True)

  /** A successfully satisfied constant `Future` of `false` */
  val False: Future[Boolean] = new ConstFuture(Return.False)

  private val SomeReturnUnit = Some(Return.Unit)
  private val NotApplied: Future[Nothing] = new NoFuture
  private val AlwaysNotApplied: Any => Future[Nothing] = scala.Function.const(NotApplied)
  private val tryToUnit: Try[Any] => Try[Unit] = {
    case t: Return[_] => Try.Unit
    case t => t.asInstanceOf[Try[Unit]]
  }
  private val tryToVoid: Try[Any] => Try[Void] = {
    case t: Return[_] => Try.Void
    case t => t.asInstanceOf[Try[Void]]
  }
  private val AlwaysMasked: PartialFunction[Throwable, Boolean] = { case _ => true }

  private val toTuple2Instance: (Any, Any) => (Any, Any) = Tuple2.apply
  private def toTuple2[A, B]: (A, B) => (A, B) = toTuple2Instance.asInstanceOf[(A, B) => (A, B)]
  private val liftToTryInstance: Any => Try[Any] = Return(_)
  private def liftToTry[T]: T => Try[T] = liftToTryInstance.asInstanceOf[T => Try[T]]

  private val flattenTryInstance: Try[Try[Any]] => Try[Any] = _.flatten
  private def flattenTry[A, T](implicit ev: A => Try[T]): Try[A] => Try[T] =
    flattenTryInstance.asInstanceOf[Try[A] => Try[T]]

  private val toTxInstance: Try[Any] => Try[Tx[Try[Any]]] =
    res => {
      val tx = new Tx[Try[Any]] {
        def ack(): Future[Tx.Result[Try[Any]]] = Future.value(Tx.Commit(res))
        def nack(): Unit = ()
      }

      Return(tx)
    }
  private def toTx[A]: Try[A] => Try[Tx[Try[A]]] =
    toTxInstance.asInstanceOf[Try[A] => Try[Tx[Try[A]]]]

  private val emptySeqInstance: Future[Seq[Any]] = Future.value(Seq.empty)
  private def emptySeq[A]: Future[Seq[A]] = emptySeqInstance.asInstanceOf[Future[Seq[A]]]

  private val emptyMapInstance: Future[Map[Any, Any]] = Future.value(Map.empty[Any, Any])
  private def emptyMap[A, B]: Future[Map[A, B]] = emptyMapInstance.asInstanceOf[Future[Map[A, B]]]

  // Exception used to raise on Futures.
  private[this] val RaiseException = new Exception with NoStackTrace
  @inline private final def raiseException = RaiseException

  /**
   * A failed `Future` analogous to [[Predef.???]].
   */
  def ??? : Future[Nothing] =
    Future.exception(new NotImplementedError("an implementation is missing"))

  /**
   * Creates a satisfied `Future` from a [[Try]].
   *
   * @see [[value]] for creation from a constant value.
   * @see [[apply]] for creation from a `Function`.
   * @see [[exception]] for creation from a `Throwable`.
   */
  def const[A](result: Try[A]): Future[A] = new ConstFuture[A](result)

  /**
   * Creates a successful satisfied `Future` from the value `a`.
   *
   * @see [[const]] for creation from a [[Try]]
   * @see [[apply]] for creation from a `Function`.
   * @see [[exception]] for creation from a `Throwable`.
   */
  def value[A](a: A): Future[A] = const[A](Return(a))

  /**
   * Creates a failed satisfied `Future`.
   *
   * For example, {{{Future.exception(new Exception("boo"))}}}.
   *
   * @see [[apply]] for creation from a `Function`.
   */
  def exception[A](e: Throwable): Future[A] = const[A](Throw(e))

  /**
   * A `Future` that can never be satisfied.
   */
  val never: Future[Nothing] = new NoFuture

  /**
   * A `Unit`-typed `Future` that is satisfied after `howlong`.
   */
  def sleep(howlong: Duration)(implicit timer: Timer): Future[Unit] =
    if (howlong <= Duration.Zero) Future.Done
    else if (howlong == Duration.Top) Future.never
    else
      new Promise[Unit] with Promise.InterruptHandler with (() => Unit) {
        private[this] val task: TimerTask = timer.schedule(howlong.fromNow)(this())

        // Timer task.
        def apply(): Unit = setDone()

        protected def onInterrupt(t: Throwable): Unit =
          if (updateIfEmpty(Throw(t))) task.cancel()
      }

  /**
   * Creates a satisfied `Future` from the result of running `a`.
   *
   * If the result of `a` is a non-fatal exception,
   * this will result in a failed `Future`. Otherwise, the result
   * is a successfully satisfied `Future`.
   *
   * @note that `a` is executed in the calling thread and as such
   *       some care must be taken with blocking code.
   */
  def apply[A](a: => A): Future[A] =
    try {
      const(Try(a))
    } catch {
      case nlrc: NonLocalReturnControl[_] => Future.exception(new FutureNonLocalReturnControl(nlrc))
    }

  def unapply[A](f: Future[A]): Option[Try[A]] = f.poll

  // The thread-safety for `outer` is guaranteed by synchronizing on `this`.
  private[this] final class MonitoredPromise[A](private[this] var outer: Promise[A])
      extends Monitor
      with (Try[A] => Unit) {

    // We're serializing access to the underlying promise such that whatever (failure, success)
    // runs it first also nulls it out. We have to stop referencing `outer` from this monitor
    // given it might be captured by one of the pending promises.
    private[this] def run(): Promise[A] = synchronized {
      val p = outer
      outer = null
      p
    }

    // Function1.apply
    def apply(ta: Try[A]): Unit = {
      val p = run()
      if (p != null) p.update(ta)
    }

    // Monitor.handle
    def handle(t: Throwable): Boolean = {
      val p = run()
      if (p == null) false
      else {
        p.raise(t)
        p.setException(t)
        true
      }
    }
  }

  /**
   * Run the computation `mkFuture` while installing a [[Monitor]] that
   * translates any exception thrown into an encoded one.  If an
   * exception is thrown anywhere, the underlying computation is
   * interrupted with that exception.
   *
   * This function is usually called to wrap a computation that
   * returns a Future (f0) whose value is satisfied by the invocation
   * of an onSuccess/onFailure/ensure callbacks of another future
   * (f1).  If an exception happens in the callbacks on f1, f0 is
   * never satisfied.  In this example, `Future.monitored { f1
   * onSuccess g; f0 }` will cancel f0 so that f0 never hangs.
   */
  def monitored[A](mkFuture: => Future[A]): Future[A] = {
    val p = new Promise[A]
    val monitored = new MonitoredPromise(p)

    // This essentially simulates MonitoredPromise.apply { ... } but w/o closure allocations.
    val saved = Monitor.getOption
    try {
      Monitor.set(monitored)
      val f = mkFuture
      p.forwardInterruptsTo(f)
      f.respond(monitored)
    } catch {
      case t: Throwable => if (!monitored.handle(t)) throw t
    } finally { Monitor.setOption(saved) }

    p
  }

  // Used by `Future.join`.
  // We would like to be able to mix in both PartialFunction[Throwable, Unit]
  // for the interrupt and handler and Function1[Try[A], Unit] for the respond handler,
  // but because these are both Function1 of different types, this cannot be done. Because
  // the respond handler is invoked for each Future in the sequence, and the interrupt handler is
  // only set once, we prefer to mix in Function1.
  private[this] class JoinPromise[A](fs: Seq[Future[A]], size: Int)
      extends Promise[Unit]
      with (Try[A] => Unit) {

    private[this] val count = new AtomicInteger(size)

    // Handler for `Future.respond`, invoked in `Future.join`
    def apply(value: Try[A]): Unit = value match {
      case Return(_) =>
        if (count.decrementAndGet() == 0)
          update(Return.Unit)
      case t @ Throw(_) =>
        updateIfEmpty(t.cast[Unit])
    }

    setInterruptHandler {
      case t: Throwable =>
        val it = fs.iterator
        while (it.hasNext) {
          it.next().raise(t)
        }
    }
  }

  /**
   * Creates a `Future` that is satisfied when all futures in `fs`
   * are successfully satisfied. If any of the futures in `fs` fail,
   * the returned `Future` is immediately satisfied by that failure.
   *
   * @param fs a sequence of Futures
   *
   * @see [[Futures.join]] for a Java friendly API.
   * @see [[collect]] if you want to be able to see the results of each `Future`.
   * @see [[collectToTry]] if you want to be able to see the results of each
   *     `Future` regardless of if they succeed or fail.
   */
  def join[A](fs: Seq[Future[A]]): Future[Unit] = {
    if (fs.isEmpty) Future.Unit
    else {
      val size = fs.size
      if (size == 1) fs.head.unit
      else {
        val result = new JoinPromise[A](fs, size)
        val iterator = fs.iterator
        while (iterator.hasNext && !result.isDefined) iterator.next().respond(result)

        result
      }
    }
  }

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
        ps map (_.toUpper) mkString ",",
        ps map(p => "%c: Future[%c]".format(p, p.toUpper)) mkString ",",
        ps map (_.toUpper) mkString ",",
        ps mkString ",",
        ps map(p => "Await.result("+p+")") mkString ","
      )

  meths foreach println
  '
   */

  /**
   * Join 2 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B](a: Future[A], b: Future[B]): Future[(A, B)] = join(Seq(a, b)).map { _ =>
    (Await.result(a), Await.result(b))
  }

  /**
   * Join 3 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A, B, C](a: Future[A], b: Future[B], c: Future[C]): Future[(A, B, C)] =
    join(Seq(a, b, c)).map { _ =>
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
  ): Future[(A, B, C, D)] = join(Seq(a, b, c, d)).map { _ =>
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
  ): Future[(A, B, C, D, E)] = join(Seq(a, b, c, d, e)).map { _ =>
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
  ): Future[(A, B, C, D, E, F)] = join(Seq(a, b, c, d, e, f)).map { _ =>
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
  ): Future[(A, B, C, D, E, F, G)] = join(Seq(a, b, c, d, e, f, g)).map { _ =>
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
  ): Future[(A, B, C, D, E, F, G, H)] = join(Seq(a, b, c, d, e, f, g, h)).map { _ =>
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
  ): Future[(A, B, C, D, E, F, G, H, I)] = join(Seq(a, b, c, d, e, f, g, h, i)).map { _ =>
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
  ): Future[(A, B, C, D, E, F, G, H, I, J)] = join(Seq(a, b, c, d, e, f, g, h, i, j)).map { _ =>
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
  ): Future[(A, B, C, D, E, F, G, H, I, J, K)] = join(Seq(a, b, c, d, e, f, g, h, i, j, k)).map {
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
    join(Seq(a, b, c, d, e, f, g, h, i, j, k, l)).map { _ =>
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
    join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m)).map { _ =>
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
    join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n)).map { _ =>
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
    join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o)).map { _ =>
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
    join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p)).map { _ =>
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
    join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q)).map { _ =>
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
    join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r)).map { _ =>
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
    join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s)).map { _ =>
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
    join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t)).map { _ =>
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
    join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u)).map { _ =>
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
    join(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v)).map { _ =>
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
   * Creates a `Future` that is satisfied when all futures in `fs`
   * are successfully satisfied. If any of the futures in `fs` fail,
   * the returned `Future` is immediately satisfied by that failure.
   *
   * TODO: This method should be deprecated in favour of `Futures.join()`.
   *
   * @param fs a java.util.List of Futures
   *
   * @see [[Futures.join]] for a Java friendly API.
   * @see [[collect]] if you want to be able to see the results of each `Future`.
   * @see [[collectToTry]] if you want to be able to see the results of each
   *     `Future` regardless of if they succeed or fail.
   */
  def join[A](fs: JList[Future[A]]): Future[Unit] = Futures.join(fs)

  /**
   * Take a sequence and sequentially apply a function `f` to each item.
   * Then return all future results `as` as a single `Future[Seq[_]]`.
   *
   * If during execution any `f` is satisfied `as` as a failure ([[Future.exception]])
   * then that failed Future will be returned and the remaining elements of `as`
   * will not be processed.
   *
   * usage:
   *  {{{
   *    // will return a Future of `Seq(2, 3, 4)`
   *    Future.traverseSequentially(Seq(1, 2, 3)) { i =>
   *      Future.value(i + 1)
   *    }
   *  }}}
   *
   * @param `as` a sequence of `A` that will have `f` applied to each item sequentially
   * @return a `Future[Seq[B]]` containing the results of `f` being applied to every item in `as`
   */
  def traverseSequentially[A, B](as: Seq[A])(f: A => Future[B]): Future[Seq[B]] =
    as.foldLeft(emptySeq[B]) { (resultsFuture, nextItem) =>
      for {
        results <- resultsFuture
        nextResult <- f(nextItem)
      } yield results :+ nextResult
    }

  private[this] final class CollectPromise[A](fs: Seq[Future[A]])
      extends Promise[Seq[A]]
      with Promise.InterruptHandler {

    private[this] val results = new mutable.ArraySeq[A](fs.size)
    private[this] val count = new AtomicInteger(results.size)

    // Respond handler. It's safe to write into different array cells concurrently.
    // We guarantee the thread writing a last value will observe all previous writes
    // (from other threads) given the happens-before relation between them (through
    // the atomic counter).
    def collectTo(index: Int): Try[A] => Unit = {
      case Return(a) =>
        results(index) = a
        if (count.decrementAndGet() == 0) setValue(results)
      case t @ Throw(_) =>
        updateIfEmpty(t.cast[Seq[A]])
    }

    protected def onInterrupt(t: Throwable): Unit = {
      val it = fs.iterator
      while (it.hasNext) {
        it.next().raise(t)
      }
    }
  }

  /**
   * Collect the results from the given futures into a new future of
   * `Seq[A]`. If one or more of the given Futures is exceptional, the resulting
   * Future result will be the first exception encountered.
   *
   * @param fs a sequence of Futures
   * @return a `Future[Seq[A]]` containing the collected values from fs.
   *
   * @see [[collectToTry]] if you want to be able to see the results of each
   *     `Future` regardless of if they succeed or fail.
   * @see [[join]] if you are not interested in the results of the individual
   *     `Futures`, only when they are complete.
   */
  def collect[A](fs: Seq[Future[A]]): Future[Seq[A]] =
    if (fs.isEmpty) emptySeq
    else {
      val result = new CollectPromise[A](fs)
      var i = 0
      val it = fs.iterator

      while (it.hasNext && !result.isDefined) {
        it.next().respond(result.collectTo(i))
        i += 1
      }

      result
    }

  /**
   * Collect the results from the given map `fs` of futures into a new future
   * of map. If one or more of the given Futures is exceptional, the resulting Future
   * result will the first exception encountered.
   *
   * @param fs a map of Futures
   * @return a `Future[Map[A, B]]` containing the collected values from fs
   */
  def collect[A, B](fs: Map[A, Future[B]]): Future[Map[A, B]] =
    if (fs.isEmpty) emptyMap
    else {
      val (keys, values) = fs.toSeq.unzip
      Future.collect(values).map { seq =>
        keys.zip(seq)(scala.collection.breakOut): Map[A, B]
      }
    }

  /**
   * Collect the results from the given futures into a new future of
   * Seq[A]. If one or more of the given futures is exceptional, the resulting
   * future result will be the first exception encountered.
   *
   * TODO: This method should be deprecated in favour of `Futures.collect()`.
   *
   * @param fs a java.util.List of Futures
   * @return a `Future[java.util.List[A]]` containing the collected values from fs.
   */
  def collect[A](fs: JList[Future[A]]): Future[JList[A]] = Futures.collect(fs)

  /**
   * Collect the results from the given futures into a new future of `Seq[Try[A]]`.
   *
   * The returned `Future` is satisfied when all of the given `Futures`
   * have been satisfied.
   *
   * @param fs a sequence of Futures
   * @return a `Future[Seq[Try[A]]]` containing the collected values from fs.
   */
  def collectToTry[A](fs: Seq[Future[A]]): Future[Seq[Try[A]]] =
    Future.collect {
      val seq = new mutable.ArrayBuffer[Future[Try[A]]](fs.size)
      val iterator = fs.iterator
      while (iterator.hasNext) seq += iterator.next().liftToTry
      seq
    }

  /**
   * Collect the results from the given futures into a new future of java.util.List[Try[A]].
   *
   * TODO: This method should be deprecated in favour of `Futures.collectToTry()`.
   *
   * @param fs a java.util.List of Futures
   * @return a `Future[java.util.List[Try[A]]]` containing the collected values from fs.
   */
  def collectToTry[A](fs: JList[Future[A]]): Future[JList[Try[A]]] = Futures.collectToTry(fs)

  /**
   * "Select" off the first future to be satisfied.  Return this as a
   * result, with the remainder of the Futures as a sequence.
   *
   * @param fs the futures to select from. Must not be empty.
   *
   * @see [[selectIndex]] which can be more performant in some situations.
   */
  def select[A](fs: Seq[Future[A]]): Future[(Try[A], Seq[Future[A]])] =
    if (fs.isEmpty) {
      Future.exception(new IllegalArgumentException("empty future list"))
    } else {
      val p = Promise.interrupts[(Try[A], Seq[Future[A]])](fs: _*)
      val size = fs.size

      val as = {
        val array = new Array[(Promise[A] with Promise.Detachable, Future[A])](size)
        val iterator = fs.iterator
        var i = 0
        while (iterator.hasNext) {
          val f = iterator.next()
          array(i) = Promise.attached(f) -> f
          i += 1
        }
        array
      }

      var i = 0
      while (i < size) {
        val tuple = as(i)
        val a = tuple._1
        val f = tuple._2
        a.respond { t =>
          if (!p.isDefined) {
            val filtered = {
              val seq = new mutable.ArrayBuffer[Future[A]](size - 1)
              var j = 0
              while (j < size) {
                val (_, fi) = as(j)
                if (fi ne f)
                  seq += fi
                j += 1
              }
              seq
            }
            p.updateIfEmpty(Return(t -> filtered))

            var j = 0
            while (j < size) {
              as(j)._1.detach()
              j += 1
            }
          }
        }
        i += 1
      }
      p
    }

  /**
   * Select the index into `fs` of the first future to be satisfied.
   *
   * @param fs cannot be empty
   *
   * @see [[select]] which can be an easier API to use.
   */
  def selectIndex[A](fs: IndexedSeq[Future[A]]): Future[Int] =
    if (fs.isEmpty) {
      Future.exception(new IllegalArgumentException("empty future list"))
    } else {
      val p = Promise.interrupts[Int](fs: _*)
      val size = fs.size
      val as = {
        val array = new Array[Promise[A] with Promise.Detachable](size)
        var i = 0
        while (i < fs.size) {
          array(i) = Promise.attached(fs(i))
          i += 1
        }
        array
      }
      var i = 0
      while (i < size) {
        val ii = i
        as(ii) ensure {
          if (!p.isDefined && p.updateIfEmpty(Return(ii))) {
            var j = 0
            while (j < size) {
              as(j).detach()
              j += 1
            }
          }
        }
        i += 1
      }
      p
    }

  /**
   * "Select" off the first future to be satisfied.  Return this as a
   * result, with the remainder of the Futures as a sequence.
   *
   * TODO: This method should be deprecated in favour of `Futures.select()`.
   *
   * @param fs a java.util.List
   * @return a `Future[Tuple2[Try[A], java.util.List[Future[A]]]]` representing the first future
   * to be satisfied and the rest of the futures.
   */
  def select[A](fs: JList[Future[A]]): Future[(Try[A], JList[Future[A]])] = Futures.select(fs)

  /**
   * Repeat a computation that returns a Future some number of times, after each
   * computation completes.
   */
  def times[A](n: Int)(f: => Future[A]): Future[Unit] = {
    val count = new AtomicInteger(0)
    whileDo(count.getAndIncrement() < n)(f)
  }

  /**
   * Perform the effects of the supplied Future only when the provided
   * flag is true.
   */
  def when[A](p: Boolean)(f: => Future[A]): Future[Unit] =
    if (p) f.unit else Future.Unit

  /**
   * Repeat a computation that returns a Future while some predicate obtains,
   * after each computation completes.
   */
  def whileDo[A](p: => Boolean)(f: => Future[A]): Future[Unit] = {
    def loop(): Future[Unit] = {
      if (p) {
        f.flatMap { _ =>
          loop()
        }
      } else Future.Unit
    }

    loop()
  }

  case class NextThrewException(cause: Throwable)
      extends IllegalArgumentException("'next' threw an exception", cause)

  private class Each[A](next: => Future[A], body: A => Unit) extends (Try[A] => Future[Nothing]) {
    def apply(t: Try[A]): Future[Nothing] = t match {
      case Return(a) =>
        body(a)
        go()
      case t @ Throw(_) =>
        Future.const(t.cast[Nothing])
    }
    def go(): Future[Nothing] = {
      try next.transform(this)
      catch {
        case scala.util.control.NonFatal(exc) =>
          Future.exception(NextThrewException(exc))
      }
    }
  }

  /**
   * Produce values from `next` until it fails synchronously
   * applying `body` to each iteration. The returned `Future`
   * indicates completion via a failed `Future`.
   */
  def each[A](next: => Future[A])(body: A => Unit): Future[Nothing] =
    new Each(next, body).go()

  def parallel[A](n: Int)(f: => Future[A]): Seq[Future[A]] = {
    var i = 0
    val result = new mutable.ArrayBuffer[Future[A]](n)
    while (i < n) {
      result += f
      i += 1
    }
    result
  }

  /**
   * Creates a "batched" Future that, given a function
   * `Seq[In] => Future[Seq[Out]]`, returns a `In => Future[Out]` interface
   * that batches the underlying asynchronous operations. Thus, one can
   * incrementally submit tasks to be performed when the criteria for batch
   * flushing is met.
   *
   * Example:
   *
   *     val timer = new JavaTimer(true)
   *     def processBatch(reqs: Seq[Request]): Future[Seq[Response]]
   *     val batcher = Future.batched(sizeThreshold = 10) {
   *       processBatch
   *     }
   *     val response: Future[Response] = batcher(new Request)
   *
   * `batcher` will wait until 10 requests have been submitted, then delegate
   * to the `processBatch` method to compute the responses.
   *
   * Batchers can be constructed with both size- or time-based thresholds:
   *
   *     val batcher = Future.batched(sizeThreshold = 10, timeThreshold = 10.milliseconds) {
   *       ...
   *     }
   *
   * To force the batcher to immediately process all unprocessed requests:
   *
   *     batcher.flushBatch()
   *
   * A batcher's size can be controlled at runtime with the `sizePercentile`
   * function argument. This function returns a float between 0.0 and 1.0,
   * representing the fractional size of the `sizeThreshold` that should be
   * used for the next batch to be collected.
   */
  def batched[In, Out](
    sizeThreshold: Int,
    timeThreshold: Duration = Duration.Top,
    sizePercentile: => Float = 1.0f
  )(f: Seq[In] => Future[Seq[Out]]
  )(
    implicit timer: Timer
  ): Batcher[In, Out] = {
    new Batcher[In, Out](
      new BatchExecutor[In, Out](sizeThreshold, timeThreshold, sizePercentile, f)
    )
  }
}

/**
 * Represents an asynchronous value.
 *
 * See the [[https://twitter.github.io/finagle/guide/Futures.html user guide]]
 * on concurrent programming with `Futures` to better understand how they
 * work and can be used.
 *
 * A `Future[T]` can be in one of three states:
 *  - Pending, the computation has not yet completed
 *  - Satisfied successfully, with a result of type `T` (a [[Return]])
 *  - Satisfied by a failure, with a `Throwable` result (a [[Throw]])
 *
 * This definition of `Future` does not assume any concrete implementation;
 * in particular, it does not couple the user to a specific executor or event loop.
 *
 * @see The [[https://twitter.github.io/finagle/guide/Futures.html user guide]]
 *      on concurrent programming with Futures.
 * @see [[Futures]] for Java-friendly APIs.
 *
 * @define callbacks
 * {{{
 * import com.twitter.util.Future
 * def callbacks(result: Future[Int]): Future[Int] =
 *   result.onSuccess { i =>
 *     println(i)
 *   }.onFailure { e =>
 *     println(e.getMessage)
 *   }.ensure {
 *     println("always printed")
 *   }
 * }}}
 *
 * @define awaitresult
 * // Await.result blocks the current thread,
 * // don't use it except for tests.
 */
abstract class Future[+A] extends Awaitable[A] { self =>

  /**
   * When the computation completes, invoke the given callback
   * function.
   *
   * The returned `Future` will be satisfied when this,
   * the original future, is done.
   *
   * This method is most useful for very generic code (like
   * libraries). Otherwise, it is a best practice to use one of the
   * alternatives ([[onSuccess]], [[onFailure]], etc.).
   *
   * @example
   * {{{
   * import com.twitter.util.{Await, Future, Return, Throw}
   * val f1: Future[Int] = Future.value(1)
   * val f2: Future[Int] = Future.exception(new Exception("boom!"))
   * val printing: Future[Int] => Future[Int] = x => x.respond {
   * 	case Return(_) => println("Here's a Return")
   * 	case Throw(_) => println("Here's an Exception")
   * }
   * Await.result(printing(f1))
   * // Prints side-effect "Here's a Return" and then returns Future value "1"
   * Await.result(printing(f2))
   * // Prints side-effect "Here's an Exception" and then throws java.lang.Exception: boom!
   * $awaitresult
   * }}}
   *
   * @note this should be used for side-effects.
   * @param k the side-effect to apply when the computation completes.
   *          The value of the input to `k` will be the result of the
   *          computation to this future.
   * @return a chained Future[A]
   * @see [[transform]] to produce a new `Future` from the result of
   *     the computation.
   * @see [[ensure]] if you are not interested in the result of
   *     the computation.
   * @see [[addEventListener]] for a Java friendly API.
   */
  def respond(k: Try[A] => Unit): Future[A]

  /**
   * Invoked regardless of whether the computation completed successfully or unsuccessfully.
   * Implemented in terms of [[respond]] so that subclasses control evaluation order. Returns a
   * chained Future.
   *
   * The returned `Future` will be satisfied when this,
   * the original future, is done.
   *
   * @example
   * $callbacks
   * {{{
   * val a = Future.value(1)
   * callbacks(a) // prints "1" and then "always printed"
   * }}}
   *
   * @note this should be used for side-effects.
   * @param f the side-effect to apply when the computation completes.
   * @see [[respond]] if you need the result of the computation for
   *     usage in the side-effect.
   */
  def ensure(f: => Unit): Future[A] = respond { _ =>
    f
  }

  /**
   * Is the result of the Future available yet?
   */
  def isDefined: Boolean = poll.isDefined

  /**
   * Checks whether a Unit-typed Future is done. By
   * convention, futures of type Future[Unit] are used
   * for signalling.
   */
  def isDone(implicit ev: this.type <:< Future[Unit]): Boolean =
    ev(this).poll == Future.SomeReturnUnit

  /**
   * Polls for an available result.  If the Future has been
   * satisfied, returns Some(result), otherwise None.
   */
  def poll: Option[Try[A]]

  /**
   * Raise the given throwable as an interrupt. Interrupts are
   * one-shot and latest-interrupt wins. That is, the last interrupt
   * to have been raised is delivered exactly once to the Promise
   * responsible for making progress on the future (multiple such
   * promises may be involved in `flatMap` chains).
   *
   * Raising an interrupt does not alter the externally observable
   * state of the `Future`. They are used to signal to the ''producer''
   * of the future's value that the result is no longer desired (for
   * whatever reason given in the passed `Throwable`). For example:
   * {{{
   * import com.twitter.util.Promise
   * val p = new Promise[Unit]()
   * p.setInterruptHandler { case _ => println("interrupt handler fired") }
   * p.poll // is `None`
   * p.raise(new Exception("raised!"))
   * p.poll // is still `None`
   * }}}
   *
   * In the context of a `Future` created via composition (e.g.
   * `flatMap`/`onSuccess`/`transform`), `raise`-ing on that `Future` will
   * call `raise` on the head of the chain which created this `Future`.
   * For example:
   * {{{
   * import com.twitter.util.Promise
   * val p = new Promise[Int]()
   * p.setInterruptHandler { case _ => println("interrupt handler fired") }
   * val f = p.map(_ + 1)
   *
   * f.raise(new Exception("fire!"))
   * }}}
   * The call to `f.raise` will call `p.raise` and print "interrupt handler fired".
   *
   * When the head of that chain of `Futures` is satisfied, the next
   * `Future` in the chain created by composition will have `raise`
   * called. For example:
   * {{{
   * import com.twitter.util.Promise
   * val p1, p2 = new Promise[Int]()
   * p1.setInterruptHandler { case _ => println("p1 interrupt handler") }
   * p2.setInterruptHandler { case _ => println("p2 interrupt handler") }
   * val f = p1.flatMap { _ => p2 }
   *
   * f.raise(new Exception("fire!")) // will print "p1 interrupt handler"
   * p1.setValue(1) // will print "p2 interrupt handler"
   * }}}
   *
   * @see [[Promise.setInterruptHandler]]
   */
  def raise(interrupt: Throwable): Unit

  /**
   * Returns a new Future that fails if this Future does not return in time.
   *
   * Same as the other `raiseWithin`, but with an implicit timer. Sometimes this is more convenient.
   *
   * ''Note'': On timeout, the underlying future is interrupted.
   */
  def raiseWithin(timeout: Duration)(implicit timer: Timer): Future[A] =
    raiseWithin(timer, timeout, new TimeoutException(timeout.toString))

  /**
   * Returns a new Future that fails if this Future does not return in time.
   *
   * Same as the other `raiseWithin`, but with an implicit timer. Sometimes this is more convenient.
   *
   * ''Note'': On timeout, the underlying future is interrupted.
   */
  def raiseWithin(timeout: Duration, exc: => Throwable)(implicit timer: Timer): Future[A] =
    raiseWithin(timer, timeout, exc)

  /**
   * Returns a new Future that fails if this Future does not return in time.
   *
   * ''Note'': On timeout, the underlying future is interrupted.
   */
  def raiseWithin(timer: Timer, timeout: Duration, exc: => Throwable): Future[A] = {
    if (timeout == Duration.Top || isDefined)
      return this

    within(timer, timeout, Future.raiseException).rescue {
      case e if e eq Future.raiseException =>
        this.raise(exc)
        Future.exception(exc)
    }
  }

  /**
   * Returns a new Future that fails if it is not satisfied in time.
   *
   * Same as the other `within`, but with an implicit timer. Sometimes this is more convenient.
   *
   * ''Note'': On timeout, the underlying future is not interrupted.
   */
  def within(timeout: Duration)(implicit timer: Timer): Future[A] =
    within(timer, timeout)

  /**
   * Returns a new Future that fails if it is not satisfied in time.
   *
   * ''Note'': On timeout, the underlying future is not interrupted.
   */
  def within(timer: Timer, timeout: Duration): Future[A] =
    within(timer, timeout, new TimeoutException(timeout.toString))

  /**
   * Returns a new Future that fails if it is not satisfied in time.
   *
   * ''Note'': On timeout, the underlying future is not interrupted.
   *
   * @param timer to run timeout on.
   * @param timeout indicates how long you are willing to wait for the result to be available.
   * @param exc exception to throw.
   */
  def within(timer: Timer, timeout: Duration, exc: => Throwable): Future[A] =
    by(timer, timeout.fromNow, exc)

  /**
   * Returns a new Future that fails if it is not satisfied before the given time.
   *
   * Same as the other `by`, but with an implicit timer. Sometimes this is more convenient.
   *
   * ''Note'': On timeout, the underlying future is not interrupted.
   */
  def by(when: Time)(implicit timer: Timer): Future[A] =
    by(timer, when)

  /**
   * Returns a new Future that fails if it is not satisfied before the given time.
   *
   * ''Note'': On timeout, the underlying future is not interrupted.
   */
  def by(timer: Timer, when: Time): Future[A] =
    by(timer, when, new TimeoutException(when.toString))

  /**
   * Returns a new Future that fails if it is not satisfied before the given time.
   *
   * ''Note'': On timeout, the underlying future is not interrupted.
   *
   * ''Note'': Interrupting a returned future would not prevent it from being satisfied
   *           with a given exception (when the time comes).
   *
   * @param timer to run timeout on.
   * @param when indicates when to stop waiting for the result to be available.
   * @param exc exception to throw.
   */
  def by(timer: Timer, when: Time, exc: => Throwable): Future[A] =
    if (when == Time.Top || isDefined) self
    else
      new Promise[A] with (Try[A] => Unit) with (() => Unit) { other =>
        private[this] val task: TimerTask = timer.schedule(when)(other())

        // Timer task.
        def apply(): Unit = updateIfEmpty(Throw(exc))

        // Respond handler.
        def apply(value: Try[A]): Unit = {
          task.cancel()
          updateIfEmpty(value)
        }

        // Register ourselves as interrupt and respond handlers.
        forwardInterruptsTo(self)
        self.respond(other)
      }

  /**
   * Delay the completion of this Future for at least `howlong` from now.
   *
   * ''Note'': Interrupting a returned future would not prevent it from becoming this future
   *           (when the time comes).
   */
  def delayed(howlong: Duration)(implicit timer: Timer): Future[A] =
    if (howlong == Duration.Zero) this
    else
      new Promise[A] with (() => Unit) { other =>
        // Timer task.
        def apply(): Unit = become(self)

        timer.schedule(howlong.fromNow)(other())
        forwardInterruptsTo(self)
      }

  /**
   * When this future completes, run `f` on that completed result
   * whether or not this computation was successful.
   *
   * The returned `Future` will be satisfied when `this`,
   * the original future, and `f` are done.
   *
   * @example
   * {{{
   * import com.twitter.util.{Await, Future, Return, Throw}
   * val f1: Future[Int] = Future.value(1)
   * val f2: Future[Int] = Future.exception(new Exception("boom!"))
   * val transforming: Future[Int] => Future[String] = x => x.transform {
   * 	case Return(i) => Future.value(i.toString)
   * 	case Throw(e) => Future.value(e.getMessage)
   * }
   * Await.result(transforming(f1)) // String = 1
   * Await.result(transforming(f2)) // String = "boom!"
   * $awaitresult
   * }}}
   *
   * @see [[respond]] for purely side-effecting callbacks.
   * @see [[map]] and [[flatMap]] for dealing strictly with successful
   *     computations.
   * @see [[handle]] and [[rescue]] for dealing strictly with exceptional
   *     computations.
   * @see [[transformedBy]] for a Java friendly API.
   */
  def transform[B](f: Try[A] => Future[B]): Future[B]

  /**
   * When this future completes, run `f` on that completed result
   * whether or not this computation was successful.
   *
   * This method is similar to `transform`, but the transformation is
   * applied without introducing an intermediate future, which leads
   * to better performance.
   *
   * The returned `Future` will be satisfied when `this`,
   * the original future, is done.
   *
   * @see [[respond]] for purely side-effecting callbacks.
   * @see [[map]] and [[flatMap]] for dealing strictly with successful
   *     computations.
   * @see [[handle]] and [[rescue]] for dealing strictly with exceptional
   *     computations.
   * @see [[transformedBy]] for a Java friendly API.
   * @see [[transform]] for transformations that return `Future`.
   */
  protected def transformTry[B](f: Try[A] => Try[B]): Future[B]

  /**
   * If this, the original future, succeeds, run `f` on the result.
   *
   * The returned result is a Future that is satisfied when the original future
   * and the callback, `f`, are done.
   *
   * @example
   * {{{
   * import com.twitter.util.{Await, Future}
   * val f: Future[Int] = Future.value(1)
   * val newf: Future[Int] = f.flatMap { x =>
   *   Future.value(x + 10)
   * }
   * Await.result(newf) // 11
   * $awaitresult
   * }}}
   *
   * If the original future fails, this one will also fail, without executing `f`
   * and preserving the failed computation of `this`
   * @example
   * {{{
   * import com.twitter.util.{Await, Future}
   * val f: Future[Int] = Future.exception(new Exception("boom!"))
   * val newf: Future[Int] = f.flatMap { x =>
   *   println("I'm being executed") // won't print
   *   Future.value(x + 10)
   * }
   * Await.result(newf) // throws java.lang.Exception: boom!
   * }}}
   *
   * @see [[map]]
   */
  def flatMap[B](f: A => Future[B]): Future[B] =
    transform {
      case Return(v) => f(v)
      case t: Throw[_] => Future.const[B](t.cast[B])
    }

  /**
   * Sequentially compose `this` with `f`. This is as [[flatMap]], but
   * discards the result of `this`. Note that this applies only
   * `Unit`-valued  Futures — i.e. side-effects.
   */
  def before[B](f: => Future[B])(implicit ev: this.type <:< Future[Unit]): Future[B] =
    transform {
      case Return(_) => f
      case t: Throw[_] => Future.const[B](t.cast[B])
    }

  /**
   * If this, the original future, results in an exceptional computation,
   * `rescueException` may convert the failure into a new result.
   *
   * The returned result is a `Future` that is satisfied when the original
   * future and the callback, `rescueException`, are done.
   *
   * This is the equivalent of [[flatMap]] for failed computations.
   *
   * @example
   * {{{
   * import com.twitter.util.{Await, Future}
   * val f1: Future[Int] = Future.exception(new Exception("boom1!"))
   * val f2: Future[Int] = Future.exception(new Exception("boom2!"))
   * val newf: Future[Int] => Future[Int] = x => x.rescue {
   * 	case e: Exception if e.getMessage == "boom1!" => Future.value(1)
   * }
   * Await.result(newf(f1)) // 1
   * Await.result(newf(f2)) // throws java.lang.Exception: boom2!
   * $awaitresult
   * }}}
   *
   * @see [[handle]]
   */
  def rescue[B >: A](rescueException: PartialFunction[Throwable, Future[B]]): Future[B] =
    transform {
      case Throw(t) =>
        val result = rescueException.applyOrElse(t, Future.AlwaysNotApplied)
        if (result eq Future.NotApplied) this else result
      case _ => this
    }

  /**
   * Invoke the callback only if the Future returns successfully. Useful for Scala `for`
   * comprehensions. Use [[onSuccess]] instead of this method for more readable code.
   *
   * @see [[onSuccess]]
   */
  def foreach(k: A => Unit): Future[A] = onSuccess(k)

  /**
   * If this, the original future, succeeds, run `f` on the result.
   *
   * The returned result is a Future that is satisfied when the original future
   * and the callback, `f`, are done.
   *
   * @example
   * {{{
   * import com.twitter.util.{Await, Future}
   * val f: Future[Int] = Future.value(1)
   * val newf: Future[Int] = f.map { x =>
   *   x + 10
   * }
   * Await.result(newf) // 11
   * $awaitresult
   * }}}
   *
   * If the original future fails, this one will also fail, without executing `f`
   * and preserving the failed computation of `this`
   *
   * @example
   * {{{
   * import com.twitter.util.{Await, Future}
   * val f: Future[Int] = Future.exception(new Exception("boom!"))
   * val newf: Future[Int] = f.map { x =>
   *   println("I'm being executed") // won't print
   *   x + 10
   * }
   * Await.result(newf) // throws java.lang.Exception: boom!
   * }}}
   *
   * @see [[flatMap]] for computations that return `Future`s.
   * @see [[onSuccess]] for side-effecting chained computations.
   */
  def map[B](f: A => B): Future[B] =
    transformTry(_.map(f))

  def filter(p: A => Boolean): Future[A] =
    transformTry(_.filter(p))

  def withFilter(p: A => Boolean): Future[A] = filter(p)

  /**
   * Invoke the function on the result, if the computation was
   * successful.  Returns a chained Future as in `respond`.
   *
   * @note this should be used for side-effects.
   *
   * @example
   * $callbacks
   * {{{
   * val a = Future.value(1)
   * callbacks(a) // prints "1" and then "always printed"
   * }}}
   *
   * @return chained Future
   * @see [[flatMap]] and [[map]] to produce a new `Future` from the result of
   *     the computation.
   */
  def onSuccess(f: A => Unit): Future[A] =
    respond {
      case Return(value) => f(value)
      case _ =>
    }

  /**
   * Invoke the function on the error, if the computation was
   * unsuccessful.  Returns a chained Future as in `respond`.
   *
   * @note this should be used for side-effects.
   *
   * @note if `fn` is a `PartialFunction` and the input is not defined for a given
   *       Throwable, the resulting `MatchError` will propagate to the current
   *       `Monitor`. This will happen if you use a construct such as
   *       `future.onFailure { case NonFatal(e) => ... }` when the Throwable
   *       is "fatal".
   *
   * @example
   * $callbacks
   * {{{
   * val b = Future.exception(new Exception("boom!"))
   * callbacks(b) // prints "boom!" and then "always printed"
   * }}}
   *
   * @return chained Future
   * @see [[handle]] and [[rescue]] to produce a new `Future` from the result of
   *     the computation.
   */
  def onFailure(fn: Throwable => Unit): Future[A] =
    respond {
      case Throw(t) => fn(t)
      case _ =>
    }

  /**
   * Register a [[FutureEventListener]] to be invoked when the
   * computation completes. This method is typically used by Java
   * programs because it avoids the use of small Function objects.
   *
   * @note this should be used for side-effects
   *
   * @see [[respond]] for a Scala API.
   * @see [[transformedBy]] for a Java friendly way to produce
   *     a new `Future` from the result of a computation.
   */
  def addEventListener(listener: FutureEventListener[_ >: A]): Future[A] = respond {
    case Throw(cause) => listener.onFailure(cause)
    case Return(value) => listener.onSuccess(value)
  }

  /**
   * Transform the `Future[A]` into a `Future[B]` using the
   * [[FutureTransformer]]. The [[FutureTransformer]] handles both success
   * ([[Return]]) and failure ([[Throw]]) values by implementing `map`/`flatMap`
   * and `handle`/`rescue`. This method is typically used by Java
   * programs because it avoids the use of small Function objects.
   *
   * @note The [[FutureTransformer]] must implement either `flatMap`
   * or `map` and may optionally implement `handle`. Failing to
   * implement a method will result in a run-time error (`AbstractMethodError`).
   *
   * @see [[transform]] for a Scala API.
   * @see [[addEventListener]] for a Java friendly way to perform side-effects.
   */
  def transformedBy[B](transformer: FutureTransformer[A, B]): Future[B] =
    transform {
      case Return(v) => transformer.flatMap(v)
      case Throw(t) => transformer.rescue(t)
    }

  /**
   * If this, the original future, results in an exceptional computation,
   * `rescueException` may convert the failure into a new result.
   *
   * The returned result is a `Future` that is satisfied when the original
   * future and the callback, `rescueException`, are done.
   *
   * This is the equivalent of [[map]] for failed computations.
   *
   * @example
   * {{{
   * import com.twitter.util.{Await, Future}
   * val f1: Future[Int] = Future.exception(new Exception("boom1!"))
   * val f2: Future[Int] = Future.exception(new Exception("boom2!"))
   * val newf: Future[Int] => Future[Int] = x => x.handle {
   * 	case e: Exception if e.getMessage == "boom1!" => 1
   * }
   * Await.result(newf(f1)) // 1
   * Await.result(newf(f2)) // throws java.lang.Exception: boom2!
   * $awaitresult
   * }}}
   *
   * @see [[rescue]]
   */
  def handle[B >: A](rescueException: PartialFunction[Throwable, B]): Future[B] = rescue {
    case e: Throwable if rescueException.isDefinedAt(e) => Future(rescueException(e))
    case _: Throwable => this
  }

  /**
   * Choose the first Future to be satisfied.
   *
   * @param other another Future
   * @return a new Future whose result is that of the first of this and other to return
   */
  def select[U >: A](other: Future[U]): Future[U] = {
    val p = Promise.interrupts[U](other, this)
    val a = Promise.attached(other)
    val b = Promise.attached(this)
    a.respond { t =>
      if (p.updateIfEmpty(t)) b.detach()
    }
    b.respond { t =>
      if (p.updateIfEmpty(t)) a.detach()
    }
    p
  }

  /**
   * A synonym for [[select]]: Choose the first `Future` to be satisfied.
   */
  def or[U >: A](other: Future[U]): Future[U] = select(other)

  /**
   * Joins this future with a given `other` future into a `Future[(A, B)]`
   * (future of a `Tuple2`). If this or `other` future fails, the returned `Future`
   * is immediately satisfied by that failure.
   */
  def join[B](other: Future[B]): Future[(A, B)] = joinWith(other)(Future.toTuple2)

  /**
   * Joins this future with a given `other` future and applies `fn` to its result.
   * If this or `other` future fails, the returned `Future` is immediately satisfied
   * by that failure.
   *
   * Before (using [[join]]):
   * {{{
   *   val ab = a.join(b).map { case (a, b) => Foo(a, b) }
   * }}}
   *
   * After (using [[joinWith]]):
   * {{{
   *   val ab = a.joinWith(b)(Foo.apply)
   * }}}
   */
  def joinWith[B, C](other: Future[B])(fn: (A, B) => C): Future[C] = {
    val p = Promise.interrupts[C](this, other)
    // This is a race between this future and the other to set the atomic
    // reference. In the Throw case, the winner updates the promise and the
    // loser does nothing. In the Return case the jobs are flipped: the loser
    // updates the promise and the winner does nothing. There is one more
    // consideration: in the Throw case, the loser sets the promise if the
    // winner was a Return. This guarantees that there can be only one attempt
    // to call `fn` and also to update the promise.
    val race = new AtomicReference[Try[_]] with (Try[_] => Unit) {
      def apply(tx: Try[_]): Unit = {
        val isFirst = compareAndSet(null, tx)
        tx match {
          case Return(_) if !isFirst && get.isReturn =>
            p.setValue(fn(Await.result(Future.this), Await.result(other)))
          case t @ Throw(_) if isFirst || get.isReturn =>
            p.update(t.cast[C])
          case _ =>
        }
      }
    }

    this.respond(race)
    other.respond(race)
    p
  }

  /**
   * Convert this `Future[A]` to a `Future[Unit]` by discarding the result.
   *
   * @note failed futures will remain as is.
   */
  def unit: Future[Unit] = transformTry(Future.tryToUnit)

  /**
   * Convert this `Future[A]` to a `Future[Void]` by discarding the result.
   *
   * @note failed futures will remain as is.
   */
  def voided: Future[Void] = transformTry(Future.tryToVoid)

  /**
   * Send updates from this Future to the other.
   * `other` must not yet be satisfied at the time of the call.
   * After this call, nobody else should satisfy `other`.
   *
   * @note using `proxyTo` will mask interrupts to this future, and it's
   * the user's responsibility to set an interrupt handler on `other`
   * to raise on f. In some cases, using
   * [[com.twitter.util.Promise.become]] may be more appropriate.
   *
   * @see [[com.twitter.util.Promise.become]]
   */
  def proxyTo[B >: A](other: Promise[B]): Unit = {
    if (other.isDefined) {
      throw new IllegalStateException(
        s"Cannot call proxyTo on an already satisfied Promise: ${Await.result(other.liftToTry)}"
      )
    }
    respond { res =>
      other.update(res)
    }
  }

  /**
   * An [[com.twitter.concurrent.Offer Offer]] for this future.
   *
   * The offer is activated when the future is satisfied.
   */
  def toOffer: Offer[Try[A]] = new Offer[Try[A]] {
    def prepare(): Future[Tx[Try[A]]] = transformTry(Future.toTx)
  }

  /**
   * Convert a Twitter Future to a Java native Future. This should
   * match the semantics of a Java Future as closely as possible to
   * avoid issues with the way another API might use them. See:
   *
   * https://download.oracle.com/javase/6/docs/api/java/util/concurrent/Future.html#cancel(boolean)
   */
  def toJavaFuture: JavaFuture[_ <: A] = {
    new JavaFuture[A] {
      private[this] val wasCancelled = new AtomicBoolean(false)

      def cancel(mayInterruptIfRunning: Boolean): Boolean = {
        if (wasCancelled.compareAndSet(false, true))
          self.raise(new CancellationException)
        true
      }

      def isCancelled: Boolean = wasCancelled.get
      def isDone: Boolean = isCancelled || self.isDefined

      def get(): A = {
        if (isCancelled)
          throw new CancellationException
        Await.result(self)
      }

      def get(time: Long, timeUnit: TimeUnit): A = {
        if (isCancelled)
          throw new CancellationException
        Await.result(self, Duration.fromTimeUnit(time, timeUnit))
      }
    }
  }

  /**
   * Converts a `Future[Future[B]]` into a `Future[B]`.
   */
  def flatten[B](implicit ev: A <:< Future[B]): Future[B] =
    flatMap[B](ev)

  /**
   * Returns an identical `Future` except that it ignores interrupts which match a predicate.
   *
   * This means that a [[Promise.setInterruptHandler Promise's interrupt handler]]
   * will not execute on calls to [[Future.raise]] for inputs to `pred`
   * that evaluate to `true`. Also, `raise` will not be forwarded to chained Futures.
   *
   * For example:
   * {{{
   * val p = new Promise[Int]()
   * p.setInterruptHandler { case x => println(s"interrupt handler for ${x.getClass}") }
   * val f1: Future[Int] = p.mask {
   *   case _: IllegalArgumentException => true
   * }
   * f1.raise(new IllegalArgumentException("ignored!")) // nothing will be printed
   * f1.map(_ + 1).raise(new IllegalArgumentException("ignored!")) // nothing will be printed
   *
   * val f2: Future[Int] = p.mask {
   *   case _: IllegalArgumentException => true
   * }
   * f2.raise(new Exception("fire!")) // will print "interrupt handler for class java.lang.Exception"
   * }}}
   *
   * @see [[raise]]
   * @see [[masked]]
   * @see [[interruptible()]]
   */
  def mask(pred: PartialFunction[Throwable, Boolean]): Future[A] = {
    val p = Promise[A]()
    p.setInterruptHandler {
      case t if !PartialFunction.cond(t)(pred) => self.raise(t)
    }
    self.proxyTo(p)
    p
  }

  /**
   * Returns an identical `Future` that ignores all interrupts.
   *
   * This means that a [[Promise.setInterruptHandler Promise's interrupt handler]]
   * will not execute for any call to [[Future.raise]].  Also, `raise` will not
   * be forwarded to chained Futures.
   *
   * For example:
   * {{{
   * import com.twitter.util.{Future, Promise}
   * val p = new Promise[Int]()
   * p.setInterruptHandler { case _ => println("interrupt handler") }
   *
   * val f: Future[Int] = p.masked
   * f.raise(new Exception("ignored!")) // nothing will be printed
   * f1.map(_ + 1).raise(new Exception("ignored!")) // nothing will be printed
   * }}}
   *
   * @see [[raise]]
   * @see [[mask]]
   * @see [[interruptible()]]
   */
  def masked: Future[A] = mask(Future.AlwaysMasked)

  /**
   * Returns a `Future[Boolean]` indicating whether two Futures are equivalent.
   *
   * Note that {{{Future.exception(e).willEqual(Future.exception(e)) == Future.value(true)}}}.
   */
  def willEqual[B](that: Future[B]): Future[Boolean] = {
    this.transform { thisResult =>
      that.transform { thatResult =>
        Future.value(thisResult == thatResult)
      }
    }
  }

  /**
   * Returns the result of the computation as a `Future[Try[A]]`.
   *
   * @example
   * {{{
   * import com.twitter.util.{Await, Future, Try}
   * val fr: Future[Int] = Future.value(1)
   * val ft: Future[Int] = Future.exception(new Exception("boom!"))
   * val r: Future[Try[Int]] = fr.liftToTry
   * val t: Future[Try[Int]] = ft.liftToTry
   * Await.result(r) // Return(1)
   * Await.result(t) // Throw(java.lang.Exception: boom!)
   * $awaitresult
   * }}}
   */
  def liftToTry: Future[Try[A]] = transformTry(Future.liftToTry)

  /**
   * Lowers a `Future[Try[T]]` into a `Future[T]`.
   *
   * @example
   * {{{
   * import com.twitter.util.{Await, Future, Return, Throw, Try}
   * val fr: Future[Try[Int]] = Future.value(Return(1))
   * val ft: Future[Try[Int]] = Future.value(Throw(new Exception("boom!")))
   * val r: Future[Int] = fr.lowerFromTry
   * val t: Future[Int] = ft.lowerFromTry
   * Await.result(r) // 1
   * Await.result(t) // throws java.lang.Exception: boom!
   * $awaitresult
   * }}}
   */
  def lowerFromTry[B](implicit ev: A <:< Try[B]): Future[B] =
    transformTry(Future.flattenTry)

  /**
   * Makes a derivative `Future` which will be satisfied with the result
   * of the parent.  However, if it's interrupted, it will detach from
   * the parent `Future`, satisfy itself with the exception raised to
   * it, and won't propagate the interruption back to the parent
   * `Future`.
   *
   * This is useful for when a `Future` is shared between many contexts,
   * where it may not be useful to discard the underlying computation
   * if just one context is no longer interested in the result.  In
   * particular, this is different from [[masked Future.masked]] in that it will
   * prevent memory leaks if the parent Future will never be
   * satisfied, because closures that are attached to this derivative
   * `Future` will not be held onto by the killer `Future`.
   *
   * @see [[raise]]
   * @see [[mask]]
   * @see [[masked]]
   */
  def interruptible(): Future[A] = {
    if (isDefined) return this

    val p = Promise.attached(this)
    p.setInterruptHandler {
      case t: Throwable =>
        if (p.detach())
          p.setException(t)
    }
    p
  }
}
