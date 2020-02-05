package com.twitter.util

import java.util.{List => JList}

import scala.collection.mutable.Buffer
import scala.jdk.CollectionConverters._
import scala.language.higherKinds
import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.Iterable
import scala.collection.compat._

/**
 * Activity, like [[com.twitter.util.Var Var]], is a value that varies over
 * time; but, richer than Var, these values have error and pending states.
 * They are useful for modeling a process that produces values, but also
 * intermittent failures (e.g. address resolution, reloadable configuration).
 *
 * As such, an Activity can be in one of three states:
 *
 *  - [[com.twitter.util.Activity.Pending Pending]]: output is pending;
 *  - [[com.twitter.util.Activity.Ok Ok]]: an output is available; and
 *  - [[com.twitter.util.Activity.Failed Failed]]: the process failed with an exception.
 *
 * An Activity may transition between any state at any time. They are typically
 * constructed from [[com.twitter.util.Event Events]], but they can also be
 * constructed without arguments.
 *
 * {{{
 * val (activity, witness) = Activity[Int]()
 * println(activity.sample()) // throws java.lang.IllegalStateException: Still pending
 * }}}
 *
 * As you can see, Activities start in a Pending state. Attempts to sample an
 * Activity that is pending will fail. Curiously, the constructor for an
 * Activity also returns a [[com.twitter.util.Witness Witness]].
 *
 * Recall that a Witness is the interface to something that you can send values
 * to; and in this case, that something is the Activity.
 *
 * {{{
 * witness.notify(Return(1))
 * println(activity.sample()) // prints 1
 * }}}
 *
 * (For implementers, it may be useful to think of Activity as a monad
 * transformer for an ''Op'' monad over [[com.twitter.util.Var Var]]
 * where Op is like a [[com.twitter.util.Try Try]] with an additional
 * pending state.)
 */
case class Activity[+T](run: Var[Activity.State[T]]) {
  import Activity._

  /**
   * Map a T-typed activity to a U-typed one.
   */
  def map[U](f: T => U): Activity[U] = collect { case x => f(x) }

  /**
   * Map the states of a T-typed activity to a U-typed one.
   */
  def mapState[U](f: Activity.State[T] => Activity.State[U]): Activity[U] = Activity(run.map(f))

  /**
   * Build a new activity by applying `f` to each value. When
   * `f` is not defined for this activity's current value, the derived
   * activity becomes pending.
   */
  def collect[U](f: PartialFunction[T, U]): Activity[U] = flatMap {
    case t if f.isDefinedAt(t) =>
      try Activity.value(f(t))
      catch {
        case NonFatal(exc) => Activity.exception(exc)
      }
    case _ => Activity.pending
  }

  /**
   * Join two activities.
   */
  def join[U](that: Activity[U]): Activity[(T, U)] =
    for (left <- this; right <- that) yield (left, right)

  /**
   * The activity which behaves as `f` applied to Ok values.
   */
  def flatMap[U](f: T => Activity[U]): Activity[U] =
    Activity(run flatMap {
      case Ok(v) =>
        val a = try f(v)
        catch {
          case NonFatal(exc) => Activity.exception(exc)
        }

        a.run
      case Pending => Var.value(Activity.Pending)
      case exc @ Failed(_) => Var.value(exc)
    })

  /**
   * The activity which behaves as `f`  to the state
   * of this activity.
   */
  def transform[U](f: Activity.State[T] => Activity[U]): Activity[U] =
    Activity(run flatMap { act =>
      val a = try f(act)
      catch {
        case NonFatal(exc) => Activity.exception(exc)
      }
      a.run
    })

  /**
   * Recover a failed activity.
   */
  def handle[U >: T](h: PartialFunction[Throwable, U]): Activity[U] = transform {
    case Activity.Failed(e) if h.isDefinedAt(e) => Activity.value(h(e))
    case Activity.Pending => Activity.pending
    case Activity.Failed(e) => Activity.exception(e)
    case Activity.Ok(t) => Activity.value(t)
  }

  /**
   * An [[com.twitter.util.Event Event]] of states.
   */
  def states: Event[State[T]] = run.changes

  /**
   * An [[com.twitter.util.Event Event]] containing only nonpending
   * values.
   */
  def values: Event[Try[T]] = states collect {
    case Ok(v) => Return(v)
    case Failed(exc) => Throw(exc)
  }

  /**
   * Sample the current value of this activity. Sample throws an
   * exception if the activity is in pending state or has failed.
   */
  def sample(): T = Activity.sample(this)

  /**
   * Stabilize the value of this activity such that
   * once an [[com.twitter.util.Activity.Ok Ok]] value has been returned
   * it stops error propagation and instead returns the last Ok value.
   */
  def stabilize: Activity[T] =
    Activity(states.foldLeft[Activity.State[T]](Activity.Pending) {
      case (_, next @ Activity.Ok(_)) => next
      case (prev @ Activity.Ok(_), _) => prev
      case (_, next) => next
    })
}

