package com.twitter.util

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.annotation.tailrec
import scala.collection.generic.CanBuild
import scala.collection.immutable.Queue
import scala.language.higherKinds

/**
 * Events are instantaneous values, defined only at particular
 * instants in time (cf. [[com.twitter.util.Var Vars]], which are
 * defined at all times). It is possible to view Events as the
 * discrete counterpart to [[com.twitter.util.Var Var]]'s continuous
 * nature.
 *
 * Events are observed by registering [[com.twitter.util.Witness Witnesses]]
 * to which the Event's values are notified.
 *
 * Note: There is a Java-friendly API for this trait: [[com.twitter.util.AbstractEvent]].
 */
trait Event[+T] { self =>
  /**
   * Register the given [[com.twitter.util.Witness Witness]] to
   * this Event. Witnesses are notified of new values until it is
   * deregistered by the returned [[com.twitter.util.Closable Closable]].
   */
  def register(s: Witness[T]): Closable

  /**
   * Observe this event with function `s`. Equivalent to
   * `register(Witness(s))`.
   */
  final def respond(s: T => Unit): Closable = register(Witness(s))

  /**
   * Build a new Event by applying the given function to each value
   * observed. Event values for which the partial function `f` does
   * not apply are dropped; other values are transformed by `f`.
   */
  def collect[U](f: PartialFunction[T, U]): Event[U] = new Event[U] {
    def register(s: Witness[U]): Closable =
      self.respond { t =>
        f.runWith(s.notify)(t)
      }
  }

  /**
   * Build a new Event by keeping only those Event values that match
   * the predicate `p`.
   */
  def filter(p: T => Boolean): Event[T] =
    collect { case t if p(t) => t }

  /**
   * Build a new Event by transforming each new event value with `f`.
   */
  def map[U](f: T => U): Event[U] =
    collect { case t => f(t) }

  /**
   * Build a new Event by incrementally accumulating over events,
   * starting with value `z`. Each intermediate aggregate is notified
   * to the derived event.
   */
  def foldLeft[U](z: U)(f: (U, T) => U): Event[U] = new Event[U] {
    def register(s: Witness[U]): Closable = {
      var a = z
      val mu = new Object()
      self.respond { Function.synchronizeWith(mu) { t =>
        a = f(a, t)
        s.notify(a)
      }}
    }
  }

  /**
   * Build a new Event representing a sliding window of at-most `n`.
   * Each event notified by the parent are added to a queue of size
   * at-most `n`. This queue is in turn notified to register of
   * the returned event.
   */
  def sliding(n: Int): Event[Seq[T]] = new Event[Seq[T]] {
    require(n > 0)
    def register(s: Witness[Seq[T]]): Closable = {
      val mu = new Object()
      var q = Queue.empty[T]
      self.respond { t =>
        s.notify(mu.synchronized {
          q = q.enqueue(t)
          while (q.length > n) {
            val (_, q1) = q.dequeue
            q = q1
          }
          q
        })
      }
    }
  }

  /**
   * The Event which merges the events resulting from `f` applied
   * to each element in this Event.
   */
  def mergeMap[U](f: T => Event[U]): Event[U] = new Event[U] {
    def register(s: Witness[U]): Closable = {
      @volatile var inners = Nil: List[Closable]
      val outer = self.respond { el =>
        inners.synchronized { inners ::= f(el).register(s) }
      }

      Closable.make { deadline =>
        outer.close(deadline) before {
          Closable.all(inners:_*).close(deadline)
        }
      }
    }
  }

  /**
   * Merge two Events of different types.
   */
  def select[U](other: Event[U]): Event[Either[T, U]] = new Event[Either[T, U]] {
    def register(s: Witness[Either[T, U]]): Closable = Closable.all(
      self.register(s.comap { t => Left(t) }),
      other.register(s.comap { u => Right(u) })
    )
  }

  /**
   * Merge two event streams in lock-step, combining corresponding
   * event values.
   *
   * @note This can be dangerous! Since the implementation needs to
   * queue outstanding Event-values from the slower producer, if one
   * Event outpaces another, this queue can grow in an unbounded
   * fashion.
   */
  def zip[U](other: Event[U]): Event[(T, U)] = new Event[(T, U)] {
    def register(s: Witness[(T, U)]): Closable = {
      val mu = new Object()
      var state: Option[Either[Queue[T], Queue[U]]] = None

      val left = self.respond { Function.synchronizeWith(mu) { t =>
        state match {
          case None =>
            state = Some(Left(Queue(t)))
          case Some(Left(q)) =>
            state = Some(Left(q enqueue t))
          case Some(Right(Queue(u, rest@_*))) =>
            if (rest.isEmpty) state = None
            else state = Some(Right(Queue(rest:_*)))
            s.notify((t, u))
        }
      }}

      val right = other.respond { Function.synchronizeWith(mu) { u =>
        state match {
          case None =>
            state = Some(Right(Queue(u)))
          case Some(Right(q)) =>
            state = Some(Right(q enqueue u))
          case Some(Left(Queue(t, rest@_*))) =>
            if (rest.isEmpty) state = None
            else state = Some(Left(Queue(rest:_*)))
            s.notify((t, u))
        }
      }}

      Closable.all(left, right)
    }
  }

