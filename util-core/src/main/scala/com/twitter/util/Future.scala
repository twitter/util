package com.twitter.util

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference, AtomicReferenceArray}
import java.util.concurrent.{CancellationException, TimeUnit, Future => JavaFuture}

import scala.collection.JavaConversions.{asScalaBuffer, seqAsJavaList}

import com.twitter.concurrent.{Offer, Scheduler, Tx}

object Future {
  val DEFAULT_TIMEOUT = Duration.Top
  val Unit = apply(())
  val Void = apply[Void](null: Void)
  val Done = Unit
  val None: Future[Option[Nothing]] = new ConstFuture(Return.None)
  val Nil: Future[Seq[Nothing]] = new ConstFuture(Return.Nil)
  val True: Future[Boolean] = new ConstFuture(Return.True)
  val False: Future[Boolean] = new ConstFuture(Return.False)

  private val toUnit: Any => Future[Unit] = scala.Function.const(Unit)
  private val toVoid: Any => Future[Void] = scala.Function.const(Void)

  // Exception used to raise on Futures.
  private[this] val RaiseException = new Exception with NoStacktrace
  @inline private final def raiseException = RaiseException

  /**
   * Makes a Future with a constant result.
   */
  def const[A](result: Try[A]): Future[A] = new ConstFuture[A](result)

  /**
   * Make a Future with a constant value. E.g., Future.value(1) is a Future[Int].
   */
  def value[A](a: A): Future[A] = const[A](Return(a))

  /**
   * Make a Future with an error. E.g., Future.exception(new
   * Exception("boo")).
   */
  def exception[A](e: Throwable): Future[A] = const[A](Throw(e))

  /**
   * Make a Future with an error. E.g., Future.exception(new
   * Exception("boo")). The exception is not wrapped in any way.
   */
  def rawException[A](e: Throwable): Future[A] = const[A](Throw(e))

  /**
   * A new future that can never complete.
   */
  val never: Future[Nothing] = new NoFuture

