package com.twitter.util

import com.twitter.concurrent.Scheduler
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.collection.mutable
import scala.runtime.NonLocalReturnControl

object Promise {
  /**
   * A continuation stored from a promise.
   */
  private[util] trait K[-A] extends (Try[A] => Unit) {
    /** Depth tag used for scheduling */
    protected[util] def depth: Short
  }

  private object K {
    val depthOfK: K[_] => Short = _.depth
  }

  /**
   * A template trait for [[com.twitter.util.Promise Promises]] that are derived
   * and capable of being detached from other Promises.
   */
  trait Detachable { _: Promise[_] =>
    /**
     * Returns true if successfully detached, will return true at most once.
     *
     * The contract is that non-idempotent side effects should only be done after the
     * successful detach.
     */
    def detach(): Boolean
  }

  /**
   * A detachable [[com.twitter.util.Promise]].
   */
  private class DetachablePromise[A](underlying: Promise[_ <: A])
    extends Promise[A] with Promise.K[A] with Detachable
  {
    underlying.continue(this)

    def detach(): Boolean = underlying.detach(this)

    // This is only called after the parent has been successfully satisfied
    def apply(result: Try[A]): Unit = {
      update(result)
    }
  }

  private class DetachableFuture[A] extends Promise[A] with Promise.Detachable {
    private[this] var detached: Boolean = false

    def detach(): Boolean = synchronized {
      if (detached) {
        false
      } else {
        detached = true
        true
      }
    }
  }

  /**
   * A monitored continuation.
   *
   * @param saved The saved local context of the invocation site
   * @param k the closure to invoke in the saved context, with the
   * provided result
   * @param depth a tag used to store the chain depth of this context
   * for scheduling purposes.
   */
  private class Monitored[A](
      saved: Local.Context,
      k: Try[A] => Unit,
      val depth: Short)
    extends K[A]
  {
    def apply(result: Try[A]): Unit = {
      val current = Local.save()
      Local.restore(saved)
      try k(result)
      catch Monitor.catcher
      finally Local.restore(current)
    }
  }

  /**
   * A transforming continuation.
   *
   * @param saved The saved local context of the invocation site
   * @param promise The Promise for the transformed value
   * @param f The closure to invoke to produce the Future of the transformed value.
   * @param depth a tag used to store the chain depth of this context
   * for scheduling purposes.
   */
  private class Transformer[A, B](
      saved: Local.Context,
      promise: Promise[B],
      f: Try[A] => Future[B],
      val depth: Short)
    extends K[A]
  {
    private[this] def k(r: Try[A]) = {
      promise.become(
        try f(r) catch {
          case e: NonLocalReturnControl[_] => Future.exception(new FutureNonLocalReturnControl(e))
          case NonFatal(e) => Future.exception(e)
        }
      )
    }

    def apply(result: Try[A]): Unit = {
      val current = Local.save()
      Local.restore(saved)
      try k(result)
      catch { case t: Throwable =>
        Monitor.handle(t)
        throw t
      }
      finally Local.restore(current)
    }
  }

  /*
   * Performance notes:
   *
   * The following is a characteristic CDF of wait queue lengths.
   * This was retrieved by instrumenting the promise implementation
   * and running it with the 'finagle-topo' test suite.
   *
   *   0 26%
   *   1 77%
   *   2 94%
   *   3 97%
   *
   * Which amounts to .94 callbacks on average.
   *
   * Due to OOPS compression on 64-bit architectures, objects that
   * have one field are of the same size as objects with two. We
   * exploit this by explicitly caching the first callback in its own
   * field, thus avoiding additional representation overhead in 77%
   * of promises.
   *
   * todo: do this sort of profiling in a production app with
   * production load.
   *
   * Implementation notes:
   *
   * While these various states for `Promises.state` should be nicely modeled
   * as a sealed type, by omitting the wrappers around `Done` and `Linked`
   * we are able to save significant number of allocations for slightly
   * more unreadable code localized within `Promise`.
   */

  /**
   * The initial state of a Promise. `first` (which may be null)
   * and `rest` are the continuations that should be run once it
   * is satisfied.
   */
  private case class Waiting(first: K[Nothing], rest: List[K[Nothing]])

