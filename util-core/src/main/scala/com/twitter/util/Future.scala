package com.twitter.util

import com.twitter.concurrent.{Offer, Scheduler, Tx}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.concurrent.{CancellationException, TimeUnit, Future => JavaFuture}
import java.util.{List => JList, Map => JMap}
import scala.collection.JavaConverters.{
  asScalaBufferConverter, mapAsJavaMapConverter, mapAsScalaMapConverter, seqAsJavaListConverter}
import scala.collection.mutable
import scala.runtime.NonLocalReturnControl
import scala.util.control.NoStackTrace

class FutureNonLocalReturnControl(cause: NonLocalReturnControl[_]) extends Exception(cause) {
  override def getMessage: String = "Invalid use of `return` in closure passed to a Future"
}

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

  /**
   * A failed `Future` analogous to `Predef.???`.
   */
  val ??? : Future[Nothing] = Future.exception(new NotImplementedError("an implementation is missing"))

  private val SomeReturnUnit = Some(Return.Unit)
  private val NotApplied: Future[Nothing] = new NoFuture
  private val AlwaysNotApplied: Any => Future[Nothing] = scala.Function.const(NotApplied)
  private val toUnit: Any => Future[Unit] = scala.Function.const(Unit)
  private val toVoid: Any => Future[Void] = scala.Function.const(Void)
  private val AlwaysMasked: PartialFunction[Throwable, Boolean] = { case _ => true }

  private val toTuple2Instance: (Any, Any) => (Any, Any) = Tuple2.apply
  private def toTuple2[A, B]: (A, B) => (A, B) = toTuple2Instance.asInstanceOf[(A, B) => (A, B)]
  private val toValueInstance: Any => Future[Any] = Future.value _
  private def toValue[T]: T => Future[T] = toValueInstance.asInstanceOf[T => Future[T]]

  private val lowerFromTryInstance: Any => Future[Any] = {
    case t: Try[_] => Future.const(t)
  }

  private def lowerFromTry[A, T]: A => Future[T] =
    lowerFromTryInstance.asInstanceOf[A => Future[T]]

  private val toTxInstance: Try[Any] => Future[Tx[Try[Any]]] =
    res => {
      val tx = new Tx[Try[Any]] {
        def ack(): Future[Tx.Result[Try[Any]]] = Future.value(Tx.Commit(res))
        def nack(): Unit = ()
      }

      Future.value(tx)
    }
  private def toTx[A]: Try[A] => Future[Tx[Try[A]]] =
    toTxInstance.asInstanceOf[Try[A] => Future[Tx[Try[A]]]]

  private val emptySeqInstance: Future[Seq[Any]] = Future.value(Seq.empty)
  private def emptySeq[A]: Future[Seq[A]] = emptySeqInstance.asInstanceOf[Future[Seq[A]]]

  private val emptyMapInstance: Future[Map[Any, Any]] = Future.value(Map.empty[Any, Any])
  private def emptyMap[A, B]: Future[Map[A, B]] = emptyMapInstance.asInstanceOf[Future[Map[A, B]]]

  // Exception used to raise on Futures.
  private[this] val RaiseException = new Exception with NoStackTrace
  @inline private final def raiseException = RaiseException

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
  def sleep(howlong: Duration)(implicit timer: Timer): Future[Unit] = {
    if (howlong <= Duration.Zero)
      return Future.Done

    val p = new Promise[Unit]
    val task = timer.schedule(howlong.fromNow) { p.setDone() }
    p.setInterruptHandler {
      case e =>
        if (p.updateIfEmpty(Throw(e)))
          task.cancel()
    }
    p
  }

  /**
   * Creates a satisfied `Future` from the result of running `a`.
   *
   * If the result of `a` is a [[NonFatal non-fatal exception]],
   * this will result in a failed `Future`. Otherwise, the result
   * is a successfully satisfied `Future`.
   *
   * @note that `a` is executed in the calling thread and as such
   *       some care must be taken with blocking code.
   */
  def apply[A](a: => A): Future[A] = try {
    const(Try(a))
  } catch {
    case nlrc: NonLocalReturnControl[_] => Future.exception(new FutureNonLocalReturnControl(nlrc))
  }

  def unapply[A](f: Future[A]): Option[Try[A]] = f.poll

  // The thread-safety for `outer` is guaranteed by synchronizing on `this`.
  private[this] final class MonitoredPromise[A](private[this] var outer: Promise[A])
    extends Monitor with (Try[A] => Unit) {

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
    val saved = Monitor.get
    try {
      Monitor.set(monitored)
      val f = mkFuture
      p.forwardInterruptsTo(f)
      f.respond(monitored)
    } catch {
      case t: Throwable => if (!monitored.handle(t)) throw t
    } finally { Monitor.set(saved) }

    p
  }

  // Used by `Future.join`.
  // We would like to be able to mix in both PartialFunction[Throwable, Unit]
  // for the interrupt and handler and Function1[Try[A], Unit] for the respond handler,
  // but because these are both Function1 of different types, this cannot be done. Because
  // the respond handler is invoked for each Future in the sequence, and the interrupt handler is
  // only set once, we prefer to mix in Function1.
  private[this] class JoinPromise[A](
      fs: Seq[Future[A]],
      size: Int)
    extends Promise[Unit]
    with Function1[Try[A], Unit] {

    private[this] val count = new AtomicInteger(size)

    // Handler for `Future.respond`, invoked in `Future.join`
    def apply(value: Try[A]): Unit = value match {
      case Return(_) =>
        if (count.decrementAndGet() == 0)
          update(Return.Unit)
      case t@Throw(_) =>
        updateIfEmpty(t.cast[Unit])
    }

    setInterruptHandler { case t: Throwable =>
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
    if (fs.isEmpty) {
      Future.Unit
    } else {
      val size = fs.size
      if (size == 1) {
        fs(0).unit
      } else {
        val p = new JoinPromise[A](fs, size)
        val iterator = fs.iterator
        while (iterator.hasNext)
          iterator.next().respond(p)
        p
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
def join[%s](%s): Future[(%s)] = join(Seq(%s)) map { _ => (%s) }""".format(
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
  def join[A,B](a: Future[A],b: Future[B]): Future[(A,B)] = join(Seq(a,b)) map { _ => (Await.result(a),Await.result(b)) }
  /**
   * Join 3 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C](a: Future[A],b: Future[B],c: Future[C]): Future[(A,B,C)] = join(Seq(a,b,c)) map { _ => (Await.result(a),Await.result(b),Await.result(c)) }
  /**
   * Join 4 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D](a: Future[A],b: Future[B],c: Future[C],d: Future[D]): Future[(A,B,C,D)] = join(Seq(a,b,c,d)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d)) }
  /**
   * Join 5 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E]): Future[(A,B,C,D,E)] = join(Seq(a,b,c,d,e)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e)) }
  /**
   * Join 6 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F]): Future[(A,B,C,D,E,F)] = join(Seq(a,b,c,d,e,f)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f)) }
  /**
   * Join 7 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G]): Future[(A,B,C,D,E,F,G)] = join(Seq(a,b,c,d,e,f,g)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g)) }
  /**
   * Join 8 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H]): Future[(A,B,C,D,E,F,G,H)] = join(Seq(a,b,c,d,e,f,g,h)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h)) }
  /**
   * Join 9 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I]): Future[(A,B,C,D,E,F,G,H,I)] = join(Seq(a,b,c,d,e,f,g,h,i)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i)) }
  /**
   * Join 10 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J]): Future[(A,B,C,D,E,F,G,H,I,J)] = join(Seq(a,b,c,d,e,f,g,h,i,j)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j)) }
  /**
   * Join 11 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K]): Future[(A,B,C,D,E,F,G,H,I,J,K)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k)) }
  /**
   * Join 12 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L]): Future[(A,B,C,D,E,F,G,H,I,J,K,L)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l)) }
  /**
   * Join 13 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m)) }
  /**
   * Join 14 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n)) }
  /**
   * Join 15 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n),Await.result(o)) }
  /**
   * Join 16 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n),Await.result(o),Await.result(p)) }
  /**
   * Join 17 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n),Await.result(o),Await.result(p),Await.result(q)) }
  /**
   * Join 18 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q],r: Future[R]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n),Await.result(o),Await.result(p),Await.result(q),Await.result(r)) }
  /**
   * Join 19 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q],r: Future[R],s: Future[S]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n),Await.result(o),Await.result(p),Await.result(q),Await.result(r),Await.result(s)) }
  /**
   * Join 20 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q],r: Future[R],s: Future[S],t: Future[T]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n),Await.result(o),Await.result(p),Await.result(q),Await.result(r),Await.result(s),Await.result(t)) }
  /**
   * Join 21 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q],r: Future[R],s: Future[S],t: Future[T],u: Future[U]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n),Await.result(o),Await.result(p),Await.result(q),Await.result(r),Await.result(s),Await.result(t),Await.result(u)) }
  /**
   * Join 22 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q],r: Future[R],s: Future[S],t: Future[T],u: Future[U],v: Future[V]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n),Await.result(o),Await.result(p),Await.result(q),Await.result(r),Await.result(s),Await.result(t),Await.result(u),Await.result(v)) }

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
   *      Future(i + 1)
   *    }
   *  }}}
   *
   * @param `as` a sequence of `A` that will have `f` applied to each item sequentially
   * @return a `Future[Seq[B]]` containing the results of `f` being applied to every item in `as`
   */
  def traverseSequentially[A,B](as: Seq[A])(f: A => Future[B]): Future[Seq[B]] =
    as.foldLeft(emptySeq[B]) { (resultsFuture, nextItem) =>
      for {
        results    <- resultsFuture
        nextResult <- f(nextItem)
      } yield results :+ nextResult
    }

  private[this] final class CollectPromise[A](fs: Seq[Future[A]])
    extends Promise[Seq[A]] with Promise.InterruptHandler {

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

      while (it.hasNext) {
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
      Future.collect(values) map { seq =>
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
      while (iterator.hasNext)
        seq += iterator.next().liftToTry
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
      val p = Promise.interrupts[(Try[A], Seq[Future[A]])](fs:_*)
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
      val p = Promise.interrupts[Int](fs:_*)
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
      if (p) f flatMap { _ => loop() }
      else Future.Unit
    }

    loop()
  }

  case class NextThrewException(cause: Throwable)
    extends IllegalArgumentException("'next' threw an exception", cause)

  /**
   * Produce values from `next` until it fails, synchronously
   * applying `body` to each iteration. The returned future
   * indicates completion (via an exception).
   */
  def each[A](next: => Future[A])(body: A => Unit): Future[Nothing] = {
    def go(): Future[Nothing] =
      try next flatMap { a => body(a); go() }
      catch {
        case NonFatal(exc) =>
          Future.exception(NextThrewException(exc))
      }

    go()
  }

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
  )(
    f: Seq[In] => Future[Seq[Out]]
  )(
    implicit timer: Timer
  ): Batcher[In, Out] = {
    new Batcher[In, Out](new BatchExecutor[In, Out](sizeThreshold, timeThreshold, sizePercentile, f))
  }
}

class FutureCancelledException
  extends Exception("The future was cancelled with Future.cancel")

/**
 * A Java friendly API for handling side-effects for a `Future`.
 *
 * If you want a Java API to
 * [[https://twitter.github.io/finagle/guide/Futures.html#sequential-composition sequence]]
 * the work you can use a [[FutureTransformer]].
 *
 * @see [[Future.respond]] which is the equivalent Scala API for further details.
 * @see [[Future.addEventListener]] for using it with a `Future`.
 * @see [[FutureTransformer]] for a Java API for transforming the results of Futures.
 */
trait FutureEventListener[T] {
  /**
   * A side-effect which is invoked if the computation completes successfully.
   */
  def onSuccess(value: T): Unit

  /**
   * A side-effect which is invoked if the computation completes unsuccessfully.
   */
  def onFailure(cause: Throwable): Unit
}

/**
 * An alternative interface for performing Future transformations;
 * that is, converting a Future[A] to a Future[B]. This interface is
 * designed to be friendly to Java users since it does not require
 * creating many small Function objects. It is used in conjunction
 * with `transformedBy`.
 *
 * You must override one of `{map, flatMap}`. If you override both
 * `map` and `flatMap`, `flatMap` takes precedence. If you fail to
 * override one of `{map, flatMap}`, an `AbstractMethodError` will be
 * thrown at Runtime.
 *
 * '''Note:''' to end a result with a failure, we encourage you to use either
 * `flatMap` or `rescue` and return a failed Future, instead of throwing an
 * exception.  A failed future can be used by returning
 * `Future.exception(throwable)` instead of throwing an exception.
 *
 * @see [[Future.transform]] which is the equivalent Scala API for further details.
 * @see [[Future.transformedBy]] for using it with a `Future`.
 * @see [[FutureEventListener]] for a Java API for side-effects.
 */
abstract class FutureTransformer[-A, +B] {
  /**
   * Invoked if the computation completes successfully. Returns the
   * new transformed value in a Future.
   */
  def flatMap(value: A): Future[B] = Future.value(map(value))

  /**
   * Invoked if the computation completes successfully. Returns the
   * new transformed value.
   *
   * ''Note'': this method will throw an `AbstractMethodError` if it is not overridden.
   */
  def map(value: A): B =
    throw new AbstractMethodError(s"`map` must be implemented by $this")

  /**
   * Invoked if the computation completes unsuccessfully. Returns the
   * new Future value.
   */
  def rescue(throwable: Throwable): Future[B] = Future.value(handle(throwable))

  /**
   * Invoked if the computation fails. Returns the new transformed
   * value.
   */
  def handle(throwable: Throwable): B = throw throwable
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
   * @note this should be used for side-effects.
   *
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
   * @note this should be used for side-effects.
   *
   * @param f the side-effect to apply when the computation completes.
   * @see [[respond]] if you need the result of the computation for
   *     usage in the side-effect.
   */
  def ensure(f: => Unit): Future[A] = respond { _ => f }

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
   * state of the Future. They are used to signal to the ''producer''
   * of the future's value that the result is no longer desired (for
   * whatever reason given in the passed Throwable).
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
  def raiseWithin(timeout: Duration, exc: Throwable)(implicit timer: Timer): Future[A] =
    raiseWithin(timer, timeout, exc)

  /**
   * Returns a new Future that fails if this Future does not return in time.
   *
   * ''Note'': On timeout, the underlying future is interrupted.
   */
  def raiseWithin(timer: Timer, timeout: Duration, exc: Throwable): Future[A] = {
    if (timeout == Duration.Top || isDefined)
      return this

    within(timer, timeout, Future.raiseException) rescue {
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
   * @param timer to run timeout on.
   * @param when indicates when to stop waiting for the result to be available.
   * @param exc exception to throw.
   */
  def by(timer: Timer, when: Time, exc: => Throwable): Future[A] = {
    if (when == Time.Top || isDefined)
      return this

    // Mix in Fn1 so that we can avoid an allocation below when calling respond
    val p = new Promise[A] with Function1[Try[A], Unit] {
      this.forwardInterruptsTo(self)

      private[this] val task = timer.schedule(when) {
        this.updateIfEmpty(Throw(exc))
      }

      def apply(value: Try[A]) : Unit = {
        task.cancel()
        this.updateIfEmpty(value)
      }
    }

    respond(p)
    p
  }

  /**
   * Delay the completion of this Future for at least
   * `howlong` from now.
   */
  def delayed(howlong: Duration)(implicit timer: Timer): Future[A] = {
    if (howlong == Duration.Zero)
      return this

    val p = Promise.interrupts[A](this)
    timer.schedule(howlong.fromNow) { p.become(this) }
    p
  }

  /**
   * When this future completes, run `f` on that completed result
   * whether or not this computation was successful.
   *
   * The returned `Future` will be satisfied when this,
   * the original future, and `f` are done.
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
   * If this, the original future, succeeds, run `f` on the result.
   *
   * The returned result is a Future that is satisfied when the original future
   * and the callback, `f`, are done.
   * If the original future fails, this one will also fail, without executing `f`
   * and preserving the failed computation of `this`.
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
   * @see [[handle]]
   */
  def rescue[B >: A](
    rescueException: PartialFunction[Throwable, Future[B]]
  ): Future[B] = transform({
    case Throw(t) =>
      val result = rescueException.applyOrElse(t, Future.AlwaysNotApplied)
      if (result eq Future.NotApplied) this else result
    case _ => this
  })

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
   * If the original future fails, this one will also fail, without executing `f`
   * and preserving the failed computation of `this`.
   *
   * @see [[flatMap]] for computations that return `Future`s.
   * @see [[onSuccess]] for side-effecting chained computations.
   */
  def map[B](f: A => B): Future[B] =
    transform {
      case Return(r) => Future { f(r) }
      case t: Throw[_] => Future.const[B](t.cast[B])
    }

  def filter(p: A => Boolean): Future[A] = transform { x: Try[A] => Future.const(x.filter(p)) }

  def withFilter(p: A => Boolean): Future[A] = filter(p)

  /**
   * Invoke the function on the result, if the computation was
   * successful.  Returns a chained Future as in `respond`.
   *
   * @note this should be used for side-effects.
   *
   * @return chained Future
   * @see [[flatMap]] and [[map]] to produce a new `Future` from the result of
   *     the computation.
   */
  def onSuccess(f: A => Unit): Future[A] =
    respond({
      case Return(value) => f(value)
      case _ =>
    })

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
    case Throw(cause)  => listener.onFailure(cause)
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
      case Throw(t)  => transformer.rescue(t)
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
   * @see [[rescue]]
   */
  def handle[B >: A](rescueException: PartialFunction[Throwable, B]): Future[B] = rescue {
    case e: Throwable if rescueException.isDefinedAt(e) => Future(rescueException(e))
    case e: Throwable                                   => this
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
    a respond { t => if (p.updateIfEmpty(t)) b.detach() }
    b respond { t => if (p.updateIfEmpty(t)) a.detach() }
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
    this.respond {
      case Throw(t) => p.update(Throw(t))
      case Return(a) => other.respond {
        case Throw(t) => p.update(Throw(t))
        case Return(b) => p.update(Return(fn(a, b)))
      }
    }
    p
  }

  /**
   * Convert this `Future[A]` to a `Future[Unit]` by discarding the result.
   *
   * @note failed futures will remain as is.
   */
  def unit: Future[Unit] = flatMap(Future.toUnit)

  /**
   * Convert this `Future[A]` to a `Future[Void]` by discarding the result.
   *
   * @note failed futures will remain as is.
   */
  def voided: Future[Void] = flatMap(Future.toVoid)

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
  def proxyTo[B >: A](other: Promise[B]) {
    if (other.isDefined) {
      throw new IllegalStateException(
        s"Cannot call proxyTo on an already satisfied Promise: ${Await.result(other.liftToTry)}")
    }
    respond { other() = _ }
  }

  /**
   * An [[com.twitter.concurrent.Offer Offer]] for this future.
   *
   * The offer is activated when the future is satisfied.
   */
  def toOffer: Offer[Try[A]] = new Offer[Try[A]] {
    def prepare(): Future[Tx[Try[A]]] = transform(Future.toTx)
  }

  /**
   * Convert a Twitter Future to a Java native Future. This should
   * match the semantics of a Java Future as closely as possible to
   * avoid issues with the way another API might use them. See:
   *
   * http://download.oracle.com/javase/6/docs/api/java/util/concurrent/Future.html#cancel(boolean)
   */
  def toJavaFuture: JavaFuture[_ <: A] = {
    val f = this
    new JavaFuture[A] {
      val wasCancelled = new AtomicBoolean(false)

      override def cancel(mayInterruptIfRunning: Boolean): Boolean = {
        if (wasCancelled.compareAndSet(false, true))
          f.raise(new CancellationException)
        true
      }

      override def isCancelled: Boolean = wasCancelled.get
      override def isDone: Boolean = isCancelled || f.isDefined

      override def get(): A = {
        if (isCancelled)
          throw new CancellationException
        Await.result(f)
      }

      override def get(time: Long, timeUnit: TimeUnit): A = {
        if (isCancelled)
          throw new CancellationException
        Await.result(f, Duration.fromTimeUnit(time, timeUnit))
      }
    }
  }

  /**
   * Converts a `Future[Future[B]]` into a `Future[B]`.
   */
  def flatten[B](implicit ev: A <:< Future[B]): Future[B] =
    flatMap[B](ev)

  /**
   * Returns an identical future except that it ignores interrupts which match a predicate
   */
  def mask(pred: PartialFunction[Throwable, Boolean]): Future[A] = {
    val p = Promise[A]()
    p.setInterruptHandler {
      case t if !PartialFunction.cond(t)(pred) => this.raise(t)
    }
    this.proxyTo(p)
    p
  }

  /**
   * Returns an identical future that ignores all interrupts
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
   */
  def liftToTry: Future[Try[A]] = transform(Future.toValue)

  /**
   * Lowers a `Future[Try[T]]` into a `Future[T]`.
   */
  def lowerFromTry[B](implicit ev: A <:< Try[B]): Future[B] =
    this.flatMap(Future.lowerFromTry)

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
   */
  def interruptible(): Future[A] = {
    if (isDefined) return this

    val p = Promise.attached(this)
    p setInterruptHandler { case t: Throwable =>
      if (p.detach())
        p.setException(t)
    }
    p
  }
}

/**
 * A `Future` that is already completed.
 *
 * These are cheap in construction compared to `Promises`.
 */
class ConstFuture[A](result: Try[A]) extends Future[A] {

  // It is not immediately obvious why `ConstFuture` uses the `Scheduler`
  // instead of executing the `k` immediately and inline.
  // The first is that this allows us to unwind the stack and thus do Future
  // "recursion". See
  // https://twitter.github.io/util/guide/util-cookbook/futures.html#future-recursion
  // for details. The second is that this keeps the execution order consistent
  // with `Promise`.
  def respond(k: Try[A] => Unit): Future[A] = {
    val saved = Local.save()
    Scheduler.submit(new Runnable {
      def run(): Unit = {
        val current = Local.save()
        Local.restore(saved)
        try k(result)
        catch Monitor.catcher
        finally Local.restore(current)
      }
    })
    this
  }

  def raise(interrupt: Throwable): Unit = ()

  def transform[B](f: Try[A] => Future[B]): Future[B] = {
    val p = new Promise[B]
    // see the note on `respond` for an explanation of why `Scheduler` is used.
    val saved = Local.save()
    Scheduler.submit(new Runnable {
      def run(): Unit = {
        val current = Local.save()
        Local.restore(saved)
        val computed = try f(result)
        catch {
          case e: NonLocalReturnControl[_] => Future.exception(new FutureNonLocalReturnControl(e))
          case NonFatal(e) => Future.exception(e)
          case t: Throwable =>
            Monitor.handle(t)
            throw t
        }
        finally Local.restore(current)
        p.become(computed)
      }
    })
    p
  }

  def poll: Option[Try[A]] = Some(result)

  override def isDefined: Boolean = true

  override def isDone(implicit ev: this.type <:< Future[Unit]): Boolean = result.isReturn

  override def toString: String = s"ConstFuture($result)"

  // Awaitable
  @throws(classOf[TimeoutException])
  @throws(classOf[InterruptedException])
  def ready(timeout: Duration)(implicit permit: Awaitable.CanAwait): this.type = this

  @throws(classOf[Exception])
  def result(timeout: Duration)(implicit permit: Awaitable.CanAwait): A = result()

  def isReady(implicit permit: Awaitable.CanAwait): Boolean = true
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
def join[%s](%s): Future[(%s)] = join(Seq(%s)) map { _ => (%s) }""".format(
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
  def join[A,B](a: Future[A],b: Future[B]): Future[(A,B)] = Future.join(Seq(a,b)) map { _ => (Await.result(a),Await.result(b)) }
  /**
   * Join 3 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C](a: Future[A],b: Future[B],c: Future[C]): Future[(A,B,C)] = Future.join(Seq(a,b,c)) map { _ => (Await.result(a),Await.result(b),Await.result(c)) }
  /**
   * Join 4 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D](a: Future[A],b: Future[B],c: Future[C],d: Future[D]): Future[(A,B,C,D)] = Future.join(Seq(a,b,c,d)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d)) }
  /**
   * Join 5 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E]): Future[(A,B,C,D,E)] = Future.join(Seq(a,b,c,d,e)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e)) }
  /**
   * Join 6 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F]): Future[(A,B,C,D,E,F)] = Future.join(Seq(a,b,c,d,e,f)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f)) }
  /**
   * Join 7 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G]): Future[(A,B,C,D,E,F,G)] = Future.join(Seq(a,b,c,d,e,f,g)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g)) }
  /**
   * Join 8 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H]): Future[(A,B,C,D,E,F,G,H)] = Future.join(Seq(a,b,c,d,e,f,g,h)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h)) }
  /**
   * Join 9 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I]): Future[(A,B,C,D,E,F,G,H,I)] = Future.join(Seq(a,b,c,d,e,f,g,h,i)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i)) }
  /**
   * Join 10 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J]): Future[(A,B,C,D,E,F,G,H,I,J)] = Future.join(Seq(a,b,c,d,e,f,g,h,i,j)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j)) }
  /**
   * Join 11 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K]): Future[(A,B,C,D,E,F,G,H,I,J,K)] = Future.join(Seq(a,b,c,d,e,f,g,h,i,j,k)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k)) }
  /**
   * Join 12 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L]): Future[(A,B,C,D,E,F,G,H,I,J,K,L)] = Future.join(Seq(a,b,c,d,e,f,g,h,i,j,k,l)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l)) }
  /**
   * Join 13 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M)] = Future.join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m)) }
  /**
   * Join 14 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N)] = Future.join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n)) }
  /**
   * Join 15 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O)] = Future.join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n),Await.result(o)) }
  /**
   * Join 16 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P)] = Future.join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n),Await.result(o),Await.result(p)) }
  /**
   * Join 17 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q)] = Future.join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n),Await.result(o),Await.result(p),Await.result(q)) }
  /**
   * Join 18 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q],r: Future[R]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R)] = Future.join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n),Await.result(o),Await.result(p),Await.result(q),Await.result(r)) }
  /**
   * Join 19 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q],r: Future[R],s: Future[S]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S)] = Future.join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n),Await.result(o),Await.result(p),Await.result(q),Await.result(r),Await.result(s)) }
  /**
   * Join 20 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q],r: Future[R],s: Future[S],t: Future[T]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T)] = Future.join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n),Await.result(o),Await.result(p),Await.result(q),Await.result(r),Await.result(s),Await.result(t)) }
  /**
   * Join 21 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q],r: Future[R],s: Future[S],t: Future[T],u: Future[U]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U)] = Future.join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n),Await.result(o),Await.result(p),Await.result(q),Await.result(r),Await.result(s),Await.result(t),Await.result(u)) }
  /**
   * Join 22 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q],r: Future[R],s: Future[S],t: Future[T],u: Future[U],v: Future[V]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V)] = Future.join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v)) map { _ => (Await.result(a),Await.result(b),Await.result(c),Await.result(d),Await.result(e),Await.result(f),Await.result(g),Await.result(h),Await.result(i),Await.result(j),Await.result(k),Await.result(l),Await.result(m),Await.result(n),Await.result(o),Await.result(p),Await.result(q),Await.result(r),Await.result(s),Await.result(t),Await.result(u),Await.result(v)) }

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
    Future.select(fs.asScala) map { case (first, rest) =>
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
    Future.collect(fs.asScala) map { _.asJava }

  /**
   * Collect the results from the given map `fs` of futures into a new future
   * of map. If one or more of the given Futures is exceptional, the resulting Future
   * result will the first exception encountered.
   */
  def collect[A, B](fs: JMap[A, Future[B]]): Future[JMap[A, B]] =
    Future.collect(fs.asScala.toMap) map { _.asJava }

  /**
   * Collect the results from the given futures into a new future of List[Try[A]]
   *
   * @param fs a java.util.List of Futures
   * @return a `Future[java.util.List[Try[A]]]` containing the collected values from fs.
   */
  def collectToTry[A](fs: JList[Future[A]]): Future[JList[Try[A]]] =
    Future.collectToTry(fs.asScala) map { _.asJava }

  /**
   * Flattens a nested future.  Same as ffa.flatten, but easier to call from Java.
   */
  def flatten[A](ffa: Future[Future[A]]): Future[A] = ffa.flatten

  /**
   * Lowers a Future[Try[T]] into a Future[T].
   */
  def lowerFromTry[T](f: Future[Try[T]]): Future[T] = f.lowerFromTry
}

/**
 * A [[Future]] which can never be satisfied and is thus always in
 * the pending state.
 *
 * @see [[Future.never]] for an instance of it.
 */
class NoFuture extends Future[Nothing] {
  def respond(k: Try[Nothing] => Unit): Future[Nothing] = this
  def transform[B](f: Try[Nothing] => Future[B]): Future[B] = this

  def raise(interrupt: Throwable): Unit = ()

  // Awaitable
  private[this] def sleepThenTimeout(timeout: Duration): TimeoutException = {
    Thread.sleep(timeout.inMilliseconds)
    new TimeoutException(timeout.toString)
  }

  @throws(classOf[TimeoutException])
  @throws(classOf[InterruptedException])
  def ready(timeout: Duration)(implicit permit: Awaitable.CanAwait): this.type = {
    throw sleepThenTimeout(timeout)
  }

  @throws(classOf[Exception])
  def result(timeout: Duration)(implicit permit: Awaitable.CanAwait): Nothing = {
    throw sleepThenTimeout(timeout)
  }

  def poll: Option[Try[Nothing]] = None

  def isReady(implicit permit: Awaitable.CanAwait): Boolean = false
}

class FutureTask[A](fn: => A) extends Promise[A] with Runnable {
  def run(): Unit = {
    update(Try(fn))
  }
}

object FutureTask {
  def apply[A](fn: => A): FutureTask[A] = new FutureTask[A](fn)
}
