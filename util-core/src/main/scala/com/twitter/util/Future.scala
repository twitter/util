package com.twitter.util

import com.twitter.concurrent.{Offer, IVar, Tx}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference, AtomicReferenceArray}
import java.util.concurrent.{
  CancellationException, Future => JavaFuture, TimeUnit}
import scala.annotation.tailrec
import scala.collection.JavaConversions.{asScalaBuffer, seqAsJavaList}
import scala.collection.mutable

object Future {
  val DEFAULT_TIMEOUT = Duration.MaxValue
  val Unit = apply(())
  val Void = apply[Void](null)
  val Done = Unit
  val None: Future[Option[Nothing]] = new ConstFuture(Return(Option.empty))

  /**
   * An object that can trace control flow through an asynchronous program.
   */
  trait Tracer {
    /**
     * Record the current location in the code, using the provided
     * object as a hint. Typically the provided object is a closure
     * or inner-class that has enough metadata to reconstruct
     * something resembling a stack frame.
     */
    private[util] def record(a: AnyRef)

    /**
     * Decorate an exception with additional trace data.
     * Implementations must return an exception of the same type as
     * the argument, so either mutate the given exception (which is
     * not thread-safe) or use the provided manifest to create a
     * dynamic proxy.
     */
    private[util] def wrap[T <: Throwable](t: T): T

    /**
     * Produces a sequence of StackTraceElements indicating the
     * asynchronous path that lead to the current stack-frame. This
     * method is public and is meant to help end users debug
     * difficult asynchronous control-flow.
     */
    def stackTrace: Seq[StackTraceElement]
  }