  /**
   * An unsatisfied Promise which has an interrupt handler attached to it.
   * `waitq` represents the continuations that should be run once it
   * is satisfied.
   *
   * @note `waitq` will always be a `List[K[A]]`
   */
  private case class Interruptible(
      waitq: List[K[Nothing]],
      handler: PartialFunction[Throwable, Unit])

  /**
   * An unsatisfied Promise which forwards interrupts to `other`.
   * `waitq` represents the continuations that should be run once it
   * is satisfied.
   *
   * @note `waitq` will always be a `List[K[A]]`
   */
  private case class Transforming(waitq: List[K[Nothing]], other: Future[_])

  /**
   * An unsatisified Promise that has been interrupted by `signal`.
   * `waitq` represents the continuations that should be run once it
   * is satisfied.
   *
   * @note `waitq` will always be a `List[K[A]]`
   */
  private case class Interrupted(waitq: List[K[Nothing]], signal: Throwable)

  /**
   * A Promise that has been satisfied with `result`.
   * This is stored in `Promise.state` as a raw `Try[A]`
   */
  // private case class Done[A](result: Try[A])

  /**
   * A Promise that is "linked" to another `Promise` via `Promise.become`.
   * This is stored in `Promise.state` as a raw `Promise[A]`
   */
  // private case class Linked[A](p: Promise[A])

  private[this] val emptyState: Waiting = Waiting(null, Nil)
  private def initState: Waiting = emptyState
  private val unsafe: sun.misc.Unsafe = Unsafe()
  private val stateOff: Long = unsafe.objectFieldOffset(classOf[Promise[_]].getDeclaredField("state"))
  private val AlwaysUnit: Any => Unit = scala.Function.const(()) _

  sealed trait Responder[A] { this: Future[A] =>
    protected[util] def depth: Short
    protected def parent: Promise[A]
    protected[util] def continue(k: K[A])

    /**
     * Note: exceptions in responds are monitored.  That is, if the
     * computation `k` throws a raw (ie.  not encoded in a Future)
     * exception, it is handled by the current monitor, see
     * [[Monitor]] for details.
     */
    def respond(k: Try[A] => Unit): Future[A] = {
      continue(new Monitored(Local.save(), k, depth))
      new Chained(parent, (depth+1).toShort)
    }

    def transform[B](f: Try[A] => Future[B]): Future[B] = {
      val promise = interrupts[B](this)

      continue(new Transformer(Local.save(), promise, f, depth))

      promise
    }
  }

  /** A future that is chained from a parent promise with a certain depth. */
  private class Chained[A](val parent: Promise[A], val depth: Short)
    extends Future[A]
    with Responder[A]
  {
    if (depth == Short.MaxValue)
      throw new AssertionError("Future chains cannot be longer than 32766!")

    // Awaitable
    @throws(classOf[TimeoutException])
    @throws(classOf[InterruptedException])
    def ready(timeout: Duration)(implicit permit: Awaitable.CanAwait): this.type = {
      parent.ready(timeout)
      this
    }

    @throws(classOf[Exception])
    def result(timeout: Duration)(implicit permit: Awaitable.CanAwait): A =
      parent.result(timeout)

    def isReady(implicit permit: Awaitable.CanAwait): Boolean =
      parent.isReady

    def poll: Option[Try[A]] = parent.poll

    override def isDefined: Boolean = parent.isDefined

    def raise(interrupt: Throwable): Unit = parent.raise(interrupt)

    protected[util] def continue(k: K[A]): Unit = parent.continue(k)

    override def toString: String = s"Future@$hashCode(depth=$depth,parent=$parent)"
  }

  // PUBLIC API

  /**
   * Indicates that an attempt to satisfy a [[com.twitter.util.Promise]] was made
   * after that promise had already been satisfied.
   */
  case class ImmutableResult(message: String) extends Exception(message)

  /** Create a new, empty, promise of type {{A}}. */
  def apply[A](): Promise[A] = new Promise[A]

  /**
   * Create a promise that interrupts all of ''fs''. In particular:
   * the returned promise handles an interrupt when any of ''fs'' do.
   */
  def interrupts[A](fs: Future[_]*): Promise[A] = {
    val handler: PartialFunction[Throwable, Unit] = {
      case intr =>
        val iterator = fs.iterator
        while (iterator.hasNext)
          iterator.next().raise(intr)
    }
    new Promise[A](handler)
  }

