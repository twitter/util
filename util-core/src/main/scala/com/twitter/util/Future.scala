package com.twitter.util

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import scala.annotation.tailrec

import com.twitter.concurrent.{Offer, IVar}
import com.twitter.conversions.time._

object Future {
  val DEFAULT_TIMEOUT = Duration.MaxValue
  val Unit = apply(())
  val Done = Unit

  /**
   * Make a Future with a constant value. E.g., Future.value(1) is a Future[Int].
   */
  def value[A](a: A): Future[A] = new Promise[A](Return(a))

  /**
   * Make a Future with an error. E.g., Future.exception(new Exception("boo"))
   */
  def exception[A](e: Throwable): Future[A] = new Promise[A](Throw(e))

  def void() = Future[Void] { null }

  /**
   * A factory function to "lift" computations into the Future monad. It will catch
   * exceptions and wrap them in the Throw[_] type. Non-exceptional values are wrapped
   * in the Return[_] type.
   */
  def apply[A](a: => A): Future[A] = new Promise[A](Try(a))

  /**
   * Take a sequence of Futures, wait till they all complete
   * succesfully.  The future fails immediately if any of the joined
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
      val promise = new Promise[Unit]

      fs foreach { f =>
        promise.linkTo(f)
        f onSuccess { _ =>
          if (count.decrementAndGet() == 0)
            promise() = Return(())
        } onFailure { cause =>
          promise.updateIfEmpty(Throw(cause))
        }
      }

      promise
    }
  }

  /**
   * Collect the results from the given futures into a new future of
   * Seq[A].
   *
   * @param fs a sequence of Futures
   * @return a Future[Seq[A]] containing the collected values from fs.
   */
  def collect[A](fs: Seq[Future[A]]): Future[Seq[A]] = {
    val collected = fs.foldLeft(Future.value(Nil: List[A])) { case (a, e) =>
      a flatMap { aa => e map { _ :: aa } }
    } map { _.reverse }

    // Cancellations don't get propagated in flatMap because the
    // computation is short circuited.  Thus we link manually to get
    // the expected behavior from collect().
    fs foreach { f => collected.linkTo(f) }

    collected
  }

  /**
   * "Select" off the first future to be satisfied.  Return this as a
   * result, with the remainder of the Futures as a sequence.
   */
  def select[A](fs: Seq[Future[A]]): Future[(Try[A], Seq[Future[A]])] = {
    if (fs.isEmpty)
      return Future.exception(new IllegalArgumentException("empty future list!"))

    val promise = new Promise[(Try[A], Seq[Future[A]])]

    fs foreach { promise.linkTo(_) }

    @tailrec
    def stripe(heads: Seq[Future[A]], elem: Future[A], tail: Seq[Future[A]]) {
      elem respond { res =>
        promise.synchronized {
          if (!promise.isDefined)
            promise() = Return((res, heads ++ tail))
        }
      }

      if (!tail.isEmpty)
        stripe(heads ++ Seq(elem), tail.head, tail.tail)
    }

    stripe(Seq(), fs.head, fs.tail)
    promise
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
    val result = new Promise[Unit]
    def iterate() {
      if (p) {
        val iteration = f
        result.linkTo(iteration)
        iteration onSuccess { _ =>
          iterate()
        } onFailure { cause =>
          result.setException(cause)
        }
      } else {
        result.setValue(())
      }
    }
    iterate()
    result
  }

  def parallel[A](n: Int)(f: => Future[A]): Seq[Future[A]] = {
    (0 until n) map { i => f }
  }
}

/**
 * An alternative interface for handling Future Events. This interface is designed
 * to be friendly to Java users since it does not require closures.
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
 * A computation evaluated asynchronously. This implementation of Future does not
 * assume any concrete implementation; in particular, it does not couple the user
 * to a specific executor or event loop.
 *
 * Note that this class extends Try[_] indicating that the results of the computation
 * may succeed or fail.
 */
abstract class Future[+A] extends TryLike[A, Future] with Cancellable {
  import Future.DEFAULT_TIMEOUT

  /**
   * When the computation completes, invoke the given callback function. Respond()
   * yields a Try (either a Return or a Throw). This method is most useful for
   * very generic code (like libraries). Otherwise, it is a best practice to use
   * one of the alternatives (onSuccess(), onFailure(), etc.). Note that almost
   * all methods on Future[_] are written in terms of respond(), so this is
   * the essential template method for use in concrete subclasses.
   */
  def respond(k: Try[A] => Unit): Future[A]