  @volatile var trace: Tracer = null
  try {
    // By default, use the standard reflection-based tracer, if it's on the classpath.
    val clazz = Class.forName("com.twitter.util.reflect.AsmFutureTracer")
    trace = clazz.newInstance.asInstanceOf[Tracer]
  } catch {
    case e: ClassNotFoundException =>
      trace = new Tracer {
        private[util] def record(a: AnyRef) {}
        private[util] def wrap[T <: Throwable](t: T) = t
        def stackTrace = Seq[StackTraceElement]()
      }
  }

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
   * Exception("boo")). The exception is wrapped using the current
   * `Future.tracer`.
   */
  def exception[A](e: Throwable): Future[A] = const[A](Throw(Future.trace.wrap(e)))

  /**
   * Make a Future with an error. E.g., Future.exception(new
   * Exception("boo")). The exception is not wrapped in any way.
   */
  def rawException[A](e: Throwable): Future[A] = const[A](Throw(e))

  /**
   * A new future that can never complete.
   */
  def never: Future[Nothing] = new NoFuture

  @deprecated("Prefer static Future.Void.", "5.x")
  def void(): Future[Void] = value[Void](null)

  /**
   * A factory function to "lift" computations into the Future monad.
   * It will catch exceptions and wrap them in the Throw[_] type.
   * Non-exceptional values are wrapped in the Return[_] type.
   */
  def apply[A](a: => A): Future[A] = const(Try(a))

  def unapply[A](f: Future[A]): Option[Try[A]] = f.poll

  /**
   * Run the computation {{f}} while installing a monitor that
   * translates any exception thrown into a coded one.  If an
   * exception is thrown anywhere, the underlying computation is
   * cancelled.
   *
   * This function is usually called to wrap a computation that
   * returns a Future (f0) whose value is satisfied by the invocation
   * of an onSuccess/onFailure/ensure callbacks of another future
   * (f1).  If an exception happens in the callbacks on f1, f0 is
   * never satisfied.  In this example, `Future.monitored { f1
   * onSuccess g; f0 }` will cancel f0 so that f0 never hangs.
   */
  def monitored[A](f: => Future[A]): Future[A] = {
    val promise = new Promise[A]
    val promiseRef = new AtomicReference(promise)
    val monitor = Monitor.mk { case exc =>
      promiseRef.getAndSet(null) match {
        case null => false
        case p =>
          p.setException(exc)
          p.cancel()
          true
      }
    }
    monitor {
      val res = f respond { r =>
        promiseRef.getAndSet(null) match {
          case null => ()
          case p => p() = r
        }
      }
      promise.linkTo(res)
    }
    promise
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
    if (fs.isEmpty) {
      Unit
    } else {
      val count = new AtomicInteger(fs.size)
      makePromise[Unit]() { promise =>
        fs foreach { f =>
          promise.linkTo(f)
          f onSuccess { _ =>
            if (count.decrementAndGet() == 0)
              promise() = Return(())
          } onFailure { cause =>
            promise.updateIfEmpty(Throw(cause))
          }
        }
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
        ps map(p => p+"()") mkString ","
      )

  meths foreach println
  '
  */

  /**
   * Join 2 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B](a: Future[A],b: Future[B]): Future[(A,B)] = join(Seq(a,b)) map { _ => (a(),b()) }
  /**
   * Join 3 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C](a: Future[A],b: Future[B],c: Future[C]): Future[(A,B,C)] = join(Seq(a,b,c)) map { _ => (a(),b(),c()) }
  /**
   * Join 4 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D](a: Future[A],b: Future[B],c: Future[C],d: Future[D]): Future[(A,B,C,D)] = join(Seq(a,b,c,d)) map { _ => (a(),b(),c(),d()) }
  /**
   * Join 5 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E]): Future[(A,B,C,D,E)] = join(Seq(a,b,c,d,e)) map { _ => (a(),b(),c(),d(),e()) }
  /**
   * Join 6 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F]): Future[(A,B,C,D,E,F)] = join(Seq(a,b,c,d,e,f)) map { _ => (a(),b(),c(),d(),e(),f()) }
  /**
   * Join 7 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G]): Future[(A,B,C,D,E,F,G)] = join(Seq(a,b,c,d,e,f,g)) map { _ => (a(),b(),c(),d(),e(),f(),g()) }
  /**
   * Join 8 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H]): Future[(A,B,C,D,E,F,G,H)] = join(Seq(a,b,c,d,e,f,g,h)) map { _ => (a(),b(),c(),d(),e(),f(),g(),h()) }
  /**
   * Join 9 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I]): Future[(A,B,C,D,E,F,G,H,I)] = join(Seq(a,b,c,d,e,f,g,h,i)) map { _ => (a(),b(),c(),d(),e(),f(),g(),h(),i()) }
  /**
   * Join 10 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J]): Future[(A,B,C,D,E,F,G,H,I,J)] = join(Seq(a,b,c,d,e,f,g,h,i,j)) map { _ => (a(),b(),c(),d(),e(),f(),g(),h(),i(),j()) }
  /**
   * Join 11 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K]): Future[(A,B,C,D,E,F,G,H,I,J,K)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k)) map { _ => (a(),b(),c(),d(),e(),f(),g(),h(),i(),j(),k()) }
  /**
   * Join 12 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L]): Future[(A,B,C,D,E,F,G,H,I,J,K,L)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l)) map { _ => (a(),b(),c(),d(),e(),f(),g(),h(),i(),j(),k(),l()) }
  /**
   * Join 13 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m)) map { _ => (a(),b(),c(),d(),e(),f(),g(),h(),i(),j(),k(),l(),m()) }
  /**
   * Join 14 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n)) map { _ => (a(),b(),c(),d(),e(),f(),g(),h(),i(),j(),k(),l(),m(),n()) }
  /**
   * Join 15 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o)) map { _ => (a(),b(),c(),d(),e(),f(),g(),h(),i(),j(),k(),l(),m(),n(),o()) }
  /**
   * Join 16 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p)) map { _ => (a(),b(),c(),d(),e(),f(),g(),h(),i(),j(),k(),l(),m(),n(),o(),p()) }
  /**
   * Join 17 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q)) map { _ => (a(),b(),c(),d(),e(),f(),g(),h(),i(),j(),k(),l(),m(),n(),o(),p(),q()) }
  /**
   * Join 18 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q],r: Future[R]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r)) map { _ => (a(),b(),c(),d(),e(),f(),g(),h(),i(),j(),k(),l(),m(),n(),o(),p(),q(),r()) }
  /**
   * Join 19 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q],r: Future[R],s: Future[S]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s)) map { _ => (a(),b(),c(),d(),e(),f(),g(),h(),i(),j(),k(),l(),m(),n(),o(),p(),q(),r(),s()) }
  /**
   * Join 20 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q],r: Future[R],s: Future[S],t: Future[T]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t)) map { _ => (a(),b(),c(),d(),e(),f(),g(),h(),i(),j(),k(),l(),m(),n(),o(),p(),q(),r(),s(),t()) }
  /**
   * Join 21 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q],r: Future[R],s: Future[S],t: Future[T],u: Future[U]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u)) map { _ => (a(),b(),c(),d(),e(),f(),g(),h(),i(),j(),k(),l(),m(),n(),o(),p(),q(),r(),s(),t(),u()) }
  /**
   * Join 22 futures. The returned future is complete when all
   * underlying futures complete. It fails immediately if any of them
   * do.
   */
  def join[A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V](a: Future[A],b: Future[B],c: Future[C],d: Future[D],e: Future[E],f: Future[F],g: Future[G],h: Future[H],i: Future[I],j: Future[J],k: Future[K],l: Future[L],m: Future[M],n: Future[N],o: Future[O],p: Future[P],q: Future[Q],r: Future[R],s: Future[S],t: Future[T],u: Future[U],v: Future[V]): Future[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V)] = join(Seq(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v)) map { _ => (a(),b(),c(),d(),e(),f(),g(),h(),i(),j(),k(),l(),m(),n(),o(),p(),q(),r(),s(),t(),u(),v()) }

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
   * Seq[A].
   *
   * @param fs a sequence of Futures
   * @return a Future[Seq[A]] containing the collected values from fs.
   */
  def collect[A](fs: Seq[Future[A]]): Future[Seq[A]] = {
    if (fs.isEmpty) {
      Future(Seq[A]())
    } else {
      val results = new AtomicReferenceArray[A](fs.size)
      val count = new AtomicInteger(fs.size)
      makePromise[Seq[A]]() { promise =>
        for (i <- 0 until fs.size) {
          val f = fs(i)
          promise.linkTo(f)
          f onSuccess { x =>
            results.set(i, x)
            if (count.decrementAndGet() == 0) {
              val resultsArray = new mutable.ArrayBuffer[A](fs.size)
              for (j <- 0 until fs.size) resultsArray += results.get(j)
              promise.setValue(resultsArray)
            }
          } onFailure { cause =>
            promise.updateIfEmpty(Throw(cause))
          }
        }
      }
    }
  }

  /**
   * Collect the results from the given futures into a new future of
   * Seq[A].
   *
   * @param fs a java.util.List of Futures
   * @return a Future[java.util.List[A]] containing the collected values from fs.
   */
  def collect[A](fs: java.util.List[Future[A]]): Future[java.util.List[A]] =
    collect(asScalaBuffer(fs)) map(seqAsJavaList(_))

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
      makePromise[(Try[A], Seq[Future[A]])](fs: _*) { promise =>
        @tailrec
        def stripe(heads: Seq[Future[A]], elem: Future[A], tail: Seq[Future[A]]) {
          elem respond { res =>
            if (!promise.isDefined) {
              promise.updateIfEmpty(Return((res, heads ++ tail)))
            }
          }

          if (!tail.isEmpty)
            stripe(heads ++ Seq(elem), tail.head, tail.tail)
        }

        stripe(Seq(), fs.head, fs.tail)
      }
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

  private[util] def makePromise[A](links: Cancellable*)(f: Promise[A] => Unit): Promise[A] = {
    val promise = new Promise[A]
    links foreach { promise.linkTo(_) }
    f(promise)
    promise
  }
}

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
 * Note that this class extends Try[_] indicating that the results of
 * the computation may succeed or fail.
 *
 * Futures are also [[com.twitter.util.Cancellable]], but with
 * special semantics: the cancellation signal is only guaranteed to
 * be delivered when the promise has not yet completed.
 */
