package com.twitter.util

import com.twitter.concurrent.Scheduler
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.tailrec
import scala.collection.mutable
import scala.runtime.NonLocalReturnControl

object Promise {
  /**
   * A continuation stored from a promise.
   */
  private trait K[-A] extends (Try[A] => Unit) {
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
    def apply(result: Try[A]) {
      update(result)
    }
  }

  /**
   * A monitored continuation.
   *
   * @param saved The saved local context of the invocation site
   *
   * @param k the closure to invoke in the saved context, with the
   * provided result
   *
   * @param depth a tag used to store the chain depth of this context
   * for scheduling purposes.
   */
  private class Monitored[A](
      saved: Local.Context,
      k: Try[A] => Unit,
      val depth: Short)
    extends K[A]
  {
    def apply(result: Try[A]) {
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
   *
   * @param promise The Promise for the transformed value
   *
   * @param f The closure to invoke to produce the Future of the transformed value.
   *
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

    def apply(result: Try[A]) {
      val current = Local.save()
      Local.restore(saved)
      try k(result)
      finally Local.restore(current)
    }
  }

  /*
   * Performance notes
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
   */
  private sealed trait State[A]
  private case class Waiting[A](first: K[A], rest: List[K[A]]) extends State[A]
  private case class Interruptible[A](waitq: List[K[A]], handler: PartialFunction[Throwable, Unit]) extends State[A]
  private case class Transforming[A](waitq: List[K[A]], other: Future[_]) extends State[A]
  private case class Interrupted[A](waitq: List[K[A]], signal: Throwable) extends State[A]
  private case class Done[A](result: Try[A]) extends State[A]
  private case class Linked[A](p: Promise[A]) extends State[A]

  private def initState[A]: State[A] = emptyState.asInstanceOf[State[A]]
  private val emptyState: State[Nothing] = Waiting(null, Nil)
  private val unsafe = Unsafe()
  private val stateOff = unsafe.objectFieldOffset(classOf[Promise[_]].getDeclaredField("state"))

  sealed trait Responder[A] extends Future[A] {
    protected[util] def depth: Short
    protected def parent: Promise[A]
    protected[util] def continue(k: K[A])

    /**
     * Note: exceptions in responds are monitored.  That is, if the
     * computation {{k}} throws a raw (ie.  not encoded in a Future)
     * exception, it is handled by the current monitor, see
     * {{com.twitter.util.Monitor}} for details.
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
  private class Chained[A](val parent: Promise[A], val depth: Short) extends Future[A] with Responder[A] {
    assert(depth < Short.MaxValue, "Future chains cannot be longer than 32766!")

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

    def poll = parent.poll

    override def isDefined = parent.isDefined

    def raise(interrupt: Throwable) = parent.raise(interrupt)

    protected[util] def continue(k: K[A]) = parent.continue(k)

    override def toString = "Future@%s(depth=%s,parent=%s)".format(hashCode, depth, parent)
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
      case intr => for (f <- fs) f.raise(intr)
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
   * Create a derivative promise that will be satisfied with the result of the parent.
   * However, if the derivative promise is detached before the parent is satisfied,
   * it can just be used as a normal Promise.
   *
   * The contract for Detachable is to only do non-idempotent side-effects after
   * detaching.  Here, the pertinent side-effect is the satisfaction of the Promise.
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
      val p = new Promise[A] with Detachable {
        private[this] val detached = new AtomicBoolean(false)

        def detach(): Boolean = detached.compareAndSet(false, true)
      }
      parent respond { case t =>
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
 * compressed OOPS. See comments on [[com.twitter.util.Promise.State]]
 * for details.
 */
class Promise[A] extends Future[A] with Promise.Responder[A] {
  import Promise._

  protected[util] final def depth = 0
  protected final def parent = this

  @volatile private[this] var state: Promise.State[A] = initState
  private def theState(): Promise.State[A] = state

  def this(handleInterrupt: PartialFunction[Throwable, Unit]) {
    this()
    this.state = Interruptible(Nil, handleInterrupt)
  }

  def this(result: Try[A]) {
    this()
    this.state = Done(result)
  }

  override def toString = "Promise@%s(state=%s)".format(hashCode, state)

  @inline private[this] def cas(oldState: State[A], newState: State[A]): Boolean =
    unsafe.compareAndSwapObject(this, stateOff, oldState, newState)

  private[this] def runq(first: K[A], rest: List[K[A]], result: Try[A]) = Scheduler.submit(
    new Runnable {
      def run() {
        // It's always safe to run ``first'' ahead of everything else
        // since the only way to get a chainer is to register a
        // callback (which would always have depth 0).
        if (first ne null) first(result)
        var k: K[A] = null

        // Depth 0
        var ks = rest
        while (ks ne Nil) {
          k = ks.head
          if (k.depth == 0)
            k(result)
          ks = ks.tail
        }

        // Depth 1
        ks = rest
        while (ks ne Nil) {
          k = ks.head
          if (k.depth == 1)
            k(result)
          ks = ks.tail
        }

        // Depth > 1 (Rare: ~6%)
        var rem: mutable.Buffer[K[A]] = null
        ks = rest
        while (ks ne Nil) {
          k = ks.head
          if (k.depth > 1) {
            if (rem == null) rem = mutable.ArrayBuffer()
            rem += k
          }
          ks = ks.tail
        }

        if (rem eq null)
          return

        val sorted = rem.sortBy(K.depthOfK)
        var i = 0
        while (i < sorted.size) {
          sorted(i).apply(result)
          i += 1
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
  final def setInterruptHandler(f: PartialFunction[Throwable, Unit]) {
    state match {
      case Linked(p) => p.setInterruptHandler(f)

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
        if (f.isDefinedAt(signal))
          f(signal)

      case Done(_) => // ignore
    }
  }

  // Useful for debugging waitq.
  private[util] def waitqLength: Int = state match {
    case Waiting(first, rest) if first == null => rest.length
    case Waiting(first, rest) => rest.length + 1
    case Interruptible(waitq, _) => waitq.length
    case Transforming(waitq, _) => waitq.length
    case Interrupted(waitq, _) => waitq.length
    case Done(_) | Linked(_) => 0
  }

  /**
   * Forward interrupts to another future.
   *
   * @param other the Future to which interrupts are forwarded.
   */
  @tailrec final
  def forwardInterruptsTo(other: Future[_]) {
    state match {
      case Linked(p) => p.forwardInterruptsTo(other)

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

      case Done(_) => // ignore
    }
  }

  @tailrec final
  def raise(intr: Throwable) = state match {
    case Linked(p) => p.raise(intr)
    case s@Interruptible(waitq, handler) =>
      if (!cas(s, Interrupted(waitq, intr))) raise(intr) else {
        if (handler.isDefinedAt(intr))
          handler(intr)
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

    case Done(_) =>
  }

  @tailrec protected[Promise] final def detach(k: K[A]): Boolean = {
    state match {
      case Linked(p) =>
        p.detach(k)

      case s@Interruptible(waitq, handler) =>
        if (!cas(s, Interruptible(waitq filterNot (_ eq k), handler)))
          detach(k)
        else
          waitq.contains(k)

      case s@Transforming(waitq, other) =>
        if (!cas(s, Transforming(waitq filterNot (_ eq k), other)))
          detach(k)
        else
          waitq.contains(k)

      case s@Interrupted(waitq, intr) =>
        if (!cas(s, Interrupted(waitq filterNot (_ eq k), intr)))
          detach(k)
        else
          waitq.contains(k)

      case s@Waiting(first, rest)  =>
        val waitq = if (first eq null) rest else first :: rest
        val next = (waitq filterNot (_ eq k)) match {
          case Nil => initState[A]
          case head :: tail => Waiting(head, tail)
        }
        if (!cas(s, next)) detach(k)
        else waitq.contains(k)

      case Done(_) => false
    }
  }

  // Awaitable
  @throws(classOf[TimeoutException])
  @throws(classOf[InterruptedException])
  def ready(timeout: Duration)(implicit permit: Awaitable.CanAwait): this.type =
    state match {
      case Linked(p) =>
        p.ready(timeout)
        this
      case Done(res) =>
        this
      case Waiting(_, _) | Interruptible(_, _) | Interrupted(_, _) | Transforming(_, _) =>
        val condition = new java.util.concurrent.CountDownLatch(1)
        respond { _ => condition.countDown() }
        Scheduler.flush()
        if (condition.await(timeout.inNanoseconds, TimeUnit.NANOSECONDS)) this
        else throw new TimeoutException(timeout.toString)
    }

  @throws(classOf[Exception])
  def result(timeout: Duration)(implicit permit: Awaitable.CanAwait): A = {
    val Done(theTry) = ready(timeout).compress().theState()
    theTry()
  }

  def isReady(implicit permit: Awaitable.CanAwait): Boolean =
    isDefined

  /**
   * Returns this promise's interrupt if it is interrupted.
   */
  def isInterrupted: Option[Throwable] = state match {
    case Linked(p) => p.isInterrupted
    case Interrupted(_, intr) => Some(intr)
    case Done(_) | Waiting(_, _) | Interruptible(_, _) | Transforming(_, _) => None
  }

  /**
   * Become the other promise. `become` declares an equivalence
   * relation: `this` and `other` are the ''same''.
   *
   * By becoming `other`, its waitlists are now merged into `this`'s,
   * and `this` becomes canonical. The same is true of interrupt
   * handlers: `other`'s interrupt handler becomes active, but is
   * stored canonically by `this` - further references are forwarded.
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
   */
  def become(other: Future[A]) {
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
  def setValue(result: A) { update(Return(result)) }

  /**
   * Populate the Promise with the given exception.
   *
   * @throws ImmutableResult if the Promise is already populated
   */
  def setException(throwable: Throwable) { update(Throw(throwable)) }

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
  def update(result: Try[A]) {
    updateIfEmpty(result) || {
      val current = Await.result(liftToTry)
      throw new ImmutableResult(s"Result set multiple times. Value='$current', New='$result'")
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
   *
   * @return true only if the result is updated, false if it was already set.
   */
  @tailrec
  final def updateIfEmpty(result: Try[A]): Boolean = state match {
    case Done(_) => false
    case s@Waiting(first, rest) =>
      if (!cas(s, Done(result))) updateIfEmpty(result) else {
        runq(first, rest, result)
        true
      }
    case s@Interruptible(waitq, _) =>
      if (!cas(s, Done(result))) updateIfEmpty(result) else {
        runq(null, waitq, result)
        true
      }
    case s@Transforming(waitq, _) =>
      if (!cas(s, Done(result))) updateIfEmpty(result) else {
        runq(null, waitq, result)
        true
      }
    case s@Interrupted(waitq, _) =>
      if (!cas(s, Done(result))) updateIfEmpty(result) else {
        runq(null, waitq, result)
        true
      }
    case Linked(p) => p.updateIfEmpty(result)
  }

  @tailrec
  protected[util] final def continue(k: K[A]) {
    state match {
      case Done(v) =>
        Scheduler.submit(new Runnable {
          def run() {
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
      case Linked(p) =>
        p.continue(k)
    }
  }

  /**
   * Should only be called when this Promise has already been fulfilled
   * or it is becoming another Future via `become`.
   */
  protected final def compress(): Promise[A] = state match {
    case s@Linked(p) =>
      val target = p.compress()
      // due to the assumptions stated above regarding when this can be called,
      // there should never be a `cas` fail.
      cas(s, Linked(target))
      target
    case _ =>
      this
  }

  @tailrec
  protected final def link(target: Promise[A]) {
    if (this eq target) return

    state match {
      case s@Linked(p) =>
        if (cas(s, Linked(target)))
          p.link(target)
        else
          link(target)

      case s@Done(value) =>
        if (!target.updateIfEmpty(value) && value != Await.result(target)) {
          throw new IllegalArgumentException(
            "Cannot link two Done Promises with differing values")
        }

      case s@Waiting(first, rest) =>
        if (!cas(s, Linked(target))) link(target) else {
          if (first != null)
            target.continue(first)
          var ks = rest
          while (ks ne Nil) {
            target.continue(ks.head)
            ks = ks.tail
          }
        }

      case s@Interruptible(waitq, handler) =>
        if (!cas(s, Linked(target))) link(target) else {
          var ks = waitq
          while (ks ne Nil) {
            target.continue(ks.head)
            ks = ks.tail
          }
          target.setInterruptHandler(handler)
        }

      case s@Transforming(waitq, other) =>
        if (!cas(s, Linked(target))) link(target) else {
          var ks = waitq
          while (ks ne Nil) {
            target.continue(ks.head)
            ks = ks.tail
          }
          target.forwardInterruptsTo(other)
        }

      case s@Interrupted(waitq, signal) =>
        if (!cas(s, Linked(target))) link(target) else {
          var ks = waitq
          while (ks ne Nil) {
            target.continue(ks.head)
            ks = ks.tail
          }
          target.raise(signal)
        }
    }
  }

  def poll: Option[Try[A]] = state match {
    case Linked(p) => p.poll
    case Done(res) => Some(res)
    case Waiting(_, _) | Interruptible(_, _) | Interrupted(_, _) | Transforming(_, _) => None
  }

  override def isDefined: Boolean = state match {
    // Note: the basic implementation is the same as `poll()`, but we want to avoid doing
    // object allocations for `Some`s when the caller does not need the result.
    case Linked(p) => p.isDefined
    case Done(res) => true
    case Waiting(_, _) | Interruptible(_, _) | Interrupted(_, _) | Transforming(_, _) => false
  }
}
