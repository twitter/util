package com.twitter.concurrent

import java.util.concurrent.atomic.AtomicBoolean
import scala.util.Random
import scala.collection.mutable.WeakHashMap

import com.twitter.util.{Future, Promise, Return}
import com.twitter.util.{Time, Timer, Duration}

// todo:  pre-synchronization activation (eg. "activate()") so that we may
// have reusable timeouts, etc.?
// todo: provide something similar to "withNack" from CML?

object Offer {
  /**
   * The offer that chooses exactly one of the given offers, choosing
   * among immediately realizable offers at random.
   */
  def choose[T](ofs: Offer[T]*): Offer[T] = new Offer[T] {
    require(!ofs.isEmpty)
    def poll(): Option[() => T] = {
      val rng = new Random(Time.now.inMilliseconds)
      val shuffled = rng.shuffle(ofs.toList)
      shuffled.foldLeft(None: Option[() => T]) {
        case (None, of) => of.poll()
        case (some@Some(_), _) => some
      }
    }

    def enqueue(setter: Setter) = {
      val dqs = ofs map { _.enqueue(setter) }
      () => dqs foreach { dq => dq() }
    }

    def objects: Seq[AnyRef] = ofs flatMap { _.objects }
  }

  /**
   * Convenience function to choose, then sync.
   */
  def select[T](ofs: Offer[T]*): Future[T] = choose(ofs:_*)()

  /**
   * The constant offer: always available with the given value.
   * Note: Computed by-name.
   */
  def const[T](t: =>T): Offer[T] = new Offer[T] {
    def poll() = Some(() => t)
    def enqueue(setter: Setter) =
      throw new IllegalStateException("enqueue cannot be called after succesfull poll()!")
    def objects = Seq()
  }

  /**
   * An offer that is never available.
   */
  def never[T]: Offer[T] = new Offer[T] {
    def poll() = None
    def enqueue(setter: Setter) = () => ()
    val objects = Seq()
  }

  /**
   * An offer that is available after the given time.
   */
  def timeout(timeout: Duration)(implicit timer: Timer) = new Offer[Unit] {
    private[this] val deadline = timeout.fromNow

    def poll() = if (deadline <= Time.now) Some(() => ()) else None
    def enqueue(setter: Setter) = {
      val task = timer.schedule(deadline) {
        setter() foreach { set => set(() => ()) }
      }
      () => task.cancel()
    }
    val objects = Seq()
  }

  /**
   *  Defines a total order over heap objects.  This is required
   *  because the identity hash code is not unique across objects.
   *  Thus, we keep a hash map that specifies the order(s) for given
   *  collisions.  We can do this because reference equality is well
   *  defined in Java.  See:
   *
   *    http://gafter.blogspot.com/2007/03/compact-object-comparator.html
   *
   *  For more information.
   */
  private[Offer] object ObjectOrder extends Ordering[AnyRef] {
    private[this] val order = new WeakHashMap[AnyRef, Long]
    private[this] var nextId = 0L

    def compare(a: AnyRef, b: AnyRef): Int = {
      val ha = System.identityHashCode(a)
      val hb = System.identityHashCode(b)
      val d = ha - hb
      if (d != 0)
        return d

      // Slow path:
      synchronized {
        val ia = order.getOrElseUpdate(a, { nextId += 1; nextId })
        val ib = order.getOrElseUpdate(b, { nextId += 1; nextId })
        ia compare ib
      }
    }
  }
}


/**
 * An offer to _communicate_ with another process.  The offer is
 * parameterized on the type of the value communicated.  An offer that
 * * _sends_ a value typically has type {{Unit}}.  An offer is
 * activated by synchronizing it.  Synchronization is done with the
 * apply() method.
 */
trait Offer[+T] { self =>
  import Offer._

  /**
   * A setter represents the atomic realization of an offer
   * synchronization.  One is given to the {{enqueue}} method when
   * the offer cannot be immediately realized.
   *
   * The type is somewhat complex in order to aid in atomic
   * realization and deferred execution.  First, the setter returns
   * {{Some(..)}} only when the synchronization has not yet been
   * realized.  If the setter is invoked, and {{Some(..)}} is
   * returned, the realization of the offer is given to the caller,
   * and a function must be provided that yields the value to be set.
   * The execution of this function may be deferred.
   */
  type Thunk[+A] = () => A
  type Setter = Thunk[Option[Thunk[T] => Unit]]

  /*
   * The following methods (poll, enqueue, objects) are used in
   * synchronization and must be implemented by concrete offers.
   */

  /**
   * Polls this offer.  This is the first stage of synchronization.
   * If the offer is ready, returns {{Some(..)}}.  At this point, the
   * offer is realized, and {{enqueue}} will not be called.
   */
  def poll(): Option[Thunk[T]]

  /**
   * Enqueue the given {{Setter}} for notification of realization of
   * the offer.  This is invoked only if {{poll}} fails.  The return
   * value is a cancellation that is called if the offer is not
   * selected.
   */
  def enqueue(setter: Setter): () => Unit

  /**
   * A sequence of objects needing synchronization before
   * synchronization begins.  This needs to be defined in order to
   * allow for atomic operations across all participants in an offer
   * (eg.  for {{Offer.choose}})
   *
   * The implementation guarantees that the monitor of any object
   * given here is locked (as in {{object.synchronized}}) before
   * invoking {{poll}} or {{enqueue}}.
   */
  def objects: Seq[AnyRef]

