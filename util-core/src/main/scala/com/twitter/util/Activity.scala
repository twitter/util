package com.twitter.util

import java.util.{List => JList}

import scala.collection.generic.CanBuild
import scala.collection.JavaConverters._
import scala.collection.mutable.Buffer

/**
 * An Activity is a handle to a concurrently running process, producing
 * T-typed values. An activity is in one of three states:
 *
 *  - [[com.twitter.util.Activity.Pending Pending]]: output is pending;
 *  - [[com.twitter.util.Activity.Ok Ok]]: an output is available; and
 *  - [[com.twitter.util.Activity.Failed Failed]]: the process failed with an exception.
 *
 * An activity may transition between any state at any time.
 *
 * (The observant reader will notice that this is really a monad
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
   * Build a new activity by applying `f` to each value. When
   * `f` is not defined for this activity's current value, the derived
   * activity becomes pending.
   */
  def collect[U](f: PartialFunction[T, U]): Activity[U] = flatMap {
    case t if f.isDefinedAt(t) =>
      try Activity.value(f(t)) catch {
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
        val a = try f(v) catch {
          case NonFatal(exc) => Activity.exception(exc)
        }

        a.run
      case Pending => Var.value(Activity.Pending)
      case exc@Failed(_) => Var.value(exc)
    })

  /**
   * The activity which behaves as `f`  to the state
   * of this activity.
   */
  def transform[U](f: Activity.State[T] => Activity[U]): Activity[U] =
    Activity(run flatMap { act =>
      val a = try f(act) catch {
        case NonFatal(exc) => Activity.exception(exc)
      }
      a.run
    })

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
   * Collect a collection of activities into an activity of a collection
   * of values.
   *
   * @usecase def collect[T](activities: Coll[Activity[T]]): Activity[Coll[T]]
   *
   *   @inheritdoc
   */
  def collect[T, CC[X] <: Traversable[X]](acts: CC[Activity[T]])
      (implicit newBuilder: CanBuild[T, CC[T]], cm: ClassManifest[T])
      : Activity[CC[T]] = {
    if (acts.isEmpty)
      return Activity.value(newBuilder().result)

    val states: Traversable[Var[State[T]]] = acts.map(_.run)
    val stateVar: Var[Traversable[State[T]]] = Var.collect(states)

    def flip(states: Traversable[State[T]]): State[CC[T]] = {
      val notOk = states find {
        case Pending | Failed(_) => true
        case Ok(_) => false
      }

      notOk match {
        case None =>
        case Some(Pending) => return Pending
        case Some(f@Failed(_)) => return f
        case Some(_) => assert(false)
      }

      val ts = newBuilder()
      states foreach {
        case Ok(t) => ts += t
        case _ => assert(false)
      }

      Ok(ts.result)
    }

    Activity(stateVar map flip)
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