  /**
   * Single-arg version to avoid object creation and take advantage of `forwardInterruptsTo`.
   */
  def interrupts[A](f: Future[_]): Promise[A] = {
    val p = new Promise[A]
    p.forwardInterruptsTo(f)
    p
  }

  /**
   * Create a derivative promise that will be satisfied with the result of the
   * parent.
   *
   * If the derivative promise is detached before the parent is satisfied, then
   * it becomes disconnected from the parent and can be used as a normal,
   * unlinked Promise.
   *
   * By the contract of `Detachable`, satisfaction of the Promise must occur
   * ''after'' detachment. Promises should only ever be satisfied after they are
   * successfully detached (thus satisfaction is the responsibility of the
   * detacher).
   *
   * Ex:
   *
   * {{{
   * val f: Future[Unit]
   * val p: Promise[Unit] with Detachable = Promise.attached(f)
   * ...
   * if (p.detach()) p.setValue(())
   * }}}
   */
  def attached[A](parent: Future[A]): Promise[A] with Detachable = parent match {
    case p: Promise[_] =>
      new DetachablePromise[A](p.asInstanceOf[Promise[A]])
    case _ =>
      val p = new DetachableFuture[A]()
      parent.respond { t =>
        if (p.detach()) p.update(t)
      }
      p
  }
}

/**
 * A writeable [[com.twitter.util.Future]] that supports merging.
 * Callbacks (responders) of Promises are scheduled with
 * [[com.twitter.concurrent.Scheduler]].
 *
 * =Implementation details=
 *
 * A Promise is in one of five states: `Waiting`, `Interruptible`,
 * `Interrupted`, `Done` and `Linked` where `Interruptible` and
 * `Interrupted` are variants of `Waiting` to deal with future
 * interrupts. Promises are concurrency-safe, using lock-free operations
 * throughout. Callback dispatch is scheduled with
 * [[com.twitter.concurrent.Scheduler]].
 *
 * Waiters are stored as a [[com.twitter.util.Promise.K]]. `K`s
 * (mnemonic: continuation) specifies a `depth`. This is used to
 * implement Promise chaining: a callback with depth `d` is invoked only
 * after all callbacks with depth < `d` have already been invoked.
 *
 * `Promise.become` merges two promises: they are declared equivalent.
 * `become` merges the states of the two promises, and links one to the
 * other. Thus promises support the analog to tail-call elimination: no
 * space leak is incurred from `flatMap` in the tail position since
 * intermediate promises are merged into the root promise.
 *
 * A number of optimizations are employed to conserve ''space'': we pay
 * particular heed to the JVM's object representation, in particular for
 * OpenJDK (HotSpot) version 7 running on 64-bit architectures with
 * compressed OOPS. See comments on `com.twitter.util.Promise.State`
 * for details.
 */
