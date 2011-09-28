package com.twitter.concurrent

import scala.collection.mutable.{ArrayBuffer, Queue}
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

import com.twitter.util.Duration

/**
 * I-structured variables provide write-once semantics with
 * synchronization on read.  IVars may be thought of as a skeletal
 * Future: it provides read synchronization, but no error handling,
 * composition etc.
 */

object IVar {
  // State laws:
  //   1.  You cannot transition out of Done.
  //   2.  You may transition from Linked, but
  //   only to another Linked.
  //   3.  You can transition from Waiting to
  //   either Linked or Done.
  private[IVar] sealed trait State[A]
  private[IVar] case class Waiting[A](
    waitq: List[A => Unit],
    chainq: List[IVar[A]]) extends State[A]
  private[IVar] case class Done[A](value: A) extends State[A]
  private[IVar] case class Linked[A](iv: IVar[A]) extends State[A]

  // Schedules are local to a thread, and
  // iteratively unwind IVar `gets` so that we can
  // implement recursive structures without
  // exhausting the stack.
  private[IVar] class Schedule {
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

  private[IVar] val _sched = new ThreadLocal[Schedule] {
    override def initialValue = new Schedule
  }
  private[IVar] def sched = _sched.get()
}

class IVar[A] {
  import IVar._

  @volatile private[this] var state: State[A] = Waiting(Nil, Nil)

  @inline
  private[this] def runqs(
    value: A,
    _waitq: List[A => Unit],
    _chainq: List[IVar[A]]
  ) = sched { () =>
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

  /**
   * Link `this` to `other` and return old state.
   * If we are already linked, we'll fix up our
   * state and issue `linkWith` recursively on the
   * linked IVar.  Note that we require `this` !=
   * `other` (transitively).  Otherwise, you'll
   * run into infinite loops.
   */
  private[IVar] def linkWith(other: IVar[A]): State[A] =
    synchronized {
      state match {
        case Linked(iv) =>
          state = Linked(other)
          iv.linkWith(other)
        case s =>
          state = Linked(other)
          s
      }
    }

  /**
   * Merge `other` into this IVar.  This does two things:
   *   1.  `this` assumes waiters and chainers of `other`.
   *   2.  the state of `other` is changed to Linked(this).
   *
   * Only (transitively) waiting IVars may be
   * merged, unless twoway=true.  Note that when
   * twoway=true and both `this' and `other' is is
   * Done, their values must be equal.
   */
  def merge(other: IVar[A], twoway: Boolean = false): Unit = {
    if (other eq this) return
    val (value, waitq, chainq) = synchronized {
      state match {
        case Linked(iv) =>
          iv.merge(other, twoway)
          return
        case Waiting(wq0, cq0) =>
          other.linkWith(this) match {
            case Waiting(Nil, Nil) => return
            case Waiting(wq1, cq1) =>
              // todo: if this is common, we should use a
              // sequence with constant concatentation.  in
              // particular, in scala 2.9, we'll have
              // UnrolledBuffer. (TODO29)
              state = Waiting(wq0 ++ wq1, cq0 ++ cq1)
              return
            case s@Done(value) =>
              state = s
              (value, wq0, cq0)
            case Linked(_) =>
              return assert(false)
          }
        case Done(value) =>
          assert(twoway)

          // In this case, we're free to merge
          // even completed IVars.  However, if
          // `other' is also Done, we require
          // their values to be equal.
          //
          // This may seem like strange and
          // abitrary semantics, but it is
          // particularly useful for IVars used
          // for signalling purposes.

          other.linkWith(this) match {
            case Waiting(Nil, Nil) => return
            case Waiting(wq, cq) => (value, wq, cq)
            case Done(value1) if value == value1 => return
            case Done(_) => return assert(false)
            case Linked(_) => return assert(false)
          }
      }
    }

    runqs(value, waitq, chainq)
  }

  /**
   * A blocking get.  It won't cause a deadlock if
   * the IVar is already defined because it does
   * not attempt to defer getting a defined value.
   */
  def apply(): A = apply(Duration.MaxValue).get
  def apply(timeout: Duration): Option[A] =
    state match {
      case Linked(iv) =>
        iv.apply(timeout)
      case Done(value) =>
        Some(value)
      case s@Waiting(_, _) =>
        val q = new ArrayBlockingQueue[A](1)
        get(q.offer(_))
        sched.flush()
        val (v, u) = timeout.inTimeUnit
        // This actually does work also with
        // primitive types due to some underlying
        // boxing magic?
        Option(q.poll(v, u))
    }

  /**
   * @return true if the value has been set.
   */
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
  def set(value: A): Boolean = {
    var (waitq, chainq) = synchronized {
      state match {
        case Done(_) =>
          return false
        case Waiting(waitq, chainq) =>
          state = Done(value)
          (waitq, chainq)
        case Linked(iv) =>
          return iv.set(value)
      }
    }

    runqs(value, waitq, chainq)
    true
  }

  /**
   * Pass a block, 'k' which will be invoked when the value is
   * available.  Blocks will be invoked in order of addition.
   */
  def get(k: A => Unit) {
    val value = synchronized {
      state match {
        case w@Waiting(waitq, _) =>
          state = w.copy(waitq = k :: waitq)
          return
        case Done(value) =>
          value
        case Linked(iv) =>
          return iv.get(k)
      }
    }

    sched { () => k(value) }
  }

  /**
   * Provide an IVar that is chained to this one.  This provides a
   * happens-before relationship vis-a-vis *this* IVar.  That is: an
   * ordering that is visible to the consumer of this IVar (wrt.
   * gets) is preserved via chaining.
   */
  def chained: IVar[A] = synchronized {
    state match {
      case w@Waiting(_, chainq) =>
        val iv = new IVar[A]
        state = w.copy(chainq = iv :: chainq)
        iv
      case Done(_) =>
        this
      case Linked(iv) =>
        iv.chained
    }
  }
}