  /**
   * Lock the monitors of the objects in {{objs}} in order, then
   * invoke {{f}}.
   */
  private[this] def lock[R](objs: List[AnyRef])(f: () => R): R = {
    objs match {
      case obj :: rest =>
        obj.synchronized { lock(rest)(f) }
      case Nil =>
        f()
    }
  }

  /* Combinators */

  /**
   * Map this offer of type {{T}} into one of type {{U}}.  The
   * translation (performed by {{f}}) is done when the offer has
   * succesfully synchronized.
   */
  def map[U](f: T => U): Offer[U] = new Offer[U] {
    def poll() = self.poll() map { t => () => f(t()) }
    def enqueue(setter: Setter): () => Unit = {
      val tsetter: Offer[T]#Setter = () =>
        setter() map { uset => { tget => uset { () => f(tget()) } } }
      self.enqueue(tsetter)
    }
    def objects: Seq[AnyRef] = self.objects
  }

  /**
   * Like {{map}}, but a constant function.
   */
  def const[U](f: => U): Offer[U] = map { _ => f }

  /**
   * An offer that, when synchronized, fails to synchronize {{this}}
   * immediately, synchronizes on {{other}} instead.  This is useful
   * for providing default values. Eg.:
   *
   * {{{
   * offer OrElse Offer.const { computeDefaultValue() }
   * }}}
   */
  def orElse[U >: T](other: Offer[U]): Offer[U] = new Offer[U] {
    def poll() = self.poll() orElse other.poll()
    def enqueue(setter: Setter) = self.enqueue(setter)
    def objects = self.objects ++ other.objects
  }

  /**
   * Synchronize on this offer indefinitely, invoking the given {{f}}
   * with each successfully synchronized value.  A receiver can use
   * this to enumerate over all received values.
   */
  def foreach(f: T => Unit) {
    this() foreach { v =>
      f(v)
      foreach(f)
    }
  }

  /**
   * Enumerate this offer (ie.  synchronize while there are active
   * responders until the channel is closed) to the given
   * {{ChannelSource}}.  n.b.: this may buffer at most one value, and
   * on close, a buffered value may be dropped.
   *
   *  note: we could make this buffer free by introducing
   *  synchronization around the channel (this would require us to
   *  make generic the concept of synchronization, and would create
   *  far too much complexity for what would never be a use case)
   *
   *  todo: create version with backpressure (using =:= on T, with
   *  convention for acks)
   */
  def enumToChannel[U >: T](ch: ChannelSource[U]) {
    // these are all protected by ch.serialized:
    var buf: Option[T] = None
    var active = false

    def enumerate() {
      // enumerate() is always enter ch.serialized.
      buf foreach { v =>
        ch.send(v)
        buf = None
      }

      this() foreach { v =>
        ch.serialized {
          if (active) {
            ch.send(v)
            enumerate()
          } else {
            buf = Some(v)
          }
        }
      }
    }

    // Only dispatch messages when we have listeners.
    ch.numObservers respond { n =>
      ch.serialized {
        n match {
          case 0 if active =>
            active = false
          case 1 if !active =>
            active = true
            enumerate()
          case _ => ()
        }
      }
    }

    ch.closes foreach { _ =>
      active = false
    }
  }

  /**
   * Create a channel, and enumerate this offer onto it.
   */
  def toChannel: Channel[T] = {
    val ch = new ChannelSource[T]
    enumToChannel(ch)
    ch
  }

  /**
   * Synchronize (discarding the value), and then invoke the given
   * closure.  Convenient for loops.
   */
  def andThen(f: => Unit) {
    this() onSuccess { _ => f }
  }

  /**
   * Synchronize this offer.  This activates this offer and attempts
   * to perform the communication specified.
   *
   * @returns A {{Future}} containing the communicated value.
   */
  def apply(): Future[T] = {
    // We sort the objects here according to their identity.  In this
    // way we avoid deadlock conditions upon locking all the objects.
    // When they are locked in this order, one thread cannot attempt
    // to acquire a lock lower in the ordering than any lock it has
    // already acquired.  Thus there cannot be cycles.
    val objs = (objects toList) sorted(ObjectOrder)

    val action: Thunk[Future[T]] = lock(objs) { () =>
      // first, attempt to poll.  if there are any values immediately
      // available, then simply return this.  we assume the underlying
      // implementation is fair.
      poll() match {
        case Some(res) =>
          () => Future.value(res())
        case None =>
          // we failed to poll, so we need to enqueue
          val promise = new Promise[T]
          val activated = new AtomicBoolean(false)
          val computation = new Promise[() => T]
          val setter: Setter = () => {
            if (activated.compareAndSet(false, true))
              Some { f => computation() = Return(f) }
            else
              None
          }

          val dequeue = enqueue(setter)

          () => {
            // activate the computation.
            computation onSuccess { f =>
              promise() = Return(f())
            }

            promise ensure {
              // finally, dequeue from waitqs.  this doesn't matter for busy
              // queues (since they'd be naturally garbage collected when
              // attempting to dequeue), but for idle queues, we could pile
              // up waiters that are never collected.
              //
              // todo: we could do with per-object locks here instead of
              // doing the whole lock dance again.
              dequeue()
            }
          }
      }
    }

    action()
  }
}