  /**
   * Join two events into a new Event which notifies a tuple of the
   * last value in each underlying event.
   */
  def joinLast[U](other: Event[U]): Event[(T, U)] = new Event[(T, U)] {
    def register(s: Witness[(T, U)]): Closable = {
      import Event.JoinState
      import JoinState._
      var state: JoinState[T, U] = Empty
      val mu = new Object()
      val left = self.respond { Function.synchronizeWith(mu) { t =>
        state match {
          case Empty | LeftHalf(_) =>
            state = LeftHalf(t)
          case RightHalf(u) =>
            state = Full(t, u)
            s.notify((t, u))
          case Full(_, u) =>
            state = Full(t, u)
            s.notify((t, u))
        }
      }}

      val right = other.respond { Function.synchronizeWith(mu) { u =>
        state match {
          case Empty | RightHalf(_) =>
            state = RightHalf(u)
          case LeftHalf(t) =>
            state = Full(t, u)
            s.notify((t, u))
          case Full(t, _) =>
            state = Full(t, u)
            s.notify((t, u))
        }
      }}

      Closable.all(left, right)
    }
  }

  /**
   * An event which consists of the first `howmany` values
   * in the parent Event.
   */
  def take(howmany: Int): Event[T] = new Event[T] {
    def register(s: Witness[T]): Closable = {
      val n = new AtomicInteger(0)
      val c = new AtomicReference(Closable.nop)
      c.set(self.respond { t =>
        if (n.incrementAndGet() <= howmany) s.notify(t)
        else c.getAndSet(Closable.nop).close()
      })

      if (n.get() == howmany)
        c.getAndSet(Closable.nop).close()

      Closable.ref(c)
    }
  }

  /**
   * Merge two events; the resulting event interleaves events
   * from this and `other`.
   */
  def merge[U >: T](other: Event[U]): Event[U] = new Event[U] {
    def register(s: Witness[U]): Closable = {
      val c1 = self.register(s)
      val c2 = other.register(s)
      Closable.all(c1, c2)
    }
  }

  /**
   * Progressively build a collection of events using the passed-in
   * builder. A value containing the current version of the collection
   * is notified for each incoming event.
   */
  def build[U >: T, That](implicit cbf: CanBuild[U, That]) = new Event[That] {
    def register(s: Witness[That]): Closable = {
      val b = cbf()
      self.respond { t =>
        b += t
        s.notify(b.result())
      }
    }
  }

  /**
   * A Future which is satisfied by the first value observed.
   */
  def toFuture(): Future[T] = {
    val p = new Promise[T]
    val c = register(Witness(p))
    p.setInterruptHandler { case exc =>
      p.updateIfEmpty(Throw(exc))
    }
    p.ensure { c.close() }
  }

  /**
   * The [[Event]] that stores the difference between successive
   * updates to the parent event. This can be used to perform
   * incremental computation on large data structures.
   */
  def diff[CC[_]: Diffable, U](implicit toCC: T <:< CC[U]): Event[Diff[CC, U]] = new Event[Diff[CC, U]] {
    def register(s: Witness[Diff[CC, U]]): Closable = {
      var left: CC[U] = Diffable.empty[CC, U]
      self.respond { t =>
        synchronized {
          val right = toCC(t)
          val diff = Diffable.diff(left, right)
          left = right
          s.notify(diff)
        }
      }
    }
  }


  /**
   * Patch up an [[Event]] of differences (like those produced by
   * [[Event.diff]]) into an [[Event]] that reflects the current
   * version of a data structure. That is: `(event:
   * Event[CC[T]]).diff.patch` is equivalent to `event`
   */
  def patch[CC[_]: Diffable, U](implicit ev: T <:< Diff[CC, U]): Event[CC[U]] = new Event[CC[U]] {
    def register(s: Witness[CC[U]]): Closable = {
      var last: CC[U] = Diffable.empty[CC, U]
      self.respond { diff =>
        synchronized {
          last = diff.patch(last)
          s.notify(last)
        }
      }
    }
  }

  /**
   * Build a new Event by keeping only those Event values where the
   * equality predicate `eq` applied to the current and new values
   * does not match.
   */
  def dedupWith(eq: (T, T) => Boolean): Event[T] =
    sliding(2).collect {
      case Seq(init) => init
      case Seq(current, next) if !eq(current, next) => next
    }