/**
 * Note: There is a Java-friendly API for this object: [[com.twitter.util.Activities]].
 */
object Activity {

  /**
   * Create a new pending activity. The activity's state is updated by
   * the given witness.
   */
  def apply[T](): (Activity[T], Witness[Try[T]]) = {
    val v = Var(Pending: State[T])
    val w: Witness[Try[T]] = Witness(v) comap {
      case Return(v) => Ok(v)
      case Throw(exc) => Failed(exc)
    }

    (Activity(v), w)
  }

  /**
   * Constructs an Activity from a state Event.
   */
  def apply[T](states: Event[State[T]]): Activity[T] =
    Activity(Var(Pending, states))

  /**
   * Collect a collection of activities into an activity of a collection
   * of values.
   *
   * @usecase def collect[T](activities: Coll[Activity[T]]): Activity[Coll[T]]
   */
  def collect[T: ClassTag, CC[X] <: Iterable[X]](
    acts: CC[Activity[T]]
  )(
    implicit factory: Factory[T, CC[T]]
  ): Activity[CC[T]] = {
    collect(acts, false)
  }

  /**
   * Collect a collection of activities into an activity of a collection
   * of values. This version relies on [[Var]]'s collectIndependent, and has
   * the same benefits and drawbacks of that method.
   *
   * Like Var.collectIndependent this is a workaround and should be deprecated when a version
   * of Var.collect without a stack overflow issue is implemented.
   *
   * @usecase def collectIndependent[T](activities: Coll[Activity[T]]): Activity[Coll[T]]
   */
  def collectIndependent[T: ClassTag, CC[X] <: Iterable[X]](
    acts: CC[Activity[T]]
  )(
    implicit factory: Factory[T, CC[T]]
  ): Activity[CC[T]] = {
    collect(acts, true)
  }

  private[this] def collect[T: ClassTag, CC[X] <: Iterable[X]](
    acts: CC[Activity[T]],
    collectIndependent: Boolean
  )(
    implicit factory: Factory[T, CC[T]]
  ): Activity[CC[T]] = {
    if (acts.isEmpty)
      return Activity.value(factory.fromSpecific(Nil))

    val states: Iterable[Var[State[T]]] = acts.map(_.run)
    val stateVar: Var[Iterable[State[T]]] = if (collectIndependent) {
      Var.collectIndependent(states)
    } else {
      Var.collect(states)
    }

    def flip(states: Iterable[State[T]]): State[CC[T]] = {
      val notOk = states find {
        case Pending | Failed(_) => true
        case Ok(_) => false
      }

      notOk match {
        case None =>
        case Some(Pending) => return Pending
        case Some(f @ Failed(_)) => return f
        case Some(_) => assert(false)
      }

      val ts = factory.newBuilder
      states foreach {
        case Ok(t) => ts += t
        case _ => assert(false)
      }

      Ok(ts.result)
    }

    Activity(stateVar map flip)
  }

