package com.twitter.concurrent

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

import scala.annotation.tailrec

import com.twitter.util.Duration

/**
 * An IVar is an "I-structured variable". It is a mutable cell that
 * begins empty but can eventually contain a value. It supports
 * write-once semantics (subsequent writes are ignored); in order to
 * read the value of the cell, you pass in a callback that will be
 * invoked when the cell is eventually populated. IVars are a
 * building block for Futures; Futures add additional composition and
 * error-handling functionality.
 *
 * IVars have three features:
 *
 * 1. A concurrency-safe mutable container for a value. When the
 * value is not yet populated, it is in a "Waiting" state, and it can
 * keep track of callback functions, called "waiters". When the value
 * is populated, the IVar transitions to a Done(value) state, and all
 * waiters are invoked. Note that the waiters are invoked indirectly
 * by enqueueing work on a Scheduler. A Scheduler is a thread-local
 * object that, when invoked, dequeues and processes items until the
 * queue is empty. This ensures that recursive operations do not
 * cause stack-overflows. For example, if when executing waiters of
 * an IVar a, waiters would be added to another IVar b in the Done
 * state, rather than evaluating b's waiters immediately, which would
 * deepen the stack, that work is enqueued for later. When all
 * waiters of IVar a have been evaluated, remaining work on the queue
 * (including the waiters for IVar b) are executed at the same stack
 * level as IVar a's waiters. Thus, execution order differs from a
 * normal procedural language: it is breadth-first.
 *
 * 2. A facility for specifying the order in which callback functions
 * are invoked. Generally, waiters are invoked in an arbitrary order.
 * However, by "chaining" you can express that a set of waiters must
 * be invoked AFTER another set of waiters. By using chaining you can
 * create a schedule of waiters in a tree topology. Chaining is
 * implemented by creating a child IVar of the parent. Waiters on the
 * parent are executed before waiters on the child. Execution order
 * is breadth-first.
 *
 * 3. An ability to merge two equivalent IVars. Two equivalent
 * Futures are constructed in the implementation of `Promise#flatMap`
 * (and `Promise#rescue`). By having equivalent Futures use merged
 * IVars, we avoid having equivalent Futures linked to one another,
 * thus avoiding space- leaks. Consider the operation `a flatMap b
 * flatMap c flatMap d`. This would, without merging, create a
 * reference chain like `a <- b <- c <- d`, with client code having
 * references to both `a` and `d`. With merging, we produce a star
 * (approximately), rather than a chain topology. So for the same
 * flatMap example, the underlying merged IVars are linked like: `a
 * <- b, a <- c, a <- d`. When two IVars are merged, one is
 * considered canonical (in this example, `a`). Upon merging, waiters
 * and chainers of apocryphal IVars are moved into the canonical
 * IVar. The benefit of the star topology is that since client code
 * only has pointers to `a` and `d`, all intermediate objects can be
 * freed by the garbage collector. Note that certain sequences of
 * operations can have the topology diverge from a star, but these
 * should be infrequent.
 */
package ivar {
  // State laws:
  //   1.  You cannot transition out of Done.
  //   2.  You may transition from Linked, but
  //   only to another Linked.
  //   3.  You can transition from Waiting to
  //   either Linked or Done.
  sealed trait State[+A]
  case class Waiting[A](
    waitq: List[A => Unit],
    chainq: List[IVar[A]]) extends State[A]
  case class Done[A](value: A) extends State[A]
  case class Linked[A](iv: IVar[A]) extends State[A]
}

import com.twitter.concurrent.ivar._

object IVar {
  private val initState: State[Nothing] = Waiting(Nil, Nil)
  private val stateUpd = AtomicReferenceFieldUpdater.newUpdater(
    classOf[IVarField[_]], classOf[State[_]], "state")
}

final class IVar[A] extends IVarField[A] {
  import IVar._

  override def toString =
    "Ivar@%s(state=%s)".format(hashCode, state)

  state = initState: State[A]
  @inline private[this]
  def cas(expect: State[A], `new`: State[A]) =
    stateUpd.compareAndSet(this, expect, `new`)

  def depth: Int = {
    @tailrec
    def loop(iv: IVar[_], d: Int): Int = iv.state match {
      case Linked(iv) => loop(iv, d + 1)
      case _ => d
    }
    loop(this, 0)
  }

  @inline
  private[this] def runqs(
    value: A,
    _waitq: List[A => Unit],
    _chainq: List[IVar[A]]
  ) = Scheduler.submit(new Runnable {
    def run() {
      // todo: exceptions stop execution
      // here, but should they?
      var waitq = _waitq
      while (waitq.nonEmpty) {
        waitq.head(value)
        waitq = waitq.tail
      }

      var chainq = _chainq
      while (chainq.nonEmpty) {
        chainq.head.set(value)
        chainq = chainq.tail
      }
    }
  })

  private[IVar] def resolve(): IVar[A] =
    state match {
      case s@Linked(next) =>
        val iv = next.resolve()
        cas(s, Linked(iv))
        iv
      case _ => this
    }