  /**
   * Builds a new Event by keeping only the Events where
   * the previous and current values are not `==` to each other.
   */
  def dedup: Event[T] = dedupWith { (a, b) => a == b }
}

/**
 * Abstract `Event` class for Java compatibility.
 */
abstract class AbstractEvent[T] extends Event[T]

/**
 * Note: There is a Java-friendly API for this object: [[com.twitter.util.Events]].
 */
object Event {
  private sealed trait JoinState[+T, +U]
  private object JoinState {
    object Empty extends JoinState[Nothing, Nothing]
    case class LeftHalf[T](t: T) extends JoinState[T, Nothing]
    case class RightHalf[U](u: U) extends JoinState[Nothing, U]
    case class Full[T, U](t: T, u: U) extends JoinState[T, U]
  }

  /**
   * A new [[Event]] of type `T` which is also a [[Witness]].
   */
  def apply[T](): Event[T] with Witness[T] = new Event[T] with Witness[T] {
    private[this] val witnesses = new AtomicReference(Set.empty[Witness[T]])

    def register(w: Witness[T]): Closable = {
      casAdd(w)
      Closable.make { _ =>
        casRemove(w)
        Future.Done
      }
    }

    /**
     * Notifies registered witnesses
     *
     * @note This method is synchronized to ensure that all witnesses
     * receive notifications in the same order. Consequently it will block
     * until the witnesses are notified.
     */
    def notify(t: T): Unit = synchronized {
      val current = witnesses.get
      for (w <- current)
        w.notify(t)
    }

    @tailrec
    private def casAdd(w: Witness[T]): Unit = {
      val current = witnesses.get
      if (!witnesses.compareAndSet(current, current + w)) {
        casAdd(w)
      }
    }

    @tailrec
    private def casRemove(w: Witness[T]): Unit = {
      val current = witnesses.get
      if (!witnesses.compareAndSet(current, current - w)) {
        casRemove(w)
      }
    }
  }
}

/**
 * A witness is the recipient of [[com.twitter.util.Event Event]].
 *
 * Note: There is a Java-friendly API for this trait: [[com.twitter.util.AbstractWitness]].
 */
trait Witness[-N] { self =>
  /**
   * Notify this Witness with the given note.
   */
  def notify(note: N): Unit

  def comap[M](f: M => N): Witness[M] = new Witness[M] {
    def notify(m: M) = self.notify(f(m))
  }
}

/**
 * Abstract `Witness` class for Java compatibility.
 */
abstract class AbstractWitness[T] extends Witness[T]

/**
 * Note: There is Java-friendly API for this object: [[com.twitter.util.Witnesses]].
 */
object Witness {
  /**
   * Create a [[Witness]] from an [[AtomicReference atomic reference]].
   */
  def apply[T](ref: AtomicReference[T]): Witness[T] = new Witness[T] {
    def notify(t: T): Unit = ref.set(t)
  }

  /**
   * Create a [[Witness]] from a [[com.twitter.util.Promise Promise]].
   */
  def apply[T](p: Promise[T]): Witness[T] = new Witness[T] {
    def notify(t: T): Unit = p.updateIfEmpty(Return(t))
  }

  /**
   * Create a [[Witness]] from a [[Function1]].
   */
  def apply[T](f: T => Unit): Witness[T] = new Witness[T] {
    def notify(t: T): Unit = f(t)
  }

  /**
   * Create a [[Witness]] from an [[Updatable]].
   */
  def apply[T](u: Updatable[T]): Witness[T] = new Witness[T] {
    def notify(t: T): Unit = u() = t
  }

  /**
   * Create a [[Witness]] which keeps only a `java.lang.ref.WeakReference`
   * to the [[Function1]].
   *
   * @see [[Var.patch]] for example.
   */
  def weakReference[T](f: T => Unit): Witness[T] = new Witness[T] {
    private[this] val weakRef = new WeakReference(f)

    def notify(note: T): Unit = {
      val fn = weakRef.get()
      if (fn != null)
        fn(note)
    }
  }

  /**
   * Create a [[Witness]] which keeps only a `java.lang.ref.WeakReference`
   * to `u`.
   *
   * @see [[Var.apply]] for example.
   */
  def weakReference[T](u: Updatable[T]): Witness[T] = new Witness[T] {
    private[this] val weakRef = new WeakReference(u)

    def notify(note: T): Unit = {
      val updatable = weakRef.get()
      if (updatable != null)
        updatable.update(note)
    }
  }

  /**
   * A [[Witness]] which prints to the console.
   */
  val printer: Witness[Any] = Witness(println(_: Any))
}

/**
 * A Java analog of `Event[A]()`.
 */
class WitnessedEvent[T] extends Event[T] with Witness[T] {

  private[this] val underlying = Event[T]()

  def register(s: Witness[T]): Closable = underlying.register(s)

  def notify(note: T): Unit = underlying.notify(note)
}