  /**
   * Join 2 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B](a: Activity[A], b: Activity[B]): Activity[(A, B)] = collect(Seq(a, b)) map { ss =>
    (ss(0).asInstanceOf[A], ss(1).asInstanceOf[B])
  }

  /**
   * Join 3 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C](a: Activity[A], b: Activity[B], c: Activity[C]): Activity[(A, B, C)] =
    collect(Seq(a, b, c)) map { ss =>
      (ss(0).asInstanceOf[A], ss(1).asInstanceOf[B], ss(2).asInstanceOf[C])
    }

  /**
   * Join 4 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D]
  ): Activity[(A, B, C, D)] = collect(Seq(a, b, c, d)) map { ss =>
    (ss(0).asInstanceOf[A], ss(1).asInstanceOf[B], ss(2).asInstanceOf[C], ss(3).asInstanceOf[D])
  }

  /**
   * Join 5 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E]
  ): Activity[(A, B, C, D, E)] = collect(Seq(a, b, c, d, e)) map { ss =>
    (
      ss(0).asInstanceOf[A],
      ss(1).asInstanceOf[B],
      ss(2).asInstanceOf[C],
      ss(3).asInstanceOf[D],
      ss(4).asInstanceOf[E]
    )
  }

  /**
   * Join 6 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F]
  ): Activity[(A, B, C, D, E, F)] = collect(Seq(a, b, c, d, e, f)) map { ss =>
    (
      ss(0).asInstanceOf[A],
      ss(1).asInstanceOf[B],
      ss(2).asInstanceOf[C],
      ss(3).asInstanceOf[D],
      ss(4).asInstanceOf[E],
      ss(5).asInstanceOf[F]
    )
  }

  /**
   * Join 7 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F],
    g: Activity[G]
  ): Activity[(A, B, C, D, E, F, G)] = collect(Seq(a, b, c, d, e, f, g)) map { ss =>
    (
      ss(0).asInstanceOf[A],
      ss(1).asInstanceOf[B],
      ss(2).asInstanceOf[C],
      ss(3).asInstanceOf[D],
      ss(4).asInstanceOf[E],
      ss(5).asInstanceOf[F],
      ss(6).asInstanceOf[G]
    )
  }

  /**
   * Join 8 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F],
    g: Activity[G],
    h: Activity[H]
  ): Activity[(A, B, C, D, E, F, G, H)] = collect(Seq(a, b, c, d, e, f, g, h)) map { ss =>
    (
      ss(0).asInstanceOf[A],
      ss(1).asInstanceOf[B],
      ss(2).asInstanceOf[C],
      ss(3).asInstanceOf[D],
      ss(4).asInstanceOf[E],
      ss(5).asInstanceOf[F],
      ss(6).asInstanceOf[G],
      ss(7).asInstanceOf[H]
    )
  }

  /**
   * Join 9 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F],
    g: Activity[G],
    h: Activity[H],
    i: Activity[I]
  ): Activity[(A, B, C, D, E, F, G, H, I)] = collect(Seq(a, b, c, d, e, f, g, h, i)) map { ss =>
    (
      ss(0).asInstanceOf[A],
      ss(1).asInstanceOf[B],
      ss(2).asInstanceOf[C],
      ss(3).asInstanceOf[D],
      ss(4).asInstanceOf[E],
      ss(5).asInstanceOf[F],
      ss(6).asInstanceOf[G],
      ss(7).asInstanceOf[H],
      ss(8).asInstanceOf[I]
    )
  }

  /**
   * Join 10 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F],
    g: Activity[G],
    h: Activity[H],
    i: Activity[I],
    j: Activity[J]
  ): Activity[(A, B, C, D, E, F, G, H, I, J)] = collect(Seq(a, b, c, d, e, f, g, h, i, j)) map {
    ss =>
      (
        ss(0).asInstanceOf[A],
        ss(1).asInstanceOf[B],
        ss(2).asInstanceOf[C],
        ss(3).asInstanceOf[D],
        ss(4).asInstanceOf[E],
        ss(5).asInstanceOf[F],
        ss(6).asInstanceOf[G],
        ss(7).asInstanceOf[H],
        ss(8).asInstanceOf[I],
        ss(9).asInstanceOf[J]
      )
  }

  /**
   * Join 11 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F],
    g: Activity[G],
    h: Activity[H],
    i: Activity[I],
    j: Activity[J],
    k: Activity[K]
  ): Activity[(A, B, C, D, E, F, G, H, I, J, K)] =
    collect(Seq(a, b, c, d, e, f, g, h, i, j, k)) map { ss =>
      (
        ss(0).asInstanceOf[A],
        ss(1).asInstanceOf[B],
        ss(2).asInstanceOf[C],
        ss(3).asInstanceOf[D],
        ss(4).asInstanceOf[E],
        ss(5).asInstanceOf[F],
        ss(6).asInstanceOf[G],
        ss(7).asInstanceOf[H],
        ss(8).asInstanceOf[I],
        ss(9).asInstanceOf[J],
        ss(10).asInstanceOf[K]
      )
    }

  /**
   * Join 12 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F],
    g: Activity[G],
    h: Activity[H],
    i: Activity[I],
    j: Activity[J],
    k: Activity[K],
    l: Activity[L]
  ): Activity[(A, B, C, D, E, F, G, H, I, J, K, L)] =
    collect(Seq(a, b, c, d, e, f, g, h, i, j, k, l)) map { ss =>
      (
        ss(0).asInstanceOf[A],
        ss(1).asInstanceOf[B],
        ss(2).asInstanceOf[C],
        ss(3).asInstanceOf[D],
        ss(4).asInstanceOf[E],
        ss(5).asInstanceOf[F],
        ss(6).asInstanceOf[G],
        ss(7).asInstanceOf[H],
        ss(8).asInstanceOf[I],
        ss(9).asInstanceOf[J],
        ss(10).asInstanceOf[K],
        ss(11).asInstanceOf[L]
      )
    }

  /**
   * Join 13 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F],
    g: Activity[G],
    h: Activity[H],
    i: Activity[I],
    j: Activity[J],
    k: Activity[K],
    l: Activity[L],
    m: Activity[M]
  ): Activity[(A, B, C, D, E, F, G, H, I, J, K, L, M)] =
    collect(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m)) map { ss =>
      (
        ss(0).asInstanceOf[A],
        ss(1).asInstanceOf[B],
        ss(2).asInstanceOf[C],
        ss(3).asInstanceOf[D],
        ss(4).asInstanceOf[E],
        ss(5).asInstanceOf[F],
        ss(6).asInstanceOf[G],
        ss(7).asInstanceOf[H],
        ss(8).asInstanceOf[I],
        ss(9).asInstanceOf[J],
        ss(10).asInstanceOf[K],
        ss(11).asInstanceOf[L],
        ss(12).asInstanceOf[M]
      )
    }

  /**
   * Join 14 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F],
    g: Activity[G],
    h: Activity[H],
    i: Activity[I],
    j: Activity[J],
    k: Activity[K],
    l: Activity[L],
    m: Activity[M],
    n: Activity[N]
  ): Activity[(A, B, C, D, E, F, G, H, I, J, K, L, M, N)] =
    collect(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n)) map { ss =>
      (
        ss(0).asInstanceOf[A],
        ss(1).asInstanceOf[B],
        ss(2).asInstanceOf[C],
        ss(3).asInstanceOf[D],
        ss(4).asInstanceOf[E],
        ss(5).asInstanceOf[F],
        ss(6).asInstanceOf[G],
        ss(7).asInstanceOf[H],
        ss(8).asInstanceOf[I],
        ss(9).asInstanceOf[J],
        ss(10).asInstanceOf[K],
        ss(11).asInstanceOf[L],
        ss(12).asInstanceOf[M],
        ss(13).asInstanceOf[N]
      )
    }

  /**
   * Join 15 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F],
    g: Activity[G],
    h: Activity[H],
    i: Activity[I],
    j: Activity[J],
    k: Activity[K],
    l: Activity[L],
    m: Activity[M],
    n: Activity[N],
    o: Activity[O]
  ): Activity[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] =
    collect(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o)) map { ss =>
      (
        ss(0).asInstanceOf[A],
        ss(1).asInstanceOf[B],
        ss(2).asInstanceOf[C],
        ss(3).asInstanceOf[D],
        ss(4).asInstanceOf[E],
        ss(5).asInstanceOf[F],
        ss(6).asInstanceOf[G],
        ss(7).asInstanceOf[H],
        ss(8).asInstanceOf[I],
        ss(9).asInstanceOf[J],
        ss(10).asInstanceOf[K],
        ss(11).asInstanceOf[L],
        ss(12).asInstanceOf[M],
        ss(13).asInstanceOf[N],
        ss(14).asInstanceOf[O]
      )
    }

  /**
   * Join 16 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F],
    g: Activity[G],
    h: Activity[H],
    i: Activity[I],
    j: Activity[J],
    k: Activity[K],
    l: Activity[L],
    m: Activity[M],
    n: Activity[N],
    o: Activity[O],
    p: Activity[P]
  ): Activity[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)] =
    collect(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p)) map { ss =>
      (
        ss(0).asInstanceOf[A],
        ss(1).asInstanceOf[B],
        ss(2).asInstanceOf[C],
        ss(3).asInstanceOf[D],
        ss(4).asInstanceOf[E],
        ss(5).asInstanceOf[F],
        ss(6).asInstanceOf[G],
        ss(7).asInstanceOf[H],
        ss(8).asInstanceOf[I],
        ss(9).asInstanceOf[J],
        ss(10).asInstanceOf[K],
        ss(11).asInstanceOf[L],
        ss(12).asInstanceOf[M],
        ss(13).asInstanceOf[N],
        ss(14).asInstanceOf[O],
        ss(15).asInstanceOf[P]
      )
    }

  /**
   * Join 17 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F],
    g: Activity[G],
    h: Activity[H],
    i: Activity[I],
    j: Activity[J],
    k: Activity[K],
    l: Activity[L],
    m: Activity[M],
    n: Activity[N],
    o: Activity[O],
    p: Activity[P],
    q: Activity[Q]
  ): Activity[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)] =
    collect(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q)) map { ss =>
      (
        ss(0).asInstanceOf[A],
        ss(1).asInstanceOf[B],
        ss(2).asInstanceOf[C],
        ss(3).asInstanceOf[D],
        ss(4).asInstanceOf[E],
        ss(5).asInstanceOf[F],
        ss(6).asInstanceOf[G],
        ss(7).asInstanceOf[H],
        ss(8).asInstanceOf[I],
        ss(9).asInstanceOf[J],
        ss(10).asInstanceOf[K],
        ss(11).asInstanceOf[L],
        ss(12).asInstanceOf[M],
        ss(13).asInstanceOf[N],
        ss(14).asInstanceOf[O],
        ss(15).asInstanceOf[P],
        ss(16).asInstanceOf[Q]
      )
    }

  /**
   * Join 18 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F],
    g: Activity[G],
    h: Activity[H],
    i: Activity[I],
    j: Activity[J],
    k: Activity[K],
    l: Activity[L],
    m: Activity[M],
    n: Activity[N],
    o: Activity[O],
    p: Activity[P],
    q: Activity[Q],
    r: Activity[R]
  ): Activity[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)] =
    collect(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r)) map { ss =>
      (
        ss(0).asInstanceOf[A],
        ss(1).asInstanceOf[B],
        ss(2).asInstanceOf[C],
        ss(3).asInstanceOf[D],
        ss(4).asInstanceOf[E],
        ss(5).asInstanceOf[F],
        ss(6).asInstanceOf[G],
        ss(7).asInstanceOf[H],
        ss(8).asInstanceOf[I],
        ss(9).asInstanceOf[J],
        ss(10).asInstanceOf[K],
        ss(11).asInstanceOf[L],
        ss(12).asInstanceOf[M],
        ss(13).asInstanceOf[N],
        ss(14).asInstanceOf[O],
        ss(15).asInstanceOf[P],
        ss(16).asInstanceOf[Q],
        ss(17).asInstanceOf[R]
      )
    }

  /**
   * Join 19 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F],
    g: Activity[G],
    h: Activity[H],
    i: Activity[I],
    j: Activity[J],
    k: Activity[K],
    l: Activity[L],
    m: Activity[M],
    n: Activity[N],
    o: Activity[O],
    p: Activity[P],
    q: Activity[Q],
    r: Activity[R],
    s: Activity[S]
  ): Activity[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)] =
    collect(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s)) map { ss =>
      (
        ss(0).asInstanceOf[A],
        ss(1).asInstanceOf[B],
        ss(2).asInstanceOf[C],
        ss(3).asInstanceOf[D],
        ss(4).asInstanceOf[E],
        ss(5).asInstanceOf[F],
        ss(6).asInstanceOf[G],
        ss(7).asInstanceOf[H],
        ss(8).asInstanceOf[I],
        ss(9).asInstanceOf[J],
        ss(10).asInstanceOf[K],
        ss(11).asInstanceOf[L],
        ss(12).asInstanceOf[M],
        ss(13).asInstanceOf[N],
        ss(14).asInstanceOf[O],
        ss(15).asInstanceOf[P],
        ss(16).asInstanceOf[Q],
        ss(17).asInstanceOf[R],
        ss(18).asInstanceOf[S]
      )
    }

  /**
   * Join 20 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F],
    g: Activity[G],
    h: Activity[H],
    i: Activity[I],
    j: Activity[J],
    k: Activity[K],
    l: Activity[L],
    m: Activity[M],
    n: Activity[N],
    o: Activity[O],
    p: Activity[P],
    q: Activity[Q],
    r: Activity[R],
    s: Activity[S],
    t: Activity[T]
  ): Activity[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)] =
    collect(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t)) map { ss =>
      (
        ss(0).asInstanceOf[A],
        ss(1).asInstanceOf[B],
        ss(2).asInstanceOf[C],
        ss(3).asInstanceOf[D],
        ss(4).asInstanceOf[E],
        ss(5).asInstanceOf[F],
        ss(6).asInstanceOf[G],
        ss(7).asInstanceOf[H],
        ss(8).asInstanceOf[I],
        ss(9).asInstanceOf[J],
        ss(10).asInstanceOf[K],
        ss(11).asInstanceOf[L],
        ss(12).asInstanceOf[M],
        ss(13).asInstanceOf[N],
        ss(14).asInstanceOf[O],
        ss(15).asInstanceOf[P],
        ss(16).asInstanceOf[Q],
        ss(17).asInstanceOf[R],
        ss(18).asInstanceOf[S],
        ss(19).asInstanceOf[T]
      )
    }

  /**
   * Join 21 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F],
    g: Activity[G],
    h: Activity[H],
    i: Activity[I],
    j: Activity[J],
    k: Activity[K],
    l: Activity[L],
    m: Activity[M],
    n: Activity[N],
    o: Activity[O],
    p: Activity[P],
    q: Activity[Q],
    r: Activity[R],
    s: Activity[S],
    t: Activity[T],
    u: Activity[U]
  ): Activity[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)] =
    collect(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u)) map { ss =>
      (
        ss(0).asInstanceOf[A],
        ss(1).asInstanceOf[B],
        ss(2).asInstanceOf[C],
        ss(3).asInstanceOf[D],
        ss(4).asInstanceOf[E],
        ss(5).asInstanceOf[F],
        ss(6).asInstanceOf[G],
        ss(7).asInstanceOf[H],
        ss(8).asInstanceOf[I],
        ss(9).asInstanceOf[J],
        ss(10).asInstanceOf[K],
        ss(11).asInstanceOf[L],
        ss(12).asInstanceOf[M],
        ss(13).asInstanceOf[N],
        ss(14).asInstanceOf[O],
        ss(15).asInstanceOf[P],
        ss(16).asInstanceOf[Q],
        ss(17).asInstanceOf[R],
        ss(18).asInstanceOf[S],
        ss(19).asInstanceOf[T],
        ss(20).asInstanceOf[U]
      )
    }

  /**
   * Join 22 Activities. The returned Activity is complete when all
   * underlying Activities are nonpending. It fails immediately if any of them
   * do.
   */
  def join[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V](
    a: Activity[A],
    b: Activity[B],
    c: Activity[C],
    d: Activity[D],
    e: Activity[E],
    f: Activity[F],
    g: Activity[G],
    h: Activity[H],
    i: Activity[I],
    j: Activity[J],
    k: Activity[K],
    l: Activity[L],
    m: Activity[M],
    n: Activity[N],
    o: Activity[O],
    p: Activity[P],
    q: Activity[Q],
    r: Activity[R],
    s: Activity[S],
    t: Activity[T],
    u: Activity[U],
    v: Activity[V]
  ): Activity[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)] =
    collect(Seq(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v)) map { ss =>
      (
        ss(0).asInstanceOf[A],
        ss(1).asInstanceOf[B],
        ss(2).asInstanceOf[C],
        ss(3).asInstanceOf[D],
        ss(4).asInstanceOf[E],
        ss(5).asInstanceOf[F],
        ss(6).asInstanceOf[G],
        ss(7).asInstanceOf[H],
        ss(8).asInstanceOf[I],
        ss(9).asInstanceOf[J],
        ss(10).asInstanceOf[K],
        ss(11).asInstanceOf[L],
        ss(12).asInstanceOf[M],
        ss(13).asInstanceOf[N],
        ss(14).asInstanceOf[O],
        ss(15).asInstanceOf[P],
        ss(16).asInstanceOf[Q],
        ss(17).asInstanceOf[R],
        ss(18).asInstanceOf[S],
        ss(19).asInstanceOf[T],
        ss(20).asInstanceOf[U],
        ss(21).asInstanceOf[V]
      )
    }

  /**
   * A Java friendly method for `Activity.collect()`.
   */
  def collect[T <: Object](activities: JList[Activity[T]]): Activity[JList[T]] = {
    val list = activities.asScala.asInstanceOf[Buffer[Activity[Object]]]
    collect(list).map(_.asJava).asInstanceOf[Activity[JList[T]]]
  }

  /**
   * Sample given `Activity`.
   */
  def sample[T](act: Activity[T]): T =
    act.run.sample() match {
      case Ok(t) => t
      case Pending => throw new IllegalStateException("Still pending")
      case Failed(exc) => throw exc
    }

  /**
   * Create a new static activity with value `v`.
   */
  def value[T](v: T): Activity[T] = Activity(Var.value(Ok(v)))

  /**
   * Create an activity backed by a [[com.twitter.util.Future]].
   *
   * The resultant `Activity` is pending until the original `Future` is
   * satisfied. `Future` success or failure corresponds to the expected
   * `Activity.Ok` or `Activity.Failed` result.
   *
   * Closure of observations of the `run` `Var` of the resultant `Activity` is
   * ''not'' propagated to the original `Future`. That is to say, invoking
   * `close()` on observations of `Activity.run` will not result in the
   * cancellation of the original `Future`.
   */
  def future[T](f: Future[T]): Activity[T] = {
    val run = Var(Pending: State[T])
    f respond {
      case Return(v) => run() = Ok(v)
      case Throw(e) => run() = Failed(e)
    }
    Activity(run)
  }

  /**
   * Create a new static activity with exception `exc`.
   */
  def exception(exc: Throwable): Activity[Nothing] = Activity(Var.value(Failed(exc)))

  /**
   * A static Activity that is pending.
   */
  val pending: Activity[Nothing] = Activity(Var.value(Pending))

  /**
   * An ADT describing the state of an Activity.
   */
  sealed trait State[+T]

  /**
   * The activity is running with a current value of `t`.
   */
  case class Ok[T](t: T) extends State[T]

  /**
   * The activity is pending output.
   */
  object Pending extends State[Nothing]

  /**
   * The activity has failed, with exception `exc`.
   */
  case class Failed(exc: Throwable) extends State[Nothing]
}