  /**
   * Link `this` to `other`. `Other` is now considered canonical in
   * this cluster. So if we are already linked, we'll fix up our
   * state and issue `linkTo` recursively on the linked IVar. This
   * will leave other IVars pointing to the recursive IVar
   * unmolested, so we will diverge from a pure star-topology (we
   * will have a chain). This causes a minor space-leak. But this
   * should be very rare, and such chains should be very short.
   *
   * Note that we require `this` != `other` (transitively).
   * Furthermore, IVars do not provide protection against linking
   * races.  That is, the behavior of the race between
   *
   *  a merge b
   *  b merge a
   *
   * is undefined.  Thus, linking/merging should be used only in
   * scenarios where such races can be guaranteed not to occur.
   */
  @tailrec private[IVar]
  def linkTo(other: IVar[A]) {
    if (this eq other) return

    state match {
      case s@Linked(iv) =>
        if (cas(s, Linked(other)))
          iv.linkTo(other)
        else
          linkTo(other)
      case s@Done(value) =>
        if (!other.set(value) && value != other())
          throw new IllegalArgumentException("Cannot link two Done IVars with differing values")
      case s@Waiting(waitq, chainq) =>
        if (cas(s, Linked(other)))
          other.addq(waitq, chainq)
        else
          linkTo(other)
    }
  }

  @tailrec private[IVar]
  def addq(
    wq: List[A => Unit], cq: List[IVar[A]]
  ): Unit = state match {
    case s@Waiting(wq0, cq0) =>
      if (!cas(s, Waiting(wq0 ++ wq, cq0 ++ cq)))
        addq(wq, cq)
    case Done(value) =>
      runqs(value, wq, cq)
    case Linked(iv) =>
      iv.addq(wq, cq)
  }

  /**
   * Merge `other` into this IVar.  This does two things:
   *   1.  `this` assumes waiters and chainers of `other`.
   *   2.  the state of `other` is changed to Linked(this).
   *
   * Note that two Done IVars can only be merged if their values are
   * equal. An InvalidArgumentException is thrown, otherwise.
   */
  def merge(other: IVar[A]) {
    other.linkTo(resolve())
  }

  /**
   * A blocking get.  It won't cause a deadlock if the IVar is
   * already defined because it does not attempt to defer getting a
   * defined value.
   */
  def apply(): A = apply(Duration.Top).get
  def apply(timeout: Duration): Option[A] =
    state match {
      case Linked(_) =>
        resolve().apply(timeout)
      case Done(value) =>
        Some(value)
      case _ =>
        val condition = new CountDownLatch(1)
        get { _ => condition.countDown() }
        val (v, u) = timeout.inTimeUnit
        Scheduler.flush()
        if (condition.await(v, u)) {
          Some(state.asInstanceOf[Done[A]].value)
        } else {
          None
        }
    }

  @tailrec
  def poll: Option[A] = state match {
    case Linked(iv) => iv.poll
    case Done(v) => Some(v)
    case _ => None
  }

  /**
   * @return true if the value has been set.
   */
  @tailrec
  def isDefined: Boolean = state match {
    case Waiting(_, _) => false
    case Done(_) => true
    case Linked(iv) => iv.isDefined
  }

  /**
   * Set the value - only the first call will be successful.
   *
   * @return true if the value was set successfully.
   */
  @tailrec
  def set(value: A): Boolean =
    state match {
      case Done(_) => false
      case s@Waiting(waitq, chainq) =>
        if (!cas(s, Done(value))) set(value) else {
          runqs(value, waitq, chainq)
          true
        }
      case Linked(iv) =>
        iv.set(value)
    }

  /**
   * Pass a block, 'k' which will be invoked when the value is
   * available.  Note that 'get' does not preserve ordering, that is:
   * {{a.get(k); a.get(k')}} does not imply {{k}} happens-before
   * {{k'}}.
   */
  @tailrec
  def get(k: A => Unit) {
    state match {
      case s@Waiting(waitq, _) =>
        if (!cas(s, s.copy(waitq = k :: waitq)))
          get(k)
      case Done(value) =>
        Scheduler.submit(new Runnable {
          def run() { k(value) }
        })
      case Linked(iv) =>
        iv.get(k)
    }
  }

  /**
   * Remove the given closure {{k}} from the waiter list by object
   * identity.
   */
  @tailrec
  def unget(k: A => Unit) {
    state match {
      case s@Waiting(k0 :: Nil, Nil) if k0 eq k =>
        if (!cas(s, initState: State[A]))
          unget(k)
      case s@Waiting(waitq, _) =>
        val waitq1 = waitq filter { _ ne k }
        if (!cas(s, s.copy(waitq = waitq1)))
          unget(k)
      case Done(_) => ()
      case Linked(iv) =>
        iv.unget(k)
    }
  }

  /**
   * Provide an IVar that is chained to this one.  This provides a
   * happens-before relationship vis-a-vis *this* IVar.  That is: an
   * ordering that is visible to the consumer of this IVar (wrt.
   * gets) is preserved via chaining.
   *
   * That is, to use in a manner that guarantees strict ordering of
   * invocation, use the following pattern:
   *
   * {{{
   *   val ivar: IVar[A]
   *   val next = ivar.chained
   *   ivar.get { _ => /* first action */ }
   *   next.get { _ => /* second action */ }
   * }}}
   */
  @tailrec
  def chained: IVar[A] =
    state match {
      case s@Waiting(_, chainq) =>
        val iv = new IVar[A]
        if (cas(s, s.copy(chainq = iv :: chainq))) iv else chained
      case Done(_) =>
        this
      case Linked(iv) =>
        iv.chained
    }
}