abstract class Future[+A] extends Cancellable {
  import Future.{DEFAULT_TIMEOUT, makePromise}

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
  def respond(k: Try[A] => Unit): Future[A] = respond(k, k)

  protected def respond(tracingObject: AnyRef, k: Try[A] => Unit): Future[A]

  /**
   * Invoked regardless of whether the computation completed successfully or unsuccessfully.
   * Implemented in terms of `respond` so that subclasses control evaluation order. Returns a
   * chained Future.
   */
  def ensure(f: => Unit): Future[A] = respond { _ => f }

  /**
   * Block indefinitely, wait for the result of the Future to be available.
   */
  def apply(): A = apply(DEFAULT_TIMEOUT)

  /**
   * Block, but only as long as the given Timeout.
   */
  def apply(timeout: Duration): A = get(timeout)()

  /**
   * Alias for apply().
   */
  def get() = apply()

  def isReturn = get(DEFAULT_TIMEOUT) isReturn
  def isThrow  = get(DEFAULT_TIMEOUT) isThrow

  /**
   * Is the result of the Future available yet?
   */
  def isDefined: Boolean

  /**
   * Trigger a callback if this future is cancelled.
   */
  def onCancellation(f: => Unit)

  /**
   * Demands that the result of the future be available within
   * `timeout`. The result is a Return[_] or Throw[_] depending upon
   * whether the computation finished in time.
   */
  def get(timeout: Duration): Try[A]