  /**
   * Block indefinitely, wait for the result of the Future to be available.
   */
  override def apply(): A = apply(DEFAULT_TIMEOUT)

  /**
   * Block, but only as long as the given Timeout.
   */
  def apply(timeout: Duration): A = get(timeout)()

  def isReturn = get(DEFAULT_TIMEOUT) isReturn
  def isThrow = get(DEFAULT_TIMEOUT) isThrow

  /**
   * Is the result of the Future available yet?
   */
  def isDefined: Boolean

  /**
   * Demands that the result of the future be available within `timeout`. The result
   * is a Return[_] or Throw[_] depending upon whether the computation finished in
   * time.
   */
  def get(timeout: Duration): Try[A] = {
    val latch = new CountDownLatch(1)
    var result: Try[A] = null
    respond { a =>
      result = a
      latch.countDown()
    }
    if (!latch.await(timeout)) {
      result = Throw(new TimeoutException(timeout.toString))
    }
    result
  }

  /**
   * Same as the other within, but with an implict timer. Sometimes this is more convenient.
   */
  def within(timeout: Duration)(implicit timer: Timer): Future[A] =
    within(timer, timeout)

  /**
   * Returns a new Future that will error if this Future does not return in time.
   *
   * @param timeout indicates how long you are willing to wait for the result to be available.
   */
  def within(timer: Timer, timeout: Duration): Future[A] = {
    val promise = new Promise[A]
    promise.linkTo(this)
    val task = timer.schedule(timeout.fromNow) {
      promise.updateIfEmpty(Throw(new TimeoutException(timeout.toString)))
    }
    respond { r =>
      task.cancel()
      promise.updateIfEmpty(r)
    }
    promise
  }

  /**
   * Invoke the callback only if the Future returns sucessfully. Useful for Scala for comprehensions.
   * Use onSuccess instead of this method for more readable code.
   */
  override def foreach(k: A => Unit) { respond(_ foreach k) }

  /**
   * Invoke the function on the result, if the computation was successful. Returns
   * `this` to allow for a fluent API. This function is like foreach but it returns
   * `this`. See `map` and `flatMap` for a less imperative API.
   *
   * @return this
   */
  def onSuccess(f: A => Unit): Future[A] = {
    respond {
      case Return(value) => f(value)
      case _ =>
    }
    this
  }

  /**
   * Invoke the funciton on the error, if the computation was unsuccessful. Returns
   * `this` to allow for a fluent API. This function is like `foreach` but for the error
   * case. It also differs from `foreach` in that it returns `this`.
   * See `rescue` and `handle` for a less imperative API.
   *
   * @return this
   */
  def onFailure(rescueException: Throwable => Unit): Future[A] = {
    respond {
      case Throw(throwable) => rescueException(throwable)
      case _ =>
    }
    this
  }

  def addEventListener(listener: FutureEventListener[_ >: A]) = respond {
    case Throw(cause)  => listener.onFailure(cause)
    case Return(value) => listener.onSuccess(value)
  }

  /**
   * Choose the first Future to succeed.
   *
   * @param other another Future
   * @return a new Future whose result is that of the first of this and other to return
   */
  def select[U >: A](other: Future[U]): Future[U] = {
    val promise = new Promise[U]
    promise.linkTo(other)
    promise.linkTo(this)
    other respond { promise.updateIfEmpty(_) }
    this  respond { promise.updateIfEmpty(_) }
    promise
  }

  /**
   * A synonym for select(): Choose the first Future to succeed.
   */
  def or[U >: A](other: Future[U]): Future[U] = select(other)

  /**
   * Combines two Futures into one Future of the Tuple of the two results.
   */
  def join[B](other: Future[B]): Future[(A, B)] = {
    val promise = new Promise[(A, B)]
    promise.linkTo(other)
    promise.linkTo(this)
    respond {
      case Return(a) =>
        other respond {
          case Return(b) => promise() = Return((a, b))
          case Throw(t)  => promise() = Throw(t)
        }
      case Throw(t) =>
        promise() = Throw(t)
    }

    promise
  }

  /**
   * Convert this Future[A] to a Future[Unit] by discarding the result.
   */
  def unit: Future[Unit] = map(_ => ())

  /**
   * Send updates from this Future to the other.
   */
  def proxyTo[B >: A](other: Promise[B]) {
    respond(other.update(_))
  }