  /**
   * A unit future that completes after `howlong`.
   */
  def sleep(howlong: Duration)(implicit timer: Timer): Future[Unit] = {
    if (howlong == Duration.Zero)
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

  @deprecated("Prefer static Future.Void.", "5.x")
  def void(): Future[Void] = value[Void](null)

  /**
   * A factory function to "lift" computations into the Future monad.
   * It will catch nonfatal (see: [[com.twitter.util.NonFatal]])
   * exceptions and wrap them in the Throw[_] type. Non-exceptional
   * values are wrapped in the Return[_] type.
   */
  def apply[A](a: => A): Future[A] = const(Try(a))

  def unapply[A](f: Future[A]): Option[Try[A]] = f.poll

  /**
   * Run the computation {{mkFuture}} while installing a monitor that
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
    // We define this outside the scope of the following
    // Promise to guarantee that it is not captured by any
    // closures.
    val promiseRef = new AtomicReference[Promise[A]]
    val monitor = Monitor.mk { case exc =>
      promiseRef.getAndSet(null) match {
        case null => false
        case p =>
          p.raise(exc)
          p.setException(exc)
          true
      }
    }

    val p = new Promise[A]
    promiseRef.set(p)
    monitor {
      val f = mkFuture
      p.forwardInterruptsTo(f)
      f respond { r =>
        promiseRef.getAndSet(null) match {
          case null => ()
          case p => p.update(r)
        }
      }
    }

    p
  }

  /**
   * Flattens a nested future.  Same as ffa.flatten, but easier to call from Java.
   */
  def flatten[A](ffa: Future[Future[A]]): Future[A] = ffa.flatten

  /**
   * Take a sequence of Futures, wait till they all complete
   * successfully.  The future fails immediately if any of the joined
   * Futures do, mimicking the semantics of exceptions.
   *
   * @param fs a sequence of Futures
   * @return a Future[Unit] whose value is populated when all of the fs return.
   */
  def join[A](fs: Seq[Future[A]]): Future[Unit] = {
    if (fs.isEmpty) Unit else {
      val count = new AtomicInteger(fs.size)
      val p = Promise.interrupts[Unit](fs:_*)
      for (f <- fs) {
        f respond {
          case Return(_) =>
            if (count.decrementAndGet() == 0)
              p.update(Return.Unit)
          case Throw(cause) =>
            p.updateIfEmpty(Throw(cause))
        }
      }
      p
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
   * Take a sequence of Futures, wait till they all complete
   * successfully.  The future fails immediately if any of the joined
   * Futures do, mimicking the semantics of exceptions.
   *
   * @param fs a java.util.List of Futures
   * @return a Future[Unit] whose value is populated when all of the fs return.
   */
  def join[A](fs: java.util.List[Future[A]]): Future[Unit] = join(asScalaBuffer(fs))

  /**
   * Collect the results from the given futures into a new future of
   * Seq[A]. If one or of the given Futures is exceptional, the resulting
   * Future result will be the first exception encountered.
   *
   * @param fs a sequence of Futures
   * @return a Future[Seq[A]] containing the collected values from fs.
   */
  def collect[A](fs: Seq[Future[A]]): Future[Seq[A]] = {
    if (fs.isEmpty) {
      Future(Seq[A]())
    } else {
      val fsSize = fs.size
      val results = new AtomicReferenceArray[A](fsSize)
      val count = new AtomicInteger(fsSize)
      val p = Promise.interrupts[Seq[A]](fs:_*)
      for ((f,i) <- fs.iterator.zipWithIndex) {
        f respond {
          case Return(x) =>
            results.set(i, x)
            if (count.decrementAndGet() == 0) {
              val resultsArray = for (j <- 0 until fsSize) yield results.get(j)
              p.setValue(resultsArray)
            }
          case Throw(cause) =>
            p.updateIfEmpty(Throw(cause))
        }
      }
      p
    }
  }

  /**
   * Collect the results from the given futures into a new future of
   * Seq[A]. If one or of the given futures is exceptional, the resulting
   * future result will be the first exception encountered.
   *
   * @param fs a java.util.List of Futures
   * @return a Future[java.util.List[A]] containing the collected values from fs.
   */
  def collect[A](fs: java.util.List[Future[A]]): Future[java.util.List[A]] =
    collect(asScalaBuffer(fs)) map(seqAsJavaList(_))

  /**
   * Collect the results from the given futures into a new future of Seq[Try[A]]
   *
   * @param fs a sequence of Futures
   * @return a Future[Seq[Try[A]]] containing the collected values from fs.
   */
  def collectToTry[A](fs: Seq[Future[A]]): Future[Seq[Try[A]]] =
    Future.collect(fs map(_.liftToTry))

  /**
   * Collect the results from the given futures into a new future of Seq[Try[A]]
   *
   * @param fs a java.util.List of Futures
   * @return a Future[java.util.List[Try[A]]] containing the collected values from fs.
   */
  def collectToTry[A](fs: java.util.List[Future[A]]): Future[java.util.List[Try[A]]] =
    collectToTry(asScalaBuffer(fs)) map(seqAsJavaList(_))

  /**
   * "Select" off the first future to be satisfied.  Return this as a
   * result, with the remainder of the Futures as a sequence.
   *
   * @param fs a scala.collection.Seq
   */
  def select[A](fs: Seq[Future[A]]): Future[(Try[A], Seq[Future[A]])] = {
    if (fs.isEmpty) {
      Future.exception(new IllegalArgumentException("empty future list!"))
    } else {
      val p = Promise.interrupts[(Try[A], Seq[Future[A]])](fs:_*)
      fs foreach { f =>
        f respond { res =>
          if (!p.isDefined) {
            val others = fs filterNot { _ eq f }
            p.updateIfEmpty(Return((res, others)))
          }
        }
      }
      p
    }
  }

  /**
   * Select the index into `fs` of the first future to be satisfied.
   *
   * @param fs cannot be empty
   */
  def selectIndex[A](fs: IndexedSeq[Future[A]]): Future[Int] = {
    if (fs.isEmpty) {
      Future.exception(new IllegalArgumentException("empty future list"))
    } else {
      val p = Promise.interrupts[Int](fs:_*)
      var i = 0
      while (i < fs.size) {
        val ii = i
        fs(i) respond { res =>
          if (!p.isDefined)
            p.updateIfEmpty(Return(ii))
        }
        i += 1
      }
      p
    }
  }

  /**
   * "Select" off the first future to be satisfied.  Return this as a
   * result, with the remainder of the Futures as a sequence.
   *
   * @param fs a java.util.List
   * @return a Future[Tuple2[Try[A], java.util.List[Future[A]]]] representing the first future
   * to be satisfied and the rest of the futures.
   */
  def select[A](fs: java.util.List[Future[A]]): Future[(Try[A], java.util.List[Future[A]])] = {
    select(asScalaBuffer(fs)) map { case (first, rest) =>
      (first, seqAsJavaList(rest))
    }
  }

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

  def parallel[A](n: Int)(f: => Future[A]): Seq[Future[A]] = {
    (0 until n) map { i => f }
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
  ): In => Future[Out] = {
    new BatchExecutor[In, Out](sizeThreshold, timeThreshold, sizePercentile, f)
  }
}

class FutureCancelledException
  extends Exception("The future was cancelled with Future.cancel")

/**
 * An alternative interface for handling Future Events. This
 * interface is designed to be friendly to Java users since it does
 * not require creating many small Function objects.
 */
trait FutureEventListener[T] {
  /**
   * Invoked if the computation completes successfully
   */
  def onSuccess(value: T): Unit

  /**
   * Invoked if the computation completes unsuccessfully
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
 * '''Note:''' an exception e thrown in any of
 * map/flatMap/handle/rescue will make the result of transformedBy be
 * equivalent to Future.exception(e).
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
  def map(value: A): B = throw new AbstractMethodError

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
 * A computation evaluated asynchronously. This implementation of
 * Future does not assume any concrete implementation; in particular,
 * it does not couple the user to a specific executor or event loop.
 *
 * Futures are also [[com.twitter.util.Cancellable]], but with
 * special semantics: the cancellation signal is only guaranteed to
 * be delivered when the promise has not yet completed.
 */
abstract class Future[+A] extends Awaitable[A] {
  import Future.DEFAULT_TIMEOUT

  /**
   * When the computation completes, invoke the given callback
   * function. Respond() yields a Try (either a Return or a Throw).
   * This method is most useful for very generic code (like
   * libraries). Otherwise, it is a best practice to use one of the
   * alternatives (onSuccess(), onFailure(), etc.). Note that almost
   * all methods on Future[_] are written in terms of respond(), so
   * this is the essential template method for use in concrete
   * subclasses.
   *
   * @return a chained Future[A]
   */
  def respond(k: Try[A] => Unit): Future[A]

  /**
   * Invoked regardless of whether the computation completed successfully or unsuccessfully.
   * Implemented in terms of `respond` so that subclasses control evaluation order. Returns a
   * chained Future.
   */
  def ensure(f: => Unit): Future[A] = respond { _ => f }

  /**
   * Block indefinitely, wait for the result of the Future to be available.
   */
  @deprecated("Use Await.result", "6.2.x")
  def apply(): A = apply(DEFAULT_TIMEOUT)

  /**
   * Block, but only as long as the given Timeout.
   */
  @deprecated("Use Await.result", "6.2.x")
  def apply(timeout: Duration): A = get(timeout)()

  /**
   * Alias for apply().
   */
  @deprecated("Use Await.result", "6.2.x")
  def get() = apply()

  @deprecated("Use Await.result", "6.2.x")
  def isReturn = get(DEFAULT_TIMEOUT) isReturn
  @deprecated("Use Await.result", "6.2.x")
  def isThrow  = get(DEFAULT_TIMEOUT) isThrow

  /**
   * Is the result of the Future available yet?
   */
  def isDefined: Boolean = poll.isDefined

  /**
   * Checks whether a Unit-typed Future is done. By
   * convention, futures of type Future[Unit] are used
   * for signalling.
   */
  def isDone(implicit ev: this.type <:< Future[Unit]) =
    ev(this).poll == Some(Return.Unit)

  /**
   * Demands that the result of the future be available within
   * `timeout`. The result is a Return[_] or Throw[_] depending upon
   * whether the computation finished in time.
   */
  @deprecated("Use Await.result", "6.2.x")
  final def get(timeout: Duration): Try[A] =
    try {
      Return(Await.result(this, timeout))
    } catch {
      // For legacy reasons, we catch even
      // fatal exceptions.
      case e: Throwable => Throw(e)
    }

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
  def raise(interrupt: Throwable)

  @deprecated("Provided for API compatibility; use raise() instead.", "6.0.0")
  def cancel() { raise(new FutureCancelledException) }

  /**
   * Same as the other raiseWithin, but with an implicit timer. Sometimes this is more convenient.
   *
   * ''Note'': On timeout, the underlying future is interrupted.
   */
  def raiseWithin(timeout: Duration)(implicit timer: Timer): Future[A] =
    raiseWithin(timer, timeout, new TimeoutException(timeout.toString))

  /**
   * Same as the other raiseWithin, but with an implicit timer. Sometimes this is more convenient.
   *
   * ''Note'': On timeout, the underlying future is interrupted.
   */
  def raiseWithin(timeout: Duration, exc: Throwable)(implicit timer: Timer): Future[A] =
    raiseWithin(timer, timeout, exc)

  /**
   * Returns a new Future that will error if this Future does not return in time.
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
   * Same as the other within, but with an implicit timer. Sometimes this is more convenient.
   *
   * ''Note'': On timeout, the underlying future is not interrupted.
   */
  def within(timeout: Duration)(implicit timer: Timer): Future[A] =
    within(timer, timeout)

  /**
   * Returns a new Future that will error if this Future does not return in time.
   *
   * ''Note'': On timeout, the underlying future is not interrupted.
   */
  def within(timer: Timer, timeout: Duration): Future[A] =
    within(timer, timeout, new TimeoutException(timeout.toString))

  /**
   * Returns a new Future that will error if this Future does not return in time.
   *
   * ''Note'': On timeout, the underlying future is not interrupted.
   *
   * @param timer to run timeout on.
   * @param timeout indicates how long you are willing to wait for the result to be available.
   * @param exc exception to throw.
   */
  def within(timer: Timer, timeout: Duration, exc: => Throwable): Future[A] = {
    if (timeout == Duration.Top || isDefined)
      return this

    val p = Promise.interrupts[A](this)
    val task = timer.schedule(timeout.fromNow) {
      p.updateIfEmpty(Throw(exc))
    }
    respond { r =>
      task.cancel()
      p.updateIfEmpty(r)
    }
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

  def transform[B](f: Try[A] => Future[B]): Future[B]

  /**
   * If this, the original future, succeeds, run f on the result.
   *
   * The returned result is a Future that is satisfied when the original future
   * and the callback, f, are done.
   * If the original future fails, this one will also fail, without executing f.
   *
   * @see map()
   */
  def flatMap[B](f: A => Future[B]): Future[B] =
    transform({
      case Return(v) => f(v)
      case Throw(t) => Future.rawException(t)
    })

  /**
   * Sequentially compose `this` with `f`. This is as `flatMap`, but
   * discards the result of `this`. Note that this applies only
   * `Unit`-valued  Futures -- i.e. side-effects..
   */
  def before[B](f: => Future[B])(implicit ev: this.type <:< Future[Unit]): Future[B] =
    transform({
      case Return(_) => f
      case Throw(t) => Future.rawException(t)
    })

  def rescue[B >: A](
    rescueException: PartialFunction[Throwable, Future[B]]
  ): Future[B] = transform({
    case Throw(t) if rescueException.isDefinedAt(t) => rescueException(t)
    case _ => this
  })

  /**
   * Invoke the callback only if the Future returns successfully. Useful for Scala `for`
   * comprehensions. Use `onSuccess` instead of this method for more readable code.
   */
  def foreach(k: A => Unit) = onSuccess(k)

  /**
   * If this, the original future, succeeds, run f on the result.
   *
   * The returned result is a Future that is satisfied when the original future
   * and the callback, f, are done.
   * If the original future fails, this one will also fail, without executing f.
   *
   * @see flatMap()
   */
  def map[B](f: A => B): Future[B] = flatMap { a => Future { f(a) } }

  def filter(p: A => Boolean): Future[A] = transform { x: Try[A] => Future.const(x.filter(p)) }

  def withFilter(p: A => Boolean): Future[A] = filter(p)

  /**
   * Invoke the function on the result, if the computation was
   * successful.  Returns a chained Future as in `respond`.
   *
   * @return chained Future
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
   * @return chained Future
   */
  def onFailure(rescueException: Throwable => Unit): Future[A] =
    respond({
      case Throw(throwable) => rescueException(throwable)
      case _ =>
    })

  /**
   * Register a FutureEventListener to be invoked when the
   * computation completes. This method is typically used by Java
   * programs because it avoids the use of small Function objects.
   *
   * Compare this method to `transformedBy`. The difference is that
   * `addEventListener` is used to perform a simple action when a
   * computation completes, such as recording data in a log-file. It
   * analogous to a `void` method in Java: it has side-effects and no
   * return value. `transformedBy`, on the other hand, is used to
   * transform values from one type to another, or to chain a series
   * of asynchronous calls and return the result. It is analogous to
   * methods in Java that have a return-type. Note that
   * `transformedBy` and `addEventListener` are not mutually
   * exclusive and may be profitably combined.
   */
  def addEventListener(listener: FutureEventListener[_ >: A]) = respond({
    case Throw(cause)  => listener.onFailure(cause)
    case Return(value) => listener.onSuccess(value)
  })

  /**
   * Transform the Future[A] into a Future[B] using the
   * FutureTransformer. The FutureTransformer handles both success
   * (Return) and failure (Throw) values by implementing map/flatMap
   * and handle/rescue. This method is typically used by Java
   * programs because it avoids the use of small Function objects.
   *
   * Compare this method to `addEventListener`. The difference is
   * that `addEventListener` is used to perform a simple action when
   * a computation completes, such as recording data in a log-file.
   * It analogous to a `void` method in Java: it has side-effects and
   * no return value. `transformedBy`, on the other hand, is used to
   * transform values from one type to another, or to chain a series
   * of asynchronous calls and return the result. It is analogous to
   * methods in Java that have a return-type. Note that
   * `transformedBy` and `addEventListener` are not mutually
   * exclusive and may be profitably combined.
   *
   * ''Note'': The FutureTransformer must implement either `flatMap`
   * or `map` and may optionally implement `handle`. Failing to
   * implement a method will result in a run-time (AbstractMethod)
   * error.
   */
  def transformedBy[B](transformer: FutureTransformer[A, B]): Future[B] =
    transform {
      case Return(v) => transformer.flatMap(v)
      case Throw(t)  => transformer.rescue(t)
    }

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
    other respond { p.updateIfEmpty(_) }
    this  respond { p.updateIfEmpty(_) }
    p
  }

  /**
   * A synonym for select(): Choose the first Future to be satisfied.
   */
  def or[U >: A](other: Future[U]): Future[U] = select(other)

  /**
   * Combines two Futures into one Future of the Tuple of the two results.
   */
  def join[B](other: Future[B]): Future[(A, B)] = {
    val p = Promise.interrupts[(A, B)](this, other)
    this.respond {
      case Throw(t) => p() = Throw(t)
      case Return(a) => other respond {
        case Throw(t) => p() = Throw(t)
        case Return(b) => p() = Return((a, b))
      }
    }
    p
  }

  /**
   * Convert this Future[A] to a Future[Unit] by discarding the result.
   */
  def unit: Future[Unit] = flatMap(Future.toUnit)

  /**
   * Convert this Future[A] to a Future[Void] by discarding the result.
   */
  def voided: Future[Void] = flatMap(Future.toVoid)

  @deprecated("'void' is a reserved word in javac.", "5.x")
  def void: Future[Void] = voided

  /**
   * Send updates from this Future to the other.
   * ``other'' must not yet be satisfied.
   */
  def proxyTo[B >: A](other: Promise[B]) {
    respond { other() = _ }
  }

  /**
   * An offer for this future.  The offer is activated when the future
   * is satisfied.
   */
  def toOffer: Offer[Try[A]] = new Offer[Try[A]] {
    def prepare() = transform { res: Try[A] =>
      val tx = new Tx[Try[A]] {
        def ack() = Future.value(Tx.Commit(res))
        def nack() {}
      }

      Future.value(tx)
    }
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
   * Converts a Future[Future[B]] into a Future[B]
   */
  def flatten[B](implicit ev: A <:< Future[B]): Future[B] =
    flatMap[B] { x => x }

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
  def masked: Future[A] = mask {
    case _ => true
  }

  /**
   * Returns a Future[Boolean] indicating whether two Futures are equivalent. Note that
   * Future.exception(e).willEqual(Future.exception(e)) == Future.value(true).
   */
  def willEqual[B](that: Future[B]) = {
    val areEqual = new Promise[Boolean]
    this respond { thisResult =>
      that respond { thatResult =>
        areEqual.setValue(thisResult == thatResult)
      }
    }
    areEqual
  }

  /**
   * Returns the result of the computation as a Future[Try[A]].
   */
  def liftToTry: Future[Try[A]] = transform(Future.value)
}

/**
 * A Future that is already completed. These are cheap in
 * construction compared to Promises.
 */
class ConstFuture[A](result: Try[A]) extends Future[A] {
  def respond(k: Try[A] => Unit): Future[A] = {
    val saved = Local.save()
    Scheduler.submit(new Runnable {
      def run() {
        val current = Local.save()
        Local.restore(saved)
        try Monitor { k(result) } finally Local.restore(current)
      }
    })
    this
  }

  def raise(interrupt: Throwable) {}

  def transform[B](f: Try[A] => Future[B]): Future[B] = {
    val p = new Promise[B]
    respond({ r =>
      val result = try f(r) catch { case NonFatal(e) => Future.exception(e) }
      p.become(result)
    })
    p
  }

  def poll: Option[Try[A]] = Some(result)

  override def isDefined = true

  // Awaitable
  @throws(classOf[TimeoutException])
  @throws(classOf[InterruptedException])
  def ready(timeout: Duration)(implicit permit: Awaitable.CanAwait): this.type = this

  @throws(classOf[Exception])
  def result(timeout: Duration)(implicit permit: Awaitable.CanAwait): A = result()

  def isReady(implicit permit: Awaitable.CanAwait) = true
}

/**
 * A future with no future (never completes).
 */
class NoFuture extends Future[Nothing] {
  def respond(k: Try[Nothing] => Unit): Future[Nothing] = this
  def transform[B](f: Try[Nothing] => Future[B]): Future[B] = this

  def raise(interrupt: Throwable) {}

  // Awaitable
  @throws(classOf[TimeoutException])
  @throws(classOf[InterruptedException])
  def ready(timeout: Duration)(implicit permit: Awaitable.CanAwait): this.type = {
    Thread.sleep(timeout.inMilliseconds)
    throw new TimeoutException(timeout.toString)
  }

  @throws(classOf[Exception])
  def result(timeout: Duration)(implicit permit: Awaitable.CanAwait): Nothing = {
    Thread.sleep(timeout.inMilliseconds)
    throw new TimeoutException(timeout.toString)
  }

  def poll: Option[Try[Nothing]] = None

  def isReady(implicit permit: Awaitable.CanAwait) = false
}

class FutureTask[A](fn: => A) extends Promise[A] with Runnable {
  def run() {
    update(Try(fn))
  }
}

object FutureTask {
  def apply[A](fn: => A) = new FutureTask[A](fn)
}

private[util] object FutureBenchmark {
  /**
   * Admittedly, this is not very good microbenchmarking technique.
   */

  import com.twitter.conversions.storage._
  private[this] val NumIters = 100.million

  private[this] def bench[A](numIters: Long)(f: => A): Long = {
    val begin = System.currentTimeMillis()
    (0L until numIters) foreach { _ => f }
    System.currentTimeMillis() - begin
  }

  private[this] def run[A](name: String)(work: => A) {
    printf("Warming up %s.. ", name)
    val warmupTime = bench(NumIters)(work)
    printf("%d ms\n", warmupTime)

    printf("Running .. ")
    val runTime = bench(NumIters)(work)

    printf(
      "%d ms, %d %s/sec\n",
      runTime, 1000 * NumIters / runTime, name)
  }

  def main(args: Array[String]) {
    run("respond") {
      val promise = new Promise[Unit]
      promise respond { res => () }
      promise() = Return.Unit
    }

    run("flatMaps") {
      val promise = new Promise[Unit]
      promise flatMap { _ => Future.value(()) }
      promise() = Return.Unit
    }
  }
}

private[util] object Leaky {
  def main(args: Array[String]) {
    def loop(i: Int): Future[Int] = Future.value(i) flatMap { count =>
      if (count % 1000000 == 0) {
        System.gc()
        println("iter %d %dMB".format(
          count, Runtime.getRuntime().totalMemory()>>20))
      }
      loop(count + 1)
    }

    loop(1)
  }
}
