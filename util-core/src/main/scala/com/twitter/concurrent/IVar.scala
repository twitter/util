package com.twitter.concurrent

import scala.annotation.tailrec

import scala.collection.mutable.{ArrayBuffer, Queue}
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.{
  AtomicReference, AtomicReferenceFieldUpdater}

import com.twitter.util.Duration

/**
 * I-structured variables provide write-once semantics with
 * synchronization on read.  IVars may be thought of as a skeletal
 * Future: it provides read synchronization, but no error handling,
 * composition etc.
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

import ivar._

object IVar {
  // Schedules are local to a thread, and
  // iteratively unwind IVar `gets` so that we can
  // implement recursive structures without
  // exhausting the stack.
  //
  // todo: it's possible, too, to not unwind
  // _every_ get, but rather only after a certain
  // stack depth has been reached.
  private class Schedule {
    private[this] type Waiter = () => Unit
    private[this] var w0, w1, w2: Waiter = null
    private[this] var ws = new Queue[Waiter]
    private[this] var running = false

    def apply(waiter: () => Unit) {
      if (w0 == null) w0 = waiter
      else if (w1 == null) w1 = waiter
      else if (w2 == null) w2 = waiter
      else ws.enqueue(waiter)
      if (!running) run()
    }

    def flush() {
      if (running) run()
    }

    private[this] def run() {
      val save = running
      running = true
      // via moderately silly benchmarking, the
      // queue unrolling gives us a ~50% speedup
      // over pure Queue usage for common
      // situations.
      try {
        while (w0 != null) {
          val w = w0
          w0 = w1
          w1 = w2
          w2 = if (ws.isEmpty) null else ws.dequeue()
          w()
        }
      } finally {
        running = save
      }
    }
  }

  private val _sched = new ThreadLocal[Schedule] {
    override def initialValue = new Schedule
  }
  private def sched = _sched.get()
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
  ) = sched { () =>
    // todo: exceptions stop execution
    // here, but should they?
    var waitq = _waitq
    while (waitq != Nil) {
      waitq.head(value)
      waitq = waitq.tail
    }

    var chainq = _chainq
    while (chainq != Nil) {
      chainq.head.set(value)
      chainq = chainq.tail
    }
  }

  private[IVar] def resolve(): IVar[A] =
    state match {
      case s@Linked(next) =>
        val iv = next.resolve()
        cas(s, Linked(iv))
        iv
      case _ => this
    }

  /**
   * Link `this` to `other` and return old state.
   * If we are already linked, we'll fix up our
   * state and issue `linkWith` recursively on the
   * linked IVar.  Note that we require `this` !=
   * `other` (transitively).  Furthermore, IVars
   * do not provide protection against linking
   * races.  That is, the behavior of the race
   * between
   *
   *  a merge b
   *  b merge a
   *
   * is undefined.  Thus, linking/merging should
   * be used only in scenarios where such races
   * can be guaranteed not to occur.
   */
  @tailrec private[IVar]
  def linkWith(
    other: IVar[A]
  ): State[A] = state match {
    case s@Linked(iv) =>
      if (iv eq other) s else {
        cas(s, Linked(other))
        iv.linkWith(other)
      }
    case s@Done(_) => s
    case s =>
      if (cas(s, Linked(other))) s else linkWith(other)
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
   * Only (transitively) waiting IVars may be
   * merged into, unless twoway=true.  Note that
   * when twoway=true and both `this' and `other'
   * is Done, their values must be equal.  See
   * `linkWith` for additional caveats.
   */
  @tailrec
  def merge(
    other: IVar[A], twoway: Boolean = false
  ): Unit = state match {
    case Waiting(_, _) =>
      other.linkWith(this) match {
        case Waiting(wq1, cq1) => addq(wq1, cq1)
        case Done(value) => set(value)
        case Linked(_) => ()
      }

    case s@Linked(ivl) =>
      resolve().merge(other, twoway)

    case Done(value) =>
      require(twoway)
      other.linkWith(this) match {
        case Waiting(wq, cq) => runqs(value, wq, cq)
        case _ => ()
      }
    }

  /**
   * A blocking get.  It won't cause a deadlock if
   * the IVar is already defined because it does
   * not attempt to defer getting a defined value.
   */
  def apply(): A = apply(Duration.MaxValue).get
  def apply(timeout: Duration): Option[A] =
    state match {
      case Linked(_) =>
        resolve().apply(timeout)
      case Done(value) =>
        Some(value)
      case _ =>
        val q = new ArrayBlockingQueue[A](1)
        get(q.offer(_))
        sched.flush()
        val (v, u) = timeout.inTimeUnit
        // This actually does work also with
        // primitive types due to some underlying
        // boxing magic?
        Option(q.poll(v, u))
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
        sched { () => k(value) }
      case Linked(iv) =>
        iv.get(k)
    }
  }

  /**
   * Remove the given closure {{k}} from
   * the waiter list.
   */
  @tailrec
  def unget(k: A => Unit) {
    state match {
      case s@Waiting(k :: Nil, Nil) =>
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