  /**
   * Polls for an available result.  If the Future has been
   * satisfied, returns Some(result), otherwise None.
   */
  def poll: Option[Try[A]]

  /**
   * Same as the other within, but with an implicit timer. Sometimes this is more convenient.
   */
  def within(timeout: Duration)(implicit timer: Timer): Future[A] =
    within(timer, timeout)

  /**
   * Returns a new Future that will error if this Future does not return in time.
   *
   * @param timeout indicates how long you are willing to wait for the result to be available.
   */
  def within(timer: Timer, timeout: Duration): Future[A] = {
    makePromise[A](this) { promise =>
      val task = timer.schedule(timeout.fromNow) {
        promise.updateIfEmpty(Throw(new TimeoutException(timeout.toString)))
      }
      respond { r =>
        task.cancel()
        promise.updateIfEmpty(r)
      }
    }
  }

  protected def transform[B](tracingObject: AnyRef, f: Try[A] => Future[B]): Future[B]

  def transform[B](f: Try[A] => Future[B]): Future[B] = transform(f, f)

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
    transform(f, {
      case Return(v) => f(v)
      case Throw(t) => Future.rawException(t)
    })

  def rescue[B >: A](
    rescueException: PartialFunction[Throwable, Future[B]]
  ): Future[B] = transform(rescueException, {
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

  /**
   * Invoke the function on the result, if the computation was
   * successful.  Returns a chained Future as in `respond`.
   *
   * @return chained Future
   */
  def onSuccess(f: A => Unit): Future[A] =
    respond(f, {
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
    respond(rescueException, {
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
  def addEventListener(listener: FutureEventListener[_ >: A]) = respond(listener, {
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
   * Choose the first Future to succeed.
   *
   * @param other another Future
   * @return a new Future whose result is that of the first of this and other to return
   */
  def select[U >: A](other: Future[U]): Future[U] = {
    makePromise[U](other, this) { promise =>
      other respond { promise.updateIfEmpty(_) }
      this  respond { promise.updateIfEmpty(_) }
    }
  }

  /**
   * A synonym for select(): Choose the first Future to succeed.
   */
  def or[U >: A](other: Future[U]): Future[U] = select(other)

  /**
   * Combines two Futures into one Future of the Tuple of the two results.
   */
  def join[B](other: Future[B]): Future[(A, B)] = {
    makePromise[(A, B)](this, other) { promise =>
      respond {
        case Throw(t) => promise() = Throw(t)
        case Return(a) => other respond {
          case Throw(t) => promise() = Throw(t)
          case Return(b) => promise() = Return((a, b))
        }
      }
    }
  }

  /**
   * Convert this Future[A] to a Future[Unit] by discarding the result.
   */
  def unit: Future[Unit] = map(_ => ())

  /**
   * Convert this Future[A] to a Future[Void] by discarding the result.
   */
  def voided: Future[Void] = map(_ => null.asInstanceOf[Void])

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
      override def cancel(cancel: Boolean): Boolean = {
        if (isDone || isCancelled) {
          false
        } else {
          f.cancel()
          true
        }
      }

      override def isCancelled: Boolean = {
        f.isCancelled
      }

      override def isDone: Boolean = {
        f.isCancelled || f.isDefined
      }

      override def get(): A = {
        if (isCancelled) {
          throw new CancellationException()
        }
        f()
      }

      override def get(time: Long, timeUnit: TimeUnit): A = {
        if (isCancelled) {
          throw new CancellationException()
        }
        f.get(Duration.fromTimeUnit(time, timeUnit)) match {
          case Return(r) => r
          case Throw(e) => throw e
        }
      }
    }
  }

  /**
   * Converts a Future[Future[B]] into a Future[B]
   */
  def flatten[B](implicit ev: A <:< Future[B]): Future[B] =
    flatMap[B] { x => x }

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

}

object Promise {
  case class ImmutableResult(message: String) extends Exception(message)
}

/**
 * A concrete Future implementation that is updatable by some
 * executor or event loop.  A typical use of Promise is for a client
 * to submit a request to some service.  The client is given an
 * object that inherits from Future[_].  The server stores a
 * reference to this object as a Promise[_] and updates the value
 * when the computation completes.
 */
class Promise[A] private[Promise](
  private[Promise] final val ivar: IVar[Try[A]],
  private[Promise] final val cancelled: IVar[Unit])
  extends Future[A]
{
  import Promise._

  @volatile private[this] var chained: Future[A] = null
  def this() = this(new IVar[Try[A]], new IVar[Unit])

  override def toString = "Promise@%s(ivar=%s, cancelled=%s)".format(hashCode, ivar, cancelled)

  /**
   * Secondary constructor where result can be provided immediately.
   */
  def this(result: Try[A]) {
    this()
    this.ivar.set(result)
  }

  def isCancelled = cancelled.isDefined
  def cancel() { cancelled.set(()) }
  def linkTo(other: Cancellable) {
    cancelled.get {
      case () => other.cancel()
    }
  }

  /**
   * Invoke 'f' if this Future is cancelled.
   */
  def onCancellation(f: => Unit) {
    linkTo(new CancellableSink(f))
  }

  def get(timeout: Duration): Try[A] =
    ivar(timeout) getOrElse {
      Throw(new TimeoutException(timeout.toString))
    }

  def poll = ivar.poll

  /**
   * Merge `other` into this promise.  See
   * {{com.twitter.concurrent.IVar.merge}} for details.  This is
   * necessary in bind operations (flatMap, rescue) in order to
   * prevent space leaks under iteration.
   *
   * Cancellation state is merged along with values, but the
   * semantics are slightly different.  Because the receiver of a
   * promise may affect its cancellation status, we must allow for
   * divergence here: if `this` has been cancelled, but `other` is
   * already complete, `other` will not change its cancellation state
   * (which is fixed at false).
   */
  private[util] def merge(other: Future[A]) {
    if (other.isInstanceOf[Promise[_]]) {
      val p = other.asInstanceOf[Promise[A]]
      this.ivar.merge(p.ivar)
      this.cancelled.merge(p.cancelled)
    } else {
      other.proxyTo(this)
      this.linkTo(other)
    }
  }

  def isDefined = ivar.isDefined

  /**
   * Populate the Promise with the given result.
   *
   * @throws ImmutableResult if the Promise is already populated
   */
  def setValue(result: A) { update(Return(result)) }

  /**
   * Populate the Promise with the given exception.
   *
   * @throws ImmutableResult if the Promise is already populated
   */
  def setException(throwable: Throwable) { update(Throw(throwable)) }

  /**
   * Populate the Promise with the given Try. The try can either be a
   * value or an exception. setValue and setException are generally
   * more readable methods to use.
   *
   * @throws ImmutableResult if the Promise is already populated
   */
  def update(result: Try[A]) {
    updateIfEmpty(result) || {
      throw new ImmutableResult("Result set multiple times: " + result)
    }
  }

  /**
   * Populate the Promise with the given Try. The try can either be a
   * value or an exception. setValue and setException are generally
   * more readable methods to use.
   *
   * @return true only if the result is updated, false if it was already set.
   */
  def updateIfEmpty(newResult: Try[A]) =
    ivar.set(newResult)

  /**
   * Note: exceptions in responds are monitored.  That is, if the
   * computation {{k}} throws a raw (ie.  not encoded in a Future)
   * exception, it is handled by the current monitor, see
   * {{com.twitter.util.Monitor}} for details.
   */
  def respond(tracingObject: AnyRef, k: Try[A] => Unit): Future[A] = {
    // Note that there's a race here, but that's okay.  The resulting
    // Futures are equivalent, and it only makes the optimization
    // less effective.
    //
    // todo: given that it's likely most responds don't actually
    // result in the chained ivar being used, we could create a shell
    // promise.  this would get rid of one object allocation (the
    // chained ivar).
    if (chained eq null)
      chained = new Promise(ivar.chained, cancelled)
    respondWithoutChaining(tracingObject, { r => Monitor { k(r) } })
    chained
  }

  private[this] def respondWithoutChaining(tracingObject: AnyRef, k: Try[A] => Unit) {
    val saved = Local.save()
    ivar.get { result =>
      val current = Local.save()
      Local.restore(saved)
      Future.trace.record(tracingObject)
      try
        k(result)
      finally
        Local.restore(current)
    }
  }

  protected[this] def transform[B](tracingObject: AnyRef, f: Try[A] => Future[B]): Future[B] = {
    val promise = new Promise[B]

    val k = { _: Unit => this.cancel() }
    promise.cancelled.get(k)
    respondWithoutChaining(tracingObject, { r =>
      promise.cancelled.unget(k)
      val result =
        try
          f(r)
        catch {
          case e => Future.exception(e)
        }
      promise.merge(result)
    })
    promise
  }

  override def rescue[B >: A](
    rescueException: PartialFunction[Throwable, Future[B]]
  ): Future[B] =
    transform(rescueException, {
      case Throw(t) if rescueException.isDefinedAt(t) => rescueException(t)
      case _ => this
    })

  private[util] def depth = ivar.depth
}

/**
 * A Future that is already completed. These are cheap in
 * construction compared to Promises.
 */
class ConstFuture[A](result: Try[A]) extends Future[A] {
  def respond(tracingObject: AnyRef, k: Try[A] => Unit): Future[A] = {
    val saved = Local.save()
    IVar.sched { () =>
      val current = Local.save()
      Local.restore(saved)
      Future.trace.record(tracingObject)
      try Monitor { k(result) } finally Local.restore(current)
    }
    this
  }

  def isDefined: Boolean = true

  // We don't guarantee cancellation delivery after future
  // completion, so we simply don't provide it for const
  // futures.
  def onCancellation(f: => Unit) {}
  def linkTo(other: Cancellable) {}
  def cancel() {}
  def isCancelled: Boolean = false

  protected def transform[B](tracingObject: AnyRef, f: Try[A] => Future[B]): Future[B] = {
    val p = new Promise[B]
    respond(tracingObject, { r =>
      val result = try f(r) catch { case e => Future.exception(e) }
      p.merge(result)
    })
    p
  }

  def get(timeout: Duration): Try[A] = result
  def poll: Option[Try[A]] = Some(result)
}

/**
 * A future with no future (never completes).
 */
class NoFuture extends Future[Nothing] {
  protected def respond(tracingObject: AnyRef, k: Try[Nothing] => Unit): Future[Nothing] = this
  protected def transform[B](tracingObject: AnyRef, f: Try[Nothing] => Future[B]): Future[B] = this

  def isDefined: Boolean = false
  def onCancellation(f: =>Unit) {}
  def linkTo(other: Cancellable) {}
  def cancel() {}
  def isCancelled: Boolean = false
  def get(timeout: Duration): Try[Nothing] = {
    // We perhaps should just throw right away, but code might rely
    // on Future.get(timeout) blocking for at least that amount of
    // time.
    Thread.sleep(timeout.inMilliseconds)
    throw new TimeoutException(timeout.toString)
  }
  def poll: Option[Try[Nothing]] = None
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
      promise() = Return(())
    }

    run("flatMaps") {
      val promise = new Promise[Unit]
      promise flatMap { _ => Future.value(()) }
      promise() = Return(())
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