class Promise[A]
  extends Future[A]
  with Promise.Responder[A]
  with Updatable[Try[A]] {
  import Promise._

  protected[util] final def depth: Short = 0
  protected final def parent: Promise[A] = this

  // Note: this will always be one of:
  // Waiting, Interrupted, Interruptible, `Try[A]` (Done), `Promise[A]` (Linked)
  @volatile private[this] var state: Any = initState
  private def theState(): Any = state

  def this(handleInterrupt: PartialFunction[Throwable, Unit]) {
    this()
    this.state = Interruptible(Nil, handleInterrupt)
  }

  def this(result: Try[A]) {
    this()
    this.state = result
  }

  override def toString: String = {
    val theState = state match {
      case p: Promise[A] /* Linked */ => s"Linked(${p.toString})"
      case res: Try[A] /* Done */ => s"Done($res)"
      case s => s.toString
    }
    s"Promise@$hashCode(state=$theState)"
  }

  @inline private[this] def cas(oldState: Any, newState: Any): Boolean =
    unsafe.compareAndSwapObject(this, stateOff, oldState, newState)

  private[this] def runq(first: K[A], rest: List[K[A]], result: Try[A]) = Scheduler.submit(
    new Runnable {
      def run(): Unit = {
        // It's always safe to run `first` ahead of everything else
        // since the only way to get a chainer is to register a
        // callback (which would always have depth 0).
        if (first ne null)
          first(result)
        var k: K[A] = null
        var moreDepth = false

        // Depth 0, about 77% only at this depth
        var ks = rest
        while (ks ne Nil) {
          k = ks.head
          if (k.depth == 0)
            k(result)
          else
            moreDepth = true
          ks = ks.tail
        }

        // depth >= 1, about 23%
        if (!moreDepth)
          return

        var maxDepth = 1
        ks = rest
        while (ks ne Nil) {
          k = ks.head
          if (k.depth == 1)
            k(result)
          else if (k.depth > maxDepth)
            maxDepth = k.depth
          ks = ks.tail
        }
        // Depth > 1, about 7%
        if (maxDepth > 1)
          runDepth2Plus(rest, result, maxDepth)
      }

      private[this] def runDepth2Plus(
        rest: List[K[A]],
        result: Try[A],
        maxDepth: Int
      ): Unit = {
        // empirically via JMH `FutureBenchmark.runqSize` the performance
        // is better once the the list gets larger. that cutoff point
        // was 14 in tests. however, it should be noted that this number
        // was picked for how it performs for that single distribution.
        // should it turn out that many users have a large `rest` with
        // shallow distributions, this number should likely be higher.
        // that said, this number is empirically large and should be a
        // rare run code path.
        val size = rest.size
        if (size > 13) {
          var rem = new mutable.ArrayBuffer[K[A]](size)
          var ks = rest
          while (ks ne Nil) {
            val k = ks.head
            if (k.depth > 1)
              rem += k
            ks = ks.tail
          }

          val sorted = rem.sortBy(K.depthOfK)
          var i = 0
          while (i < sorted.size) {
            sorted(i).apply(result)
            i += 1
          }
        } else {
          var depth = 2
          while (depth <= maxDepth) {
            var ks = rest
            while (ks ne Nil) {
              val k = ks.head
              if (k.depth == depth)
                k(result)
              ks = ks.tail
            }
            depth += 1
          }
        }
      }
    })

  /**
   * (Re)sets the interrupt handler. There is only
   * one active interrupt handler.
   *
   * @param f the new interrupt handler
   */
  @tailrec
  final def setInterruptHandler(f: PartialFunction[Throwable, Unit]): Unit = {
    state match {
      case p: Promise[A] /* Linked */ => p.setInterruptHandler(f)

      case s@Waiting(first, rest) =>
        val waitq = if (first eq null) rest else first :: rest
        if (!cas(s, Interruptible(waitq, f)))
          setInterruptHandler(f)

      case s@Interruptible(waitq, _) =>
        if (!cas(s, Interruptible(waitq, f)))
          setInterruptHandler(f)

      case s@Transforming(waitq, _) =>
        if (!cas(s, Interruptible(waitq, f)))
          setInterruptHandler(f)

      case Interrupted(_, signal) =>
        f.applyOrElse(signal, Promise.AlwaysUnit)

      case _: Try[A] /* Done */ => // ignore
    }
  }

  // Useful for debugging waitq.
  private[util] def waitqLength: Int = state match {
    case Waiting(first, rest) if first == null => rest.length
    case Waiting(first, rest) => rest.length + 1
    case Interruptible(waitq, _) => waitq.length
    case Transforming(waitq, _) => waitq.length
    case Interrupted(waitq, _) => waitq.length
    case _: Promise[A] /* Linked */ => 0
    case _: Try[A] /* Done */ => 0
  }

  /**
   * Forward interrupts to another future.
   * If the other future is fulfilled, this is a no-op.
   * Calling this multiple times is not recommended as
   * the resulting state may not be as expected.
   *
   * @param other the Future to which interrupts are forwarded.
   */
  @tailrec final
  def forwardInterruptsTo(other: Future[_]): Unit = {
    // This reduces allocations in the common case.
    if (other.isDefined) return
    state match {
      case p: Promise[A] /* Linked */ => p.forwardInterruptsTo(other)

      case s@Waiting(first, rest) =>
        val waitq = if (first eq null) rest else first :: rest
        if (!cas(s, Transforming(waitq, other)))
          forwardInterruptsTo(other)

      case s@Interruptible(waitq, _) =>
        if (!cas(s, Transforming(waitq, other)))
          forwardInterruptsTo(other)

      case s@Transforming(waitq, _) =>
        if (!cas(s, Transforming(waitq, other)))
          forwardInterruptsTo(other)

      case Interrupted(_, signal) =>
        other.raise(signal)

      case _: Try[A] /* Done */ => () // ignore
    }
  }

  @tailrec final
  def raise(intr: Throwable): Unit = state match {
    case p: Promise[A] /* Linked */ => p.raise(intr)

    case s@Interruptible(waitq, handler) =>
      if (!cas(s, Interrupted(waitq, intr))) raise(intr) else {
        handler.applyOrElse(intr, Promise.AlwaysUnit)
      }

    case s@Transforming(waitq, other) =>
      if (!cas(s, Interrupted(waitq, intr))) raise(intr) else {
        other.raise(intr)
      }

    case s@Interrupted(waitq, _) =>
      if (!cas(s, Interrupted(waitq, intr)))
        raise(intr)

    case s@Waiting(first, rest)  =>
      val waitq = if (first eq null) rest else first :: rest
      if (!cas(s, Interrupted(waitq, intr)))
        raise(intr)

    case _: Try[A] /* Done */ => () // nothing to do, as its already satisified.
  }

  @tailrec protected[Promise] final def detach(k: K[A]): Boolean = {

    def remove(list: List[K[Nothing]], k: K[_]): List[K[Nothing]] = {
      var res: List[K[Nothing]] = Nil
      var ls = list
      while (ls != Nil) {
        val h = ls.head
        ls = ls.tail
        if (h ne k) res = h :: res
      }

      res
    }

    state match {
      case p: Promise[A] /* Linked */ =>
        p.detach(k)

      case s@Interruptible(waitq, handler) =>
        if (!cas(s, Interruptible(remove(waitq, k), handler)))
          detach(k)
        else
          waitq.contains(k)

      case s@Transforming(waitq, other) =>
        if (!cas(s, Transforming(remove(waitq, k), other)))
          detach(k)
        else
          waitq.contains(k)

      case s@Interrupted(waitq, intr) =>
        if (!cas(s, Interrupted(remove(waitq, k), intr)))
          detach(k)
        else
          waitq.contains(k)

      case s@Waiting(first, rest)  =>
        val waitq = if (first eq null) rest else first :: rest
        val next = remove(waitq, k) match {
          case Nil => initState
          case head :: tail => Waiting(head, tail)
        }
        if (!cas(s, next)) detach(k)
        else waitq.contains(k)

      case _: Try[A] /* Done */ => false
    }
  }

  // Awaitable
  @throws(classOf[TimeoutException])
  @throws(classOf[InterruptedException])
  def ready(timeout: Duration)(implicit permit: Awaitable.CanAwait): this.type =
    state match {
      case p: Promise[A] /* Linked */ =>
        p.ready(timeout)
        this
      case res: Try[A] /* Done */ =>
        this
      case Waiting(_, _) | Interruptible(_, _) | Interrupted(_, _) | Transforming(_, _) =>
        val condition = new java.util.concurrent.CountDownLatch(1)
        respond { _ => condition.countDown() }

        // we need to `flush` pending tasks to give ourselves a chance
        // to complete. As a succinct example, this hangs without the `flush`:
        //
        //   Future.Done.map { _ =>
        //     Await.result(Future.Done.map(Predef.identity))
        //   }
        //
        Scheduler.flush()

        if (condition.await(timeout.inNanoseconds, TimeUnit.NANOSECONDS)) this
        else throw new TimeoutException(timeout.toString)
    }

  @throws(classOf[Exception])
  def result(timeout: Duration)(implicit permit: Awaitable.CanAwait): A = {
    val theTry = ready(timeout).compress().theState().asInstanceOf[Try[A]]
    theTry()
  }

  def isReady(implicit permit: Awaitable.CanAwait): Boolean =
    isDefined

  /**
   * Returns this promise's interrupt if it is interrupted.
   */
  def isInterrupted: Option[Throwable] = state match {
    case p: Promise[A] /* Linked */ => p.isInterrupted
    case Interrupted(_, intr) => Some(intr)
    case Waiting(_, _) | Interruptible(_, _) | Transforming(_, _) => None
    case _: Try[A] /* Done */ => None
  }

  /**
   * Become the other promise. `become` declares an equivalence
   * relation: `this` and `other` are the ''same''.
   *
   * By becoming `other`, its waitlists are now merged into `this`'s,
   * and `this` becomes canonical. The same is true of interrupt
   * handlers: `other`'s interrupt handler is overwritten with the
   * handlers installed for `this`.
   *
   * Note: Using `become` and `setInterruptHandler` on the same
   * promise is not recommended. Consider the following, which
   * demonstrates unexpected behavior related to this usage.
   *
   * {{{
   * val a, b = new Promise[Unit]
   * a.setInterruptHandler { case _ => println("A") }
   * b.become(a)
   * b.setInterruptHandler { case _ => println("B") }
   * a.raise(new Exception)
   * }}}
   *
   * This prints "B", the action in the interrupt handler for `b`,
   * which is unexpected because we raised on `a`. In this case and
   * others, using [[com.twitter.util.Future.proxyTo]] may be more
   * appropriate.
   *
   * Note that `this` must be unsatisfied at the time of the call,
   * and not race with any other setters. `become` is a form of
   * satisfying the promise.
   *
   * This has the combined effect of compressing the `other` into
   * `this`, effectively providing a form of tail-call elimination
   * when used in recursion constructs. `transform` (and thus any
   * other combinator) use this to compress Futures, freeing them
   * from space leaks when used with recursive constructions.
   *
   * '''Note:''' do not use become with cyclic graphs of futures: the
   * behavior of racing `a.become(b)` with `b.become(a)` is undefined
   * (where `a` and `b` may resolve as such transitively).
   *
   * @see [[com.twitter.util.Future.proxyTo]]
   */
  def become(other: Future[A]): Unit = {
    if (isDefined) {
      val current = Await.result(liftToTry)
      throw new IllegalStateException(s"cannot become() on an already satisfied promise: $current")
    }
    if (other.isInstanceOf[Promise[_]]) {
      val that = other.asInstanceOf[Promise[A]]
      that.link(compress())
    } else {
      other.proxyTo(this)
      forwardInterruptsTo(other)
    }
  }

  /**
   * Populate the Promise with the given result.
   *
   * @throws ImmutableResult if the Promise is already populated
   */
  def setValue(result: A): Unit = update(Return(result))

  /**
   * Populate the Promise with the given exception.
   *
   * @throws ImmutableResult if the Promise is already populated
   */
  def setException(throwable: Throwable): Unit = update(Throw(throwable))

  /**
   * Sets a Unit-typed future. By convention, futures of type
   * Future[Unit] are used for signalling.
   */
  def setDone()(implicit ev: this.type <:< Promise[Unit]): Boolean =
    ev(this).updateIfEmpty(Return.Unit)

  /**
   * Populate the Promise with the given Try. The try can either be a
   * value or an exception. setValue and setException are generally
   * more readable methods to use.
   *
   * @throws ImmutableResult if the Promise is already populated
   */
  def update(result: Try[A]): Unit = {
    updateIfEmpty(result) || {
      val current = Await.result(liftToTry)
      throw ImmutableResult(s"Result set multiple times. Value='$current', New='$result'")
    }
  }

  /**
   * Populate the Promise with the given Try. The Try can either be a
   * value or an exception. `setValue` and `setException` are generally
   * more readable methods to use.
   *
   * @note Invoking `updateIfEmpty` without checking the boolean result is almost
   * never the right approach. Doing so is generally unsafe unless race
   * conditions are acceptable.
   * @return true only if the result is updated, false if it was already set.
   */
  @tailrec
  final def updateIfEmpty(result: Try[A]): Boolean = state match {
    case _: Try[A] /* Done */ => false
    case s@Waiting(first, rest) =>
      if (!cas(s, result)) updateIfEmpty(result) else {
        runq(first.asInstanceOf[K[A]], rest.asInstanceOf[List[K[A]]], result)
        true
      }
    case s@Interruptible(waitq, _) =>
      if (!cas(s, result)) updateIfEmpty(result) else {
        runq(null, waitq.asInstanceOf[List[K[A]]], result)
        true
      }
    case s@Transforming(waitq, _) =>
      if (!cas(s, result)) updateIfEmpty(result) else {
        runq(null, waitq.asInstanceOf[List[K[A]]], result)
        true
      }
    case s@Interrupted(waitq, _) =>
      if (!cas(s, result)) updateIfEmpty(result) else {
        runq(null, waitq.asInstanceOf[List[K[A]]], result)
        true
      }
    case p: Promise[A] /* Linked */ => p.updateIfEmpty(result)
  }

  @tailrec
  protected[util] final def continue(k: K[A]): Unit = {
    state match {
      case v: Try[A] /* Done */ =>
        Scheduler.submit(new Runnable {
          def run(): Unit = {
            k(v)
          }
        })
      case s@Waiting(first, rest) if first == null =>
        if (!cas(s, Waiting(k, rest)))
          continue(k)
      case s@Waiting(first, rest) =>
        if (!cas(s, Waiting(first, k :: rest)))
          continue(k)
      case s@Interruptible(waitq, handler) =>
        if (!cas(s, Interruptible(k :: waitq, handler)))
          continue(k)
      case s@Transforming(waitq, other) =>
        if (!cas(s, Transforming(k :: waitq, other)))
          continue(k)
      case s@Interrupted(waitq, signal) =>
        if (!cas(s, Interrupted(k :: waitq, signal)))
          continue(k)
      case p: Promise[A] /* Linked */ =>
        p.continue(k)
    }
  }

  /**
   * Should only be called when this Promise has already been fulfilled
   * or it is becoming another Future via `become`.
   */
  protected final def compress(): Promise[A] = state match {
    case p: Promise[A] /* Linked */ =>
      val target = p.compress()
      // due to the assumptions stated above regarding when this can be called,
      // there should never be a `cas` fail.
      cas(p, target)
      target
    case _ =>
      this
  }

  @tailrec
  protected final def link(target: Promise[A]): Unit = {
    if (this eq target) return

    state match {
      case p: Promise[A] /* Linked */ =>
        if (cas(p, target))
          p.link(target)
        else
          link(target)

      case value: Try[A] /* Done */ =>
        if (!target.updateIfEmpty(value) && value != Await.result(target)) {
          throw new IllegalArgumentException(
            "Cannot link two Done Promises with differing values")
        }

      case s@Waiting(first, rest) =>
        if (!cas(s, target)) link(target) else {
          if (first != null)
            target.continue(first.asInstanceOf[K[A]])
          var ks = rest
          while (ks ne Nil) {
            target.continue(ks.head.asInstanceOf[K[A]])
            ks = ks.tail
          }
        }

      case s@Interruptible(waitq, handler) =>
        if (!cas(s, target)) link(target) else {
          var ks = waitq
          while (ks ne Nil) {
            target.continue(ks.head.asInstanceOf[K[A]])
            ks = ks.tail
          }
          target.setInterruptHandler(handler)
        }

      case s@Transforming(waitq, other) =>
        if (!cas(s, target)) link(target) else {
          var ks = waitq
          while (ks ne Nil) {
            target.continue(ks.head.asInstanceOf[K[A]])
            ks = ks.tail
          }
          target.forwardInterruptsTo(other)
        }

      case s@Interrupted(waitq, signal) =>
        if (!cas(s, target)) link(target) else {
          var ks = waitq
          while (ks ne Nil) {
            target.continue(ks.head.asInstanceOf[K[A]])
            ks = ks.tail
          }
          target.raise(signal)
        }
    }
  }

  def poll: Option[Try[A]] = state match {
    case p: Promise[A] /* Linked */ => p.poll
    case res: Try[A] /* Done */ => Some(res)
    case Waiting(_, _) | Interruptible(_, _) | Interrupted(_, _) | Transforming(_, _) => None
  }

  override def isDefined: Boolean = state match {
    // Note: the basic implementation is the same as `poll()`, but we want to avoid doing
    // object allocations for `Some`s when the caller does not need the result.
    case p: Promise[A] /* Linked */ => p.isDefined
    case res: Try[A] /* Done */ => true
    case Waiting(_, _) | Interruptible(_, _) | Interrupted(_, _) | Transforming(_, _) => false
  }
}