  /**
   * An offer for this future.  The offer is activated when the future
   * is satisfied.
   */
  def toOffer: Offer[Try[A]] = new Offer[Try[A]] {
    def poll() = if (isDefined) Some(() => get(0.seconds)) else None
    def enqueue(setter: Setter) = {
      respond { v =>
        setter() foreach { set => set(() => v) }
      }
      // we can't dequeue futures
      () => ()
    }
    def objects = Seq()
  }
}

object Promise {
  case class ImmutableResult(message: String) extends Exception(message)
}

/**
 * A concrete Future implementation that is updatable by some executor or event loop.
 * A typical use of Promise is for a client to submit a request to some service.
 * The client is given an object that inherits from Future[_]. The server stores a
 * reference to this object as a Promise[_] and updates the value when the computation
 * completes.
 */
class Promise[A] extends Future[A] {
  import Promise._

  private[this] val ivar = new IVar[Try[A]]

  /**
   * Secondary constructor where result can be provided immediately.
   */
  def this(result: Try[A]) {
    this()
    this.ivar.set(result)
  }

  def isDefined = ivar.isDefined

  /**
   * Populate the Promise with the given result.
   *
   * @throws ImmutableResult if the Promise is already populated
   */
  def setValue(result: A) = update(Return(result))

  /**
   * Populate the Promise with the given exception.
   *
   * @throws ImmutableResult if the Promise is already populated
   */
  def setException(throwable: Throwable) = update(Throw(throwable))

  /**
   * Populate the Promise with the given Try. The try can either be a value
   * or an exception. setValue and setException are generally more readable
   * methods to use.
   *
   * @throws ImmutableResult if the Promise is already populated
   */
  def update(result: Try[A]) {
    updateIfEmpty(result) || {
      throw new ImmutableResult("Result set multiple times: " + result)
    }
  }

  /**
   * Populate the Promise with the given Try. The try can either be a value
   * or an exception. setValue and setException are generally more readable
   * methods to use.
   *
   * @return true or false depending on whether the result was available.
   */
  def updateIfEmpty(newResult: Try[A]) = ivar.set(newResult)

  override def respond(k: Try[A] => Unit) = {
    val saved = Locals.save()
    ivar.get { result =>
      val current = Locals.save()
      saved.restore()
      try
        k(result)
      finally
        current.restore()
    }

    this
  }

  /**
   * Invoke 'f' if this Future is cancelled.
   */
  def onCancellation(f: => Unit) {
    linkTo(new Cancellable {
      override def cancel() { f }
    })
  }

  override def map[B](f: A => B) = new Promise[B] {
    linkTo(Promise.this)
    Promise.this.respond { x =>
      update(x map(f))
    }
  }

  def flatMap[B, AlsoFuture[B] >: Future[B] <: Future[B]](f: A => AlsoFuture[B]) = new Promise[B] {
    linkTo(Promise.this)  // propagate cancellation
    Promise.this.respond {
      case Return(r) =>
        try {
          val next = f(r)
          linkTo(next)
          next respond(update(_))
        } catch {
          case e => update(Throw(e))
        }
      case Throw(e) => update(Throw(e))
    }
  }

  def rescue[B >: A, AlsoFuture[B] >: Future[B] <: Future[B]](
    rescueException: PartialFunction[Throwable, AlsoFuture[B]]
  ) = new Promise[B] {
    linkTo(Promise.this)
    Promise.this.respond {
      case r: Return[_] => update(r)
      case Throw(e) if rescueException.isDefinedAt(e) =>
        try {
          val next = rescueException(e)
          linkTo(next)
          next respond(update(_))
        } catch {
          case e => update(Throw(e))
        }
      case Throw(e) => update(Throw(e))
     }
  }

  override def filter(p: A => Boolean) = new Promise[A] {
    linkTo(Promise.this)
    Promise.this.respond { x =>
      update(x filter(p))
    }
  }

  def handle[B >: A](rescueException: PartialFunction[Throwable, B]) = rescue {
    case e: Throwable if rescueException.isDefinedAt(e) => Future(rescueException(e))
    case e: Throwable                                   => this
  }
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

  def main(args: Array[String]) {
    printf("Warming up.. ")
    val warmupTime = bench(NumIters) {
      val promise = new Promise[Unit]
      promise respond { res => () }
      promise() = Return(())
    }
    printf("%d ms\n", warmupTime)

    printf("Running .. ")
    val runTime = bench(NumIters) {
      val promise = new Promise[Unit]
      promise respond { res => () }
      promise() = Return(())
    }
    printf(
      "%d ms, %d responds/sec\n",
      runTime, 1000 * NumIters / runTime)
  }
}
