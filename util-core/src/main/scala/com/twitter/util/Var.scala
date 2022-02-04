package com.twitter.util

import java.util.{List => JList}
import scala.annotation.tailrec
import scala.collection.{Seq => AnySeq}
import scala.collection.compat.immutable.ArraySeq
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import scala.language.higherKinds

/**
 * Vars are values that vary over time. To create one, you must give it an
 * initial value.
 *
 * {{{
 * val a = Var[Int](1)
 * }}}
 *
 * A Var created this way can be sampled to retrieve its current value,
 *
 * {{{
 * println(Var.sample(a)) // prints 1
 * }}}
 *
 * or, invoked to assign it new values.
 *
 * {{{
 * a.update(2)
 * println(Var.sample(a)) // prints 2
 * }}}
 *
 * Vars can be derived from other Vars.
 *
 * {{{
 * val b = a.flatMap { x => Var(x + 2) }
 * println(Var.sample(b)) // prints 4
 * }}}
 *
 * And, if the underlying is assigned a new value, the derived Var is updated.
 * Updates are computed lazily, so while assignment is cheap, sampling is where
 * we pay the cost of the computation required to derive the new Var.
 *
 * {{{
 * a.update(1)
 * println(Var.sample(b)) // prints 3
 * }}}
 *
 * A key difference between the derived Var and its underlying is that derived
 * Vars can't be assigned new values. That's why `b`, from the example above,
 * can't be invoked to assign it a new value, it can only be sampled.
 *
 * @note Vars do not always perform the minimum amount of
 * re-computation.
 * @note There are no well-defined error semantics for Var. Vars are
 * computed lazily, and the updating thread will receive any
 * exceptions thrown while computing derived Vars.
 *
 * Note: There is a Java-friendly API for this trait: [[com.twitter.util.AbstractVar]].
 */
trait Var[+T] { self =>
  import Var.Observer

  /**
   * Observe this Var. `f` is invoked each time the variable changes,
   * and synchronously with the first call to this method.
   */
  private[util] final def observe(f: T => Unit): Closable = observe(0, Observer(f))

  /**
   * Concrete implementations of Var implement observe. This is
   * called for each toplevel observe. Depths indicate the relative
   * structural depth of the observation, from the frame of reference
   * of the root call to observe. (Each Var derived via flatMap
   * increases the depth.) Depths are used to order the invocation of
   * update callbacks. This is used to ensure that updates proceed in
   * topological order so that every input variable is fully resolved
   * before recomputing a derived variable.
   */
  protected def observe(depth: Int, obs: Observer[T]): Closable

  /**
   * Create a derived variable by applying `f` to the contained
   * value.
   */
  def map[U](f: T => U): Var[U] = flatMap(t => Var.value(f(t)))

  /**
   * Create a dependent Var which behaves as `f` applied to the
   * current value of this Var. FlatMap manages a dynamic dependency
   * graph: the dependent Var is detached and recomputed  whenever
   * the outer Var changes, but only if there are any observers.  An
   * unobserved Var returned by flatMap will not invoke `f`
   */
  def flatMap[U](f: T => Var[U]): Var[U] = new Var[U] {
    private class ObserverManager(depth: Int, obs: Observer[U]) extends Closable with (T => Unit) {
      private[this] var closable: Closable = Closable.nop
      private[this] var closed: Boolean = false

      def apply(t: T): Unit = {
        // We have to synchronize and make sure we're not already closed or else
        // we may generate a new observation that has raced with the a `close`
        // call and lost, therefore making an orphan and a resource leak.
        val toClose = this.synchronized {
          if (closed) Closable.nop
          else {
            val old = closable
            closable = f(t).observe(depth + 1, obs)
            old
          }
        }
        // TODO: Right now we rely on synchronous propagation; and
        // thus also synchronous closes. We should instead perform
        // asynchronous propagation so that it is is safe &
        // predictable to have asynchronously closing Vars, for
        // example. Currently the only source of potentially
        // asynchronous closing is Var.async; here we have modified
        // the external process to close asynchronously with the Var
        // itself. Thus we know the code path here is synchronous:
        // we control all Var implementations, and also all Closable
        // combinators have been modified to evaluate their respective
        // Futures eagerly.
        val done = toClose.close()
        assert(done.isDefined)
      }

      def close(deadline: Time): Future[Unit] = {
        val toClose = this.synchronized {
          closed = true
          val old = closable
          closable = Closable.nop
          old
        }
        toClose.close(deadline)
      }
    }

    def observe(depth: Int, obs: Observer[U]): Closable = {
      val manager = new ObserverManager(depth, obs)
      val outer = self.observe(depth, Observer(manager))
      Closable.sequence(outer, manager)
    }
  }

  def join[U](other: Var[U]): Var[(T, U)] =
    for { t <- self; u <- other } yield (t, u)

  /**
   * An Event where changes in Var are emitted. The current value
   * of this Var is emitted synchronously upon subscription.
   *
   * All changes to this Var are guaranteed to be published to the
   * Event.
   */
  lazy val changes: Event[T] = new Event[T] {
    def register(s: Witness[T]) =
      self.observe { newv => s.notify(newv) }
  }

  /**
   * Produce an [[Event]] reflecting the differences between
   * each update to this [[Var]].
   */
  def diff[CC[_]: Diffable, U](implicit toCC: T <:< CC[U]): Event[Diff[CC, U]] =
    changes.diff

  def sample(): T = Var.sample(this)
}

/**
 * Abstract `Var` class for Java compatibility.
 */
abstract class AbstractVar[T] extends Var[T]

/**
 * Note: There is a Java-friendly API for this object: [[com.twitter.util.Vars]].
 */
object Var {

  /**
   * A Var observer. Observers are owned by exactly one producer,
   * enforced by a leasing mechanism.
   */
  final class Observer[-T](observe: T => Unit) {
    private[this] var thisOwner: AnyRef = null
    private[this] var thisVersion = Long.MinValue

    /**
     * Claim this observer with owner `newOwner`. Claiming
     * an observer gives the owner exclusive rights to publish
     * to it while it has not been claimed by another owner.
     */
    def claim(newOwner: AnyRef): Unit = synchronized {
      if (thisOwner ne newOwner) {
        thisOwner = newOwner
        thisVersion = Long.MinValue
      }
    }

    /**
     * Publish the given versioned value with the given owner.
     * If the owner is not current (because another has claimed
     * the observer), or if the version has already published (by
     * assumption of a monotonically increasing version number)
     * the publish operation is a no-op.
     */
    def publish(owner: AnyRef, value: T, version: Long): Unit = synchronized {
      if ((owner eq thisOwner) && thisVersion < version) {
        thisVersion = version
        observe(value)
      }
    }
  }

  object Observer {
    def apply[T](k: T => Unit): Observer[T] = new Observer(k)
  }

  /**
   * Sample the current value of this Var. Note that this may lead to
   * surprising results for lazily defined Vars: the act of observing
   * a Var may be kick off a process to populate it; the value
   * returned from sample may then reflect an intermediate value.
   */
  def sample[T](v: Var[T]): T = {
    var opt: Option[T] = None
    v.observe(0, Observer(v => opt = Some(v))).close()
    opt.get
  }

  object Sampled {
    def apply[T](v: T): Var[T] = value(v)
    def unapply[T](v: Var[T]): Option[T] = Some(sample(v))
  }

  /**
   * Create a new, updatable Var with an initial value. We call
   * such Vars independent -- derived Vars being dependent
   * on these.
   */
  def apply[T](init: T): Var[T] with Updatable[T] with Extractable[T] =
    new UpdatableVar(init)

  /**
   * Constructs a [[Var]] from an initial value plus an event stream of
   * changes. Note that this eagerly subscribes to the event stream;
   * it is unsubscribed whenever the returned [[Var]] is collected.
   */
  def apply[T](init: T, e: Event[T]): Var[T] = {
    val v = Var(init)

    // In order to support unsubscribing from e when v is no longer referenced
    // we must avoid e keeping a strong reference to v.
    val witness = Witness.weakReference(v)
    Closable.closeOnCollect(e.register(witness), v)

    v
  }

  /**
   * Patch reconstructs a [[Var]] based on observing the incremental
   * changes presented in the underlying [[Diff Diffs]].
   *
   * Note that this eagerly subscribes to the event stream;
   * it is unsubscribed whenever the returned [[Var]] is collected.
   */
  def patch[CC[_]: Diffable, T](diffs: Event[Diff[CC, T]]): Var[CC[T]] = {
    val v = Var(Diffable.empty[CC, T]: CC[T])

    // In order to support unsubscribing from diffs when v is no longer referenced
    // we must avoid diffs keeping a strong reference to v.
    val witness = Witness.weakReference { (diff: Diff[CC, T]) =>
      synchronized {
        v.update(diff.patch(v()))
      }
    }
    val closable = diffs.register(witness)
    Closable.closeOnCollect(closable, v)

    v
  }

  /**
   * Create a new, constant, v-valued Var.
   */
  def value[T](v: T): Var[T] with Extractable[T] = new ConstVar(v)

  /**
   * Collect a collection of Vars into a Var of collection.
   * Var.collect can result in a stack overflow if called with a large sequence.
   * Var.collectIndependent breaks composition with respect to update propagation.
   * That is, collectIndependent can fail to correctly update interdependent vars,
   * but is safe for independent vars.
   *
   * {{{
   *  // Example of difference between collect and collectIndependent:
   *  val v1 = Var(1)
   *  val v2 = v1.map(_*2)
   *  val vCollect = Var.collect(Seq(v1, v2)).map { case Seq(a, b) => (a, b) }
   *  val vCollectIndependent = Var.collectIndependent(Seq(v1, v2)).map { case Seq(a, b) => (a, b) }
   *  val refCollect = new AtomicReference[Seq[(Int, Int)]]
   *  vCollect.changes.build.register(Witness(refCollect))
   *  val refCollectIndependent = new AtomicReference[Seq[(Int, Int)]]
   *  vCollectIndependent.changes.build.register(Witness(refCollectIndependent))
   *  v1() = 2
   *  // refCollect == Vector((1,2), (2,4))
   *  // refCollectIndependent == Vector((1,2), (2,2), (2,4))
   * }}}
   */
  def collect[T](vars: AnySeq[Var[T]]): Var[Seq[T]] = {
    val vs = vars.toIndexedSeq

    def tree(begin: Int, end: Int): Var[Seq[T]] =
      if (begin == end) Var(Seq.empty)
      else if (begin == end - 1) vs(begin).map(t => Seq(t))
      else {
        val n = (end - begin) / 2

        for {
          left <- tree(begin, begin + n)
          right <- tree(begin + n, end)
        } yield left ++ right
      }

    tree(0, vs.length)
  }

  /**
   * Collect a collection of Vars into a Var of collection.
   * Var.collectIndependent breaks composition with respect to update propagation.
   * That is, collectIndependent can fail to correctly update interdependent vars,
   * but is safe for independent vars.
   *
   * {{{
   *  // Example of difference between collect and collectIndependent:
   *  val v1 = Var(1)
   *  val v2 = v1.map(_*2)
   *  val vCollect = Var.collect(Seq(v1, v2)).map { case Seq(a, b) => (a, b) }
   *  val vCollectIndependent = Var.collectIndependent(Seq(v1, v2)).map { case Seq(a, b) => (a, b) }
   *  val refCollect = new AtomicReference[Seq[(Int, Int)]]
   *  vCollect.changes.build.register(Witness(refCollect))
   *  val refCollectIndependent = new AtomicReference[Seq[(Int, Int)]]
   *  vCollectIndependent.changes.build.register(Witness(refCollectIndependent))
   *  v1() = 2
   *  // refCollect == Vector((1,2), (2,4))
   *  // refCollectIndependent == Vector((1,2), (2,2), (2,4))
   * }}}
   */
  def collectIndependent[T](vars: AnySeq[Var[T]]): Var[Seq[T]] =
    async(Seq.empty[T]) { u =>
      val N = vars.size

      // `filling` represents whether or not we have gone through our collection
      // of `Var`s and added observations. Once we have "filled" the `cur` array
      // for the first time, we can publish an initial update to `u`. Subsequent,
      // updates to each constintuent Var are guarded by a lock on the `cur` and
      // we ensure that we publish the update before any new updates can come
      // through.
      //
      // @note there is still a subtle race where we can be in the middle
      // of "filling" and receive updates on previously filled Vars. However, this
      // is an acceptable race since technically we haven't added observations to all
      // of the constituent Vars yet.
      //
      // @note "filling" only works with the guarantee that the initial `observe` is
      // synchronous. This should be the case with Vars since they have an initial value.
      var filling = true
      val cur = new Array[Any](N)

      def publishAt(i: Int): T => Unit = { newValue =>
        cur.synchronized {
          cur(i) = newValue
          if (filling && i == N - 1) filling = false
          if (!filling) {
            // toSeq does not do a deep copy until 2.13
            val copy = new Array[Any](N)
            Array.copy(cur, 0, copy, 0, cur.length)
            u() = ArraySeq.unsafeWrapArray(copy).asInstanceOf[Seq[T]]
          }
        }
      }

      val closables = new Array[Closable](N)
      var i = 0
      val iter = vars.iterator
      while (iter.hasNext) {
        val v = iter.next()
        closables(i) = v.observe(publishAt(i))
        i += 1
      }

      Closable.all(ArraySeq.unsafeWrapArray(closables): _*)
    }

  /**
   * Collect a List of Vars into a new Var of List.
   *
   * @param vars a java.util.List of Vars
   * @return a Var[java.util.List[A]] containing the collected values from vars.
   */
  def collect[T <: Object](vars: JList[Var[T]]): Var[JList[T]] = {
    // we cast to Object and back because we need a ClassTag[T]
    val list = vars.asScala.asInstanceOf[Buffer[Var[Object]]]
    collect(list).map(_.asJava).asInstanceOf[Var[JList[T]]]
  }

  private object create {
    sealed trait State[+T]
    object Idle extends State[Nothing]
    case class Observing[T](n: Int, v: Var[T], c: Closable) extends State[T]
  }

  /**
   * Create a new Var whose values are provided asynchronously by
   * `update`. The returned Var is dormant until it is observed:
   * `update` is called by-need. Such observations are also reference
   * counted so that simultaneous observations do not result in
   * multiple invocations of `update`. When the last observer stops
   * observing, the [[com.twitter.util.Closable]] returned
   * from `update` is closed. Subsequent observations result in a new
   * call to `update`.
   *
   * `empty` is used to fill the returned Var until `update` has
   * provided a value. The first observation of the returned Var is
   * synchronous with the call to `update`--it is guaranteed the the
   * opportunity to fill the Var before the observer sees any value
   * at all.
   *
   * Updates from `update` are ignored after the returned
   * [[com.twitter.util.Closable]] is closed.
   */
  def async[T](empty: T)(update: Updatable[T] => Closable): Var[T] = new Var[T] { self =>
    import create._
    private var state: State[T] = Idle

    private val closable = Closable.make { deadline =>
      self.synchronized {
        state match {
          case Idle =>
            Future.Done
          case Observing(1, _, c) =>
            state = Idle
            // We close the external process asynchronously from the
            // async Var so that it is safe to Await Var.close() in
            // flatMap. (See the TODO there.)
            c.close(deadline)
            Future.Done
          case Observing(n, v, c) =>
            state = Observing(n - 1, v, c)
            Future.Done
        }
      }
    }

    protected def observe(depth: Int, obs: Observer[T]): Closable = {
      val v = self.synchronized {
        state match {
          case Idle =>
            val v = Var(empty)
            val c = update(v)
            state = Observing(1, v, c)
            v
          case Observing(n, v, c) =>
            state = Observing(n + 1, v, c)
            v
        }
      }

      val c = v.observe(depth, obs)
      Closable.sequence(c, closable)
    }
  }
}

private object UpdatableVar {
  import Var.Observer

  final case class Party[T](observer: Observer[T], depth: Int, sequence: Long) {
    @volatile var active: Boolean = true
  }

  val partyOrder: Ordering[Party[_]] = new Ordering[Party[_]] {
    def compare(a: Party[_], b: Party[_]): Int = {
      val c1 = java.lang.Integer.compare(a.depth, b.depth)
      if (c1 != 0) return c1
      java.lang.Long.compare(a.sequence, b.sequence)
    }
  }
}

private[util] class UpdatableVar[T](init: T) extends Var[T] with Updatable[T] with Extractable[T] {
  import UpdatableVar._
  import Var.Observer

  // The state must be mutated or read under a synchronized block on 'this'. The value can be read
  // w/o an external synchronization.
  @volatile private[this] var value = init
  private[this] var version = 0L
  private[this] var partySequence = 0L
  // Exposed for testing.
  //
  // Parties is a sorted set. We implement it ourselves (out top of vanilla list) at the cost
  // of O(n) insert/remove in order to save on memory footprint. Similar to Future's WaitQueue,
  // there just a handful of observers registering to each Var so plain old linked list works
  // the best.
  @volatile private[util] var parties = List.empty[Party[T]]

  def apply(): T = value

  def update(newValue: T): Unit = {
    val v = synchronized {
      version += 1
      value = newValue
      version
    }

    // Another party maybe be racing to register while we're updating with the new value.
    // This is not a big deal b/c observers prevent double-updates by design (via versions).
    parties.foreach { p =>
      // An antecedent update may have closed the current
      // party (e.g. flatMap does this); we need to check that
      // the party is active here in order to prevent stale updates.
      if (p.active) p.observer.publish(this, newValue, v)
    }
  }

  protected def observe(depth: Int, obs: Observer[T]): Closable = {
    obs.claim(this)

    val (p, curValue, curVersion) = synchronized {
      val party = Party(obs, depth, partySequence)
      insertParty(party)
      partySequence += 1
      (party, value, version)
    }

    obs.publish(this, curValue, curVersion)

    new Closable {
      def close(deadline: Time): Future[Unit] = {
        p.active = false
        UpdatableVar.this.synchronized {
          removeParty(p)
        }
        Future.Done
      }
    }
  }

  // Must be called while synchronized on this.
  def removeParty(p: Party[T]): Unit = {
    @tailrec
    def loop(current: List[Party[T]], result: ListBuffer[Party[T]]): List[Party[T]] =
      current match {
        case Nil => result.toList
        case q :: next =>
          if (q eq p) (result ++= next).toList
          else loop(next, result += q)
      }

    parties = parties match {
      case q :: next if q eq p => next
      case ps => loop(ps, ListBuffer.empty)
    }
  }

  // Must be called while synchronized on this.
  def insertParty(p: Party[T]): Unit = {
    @tailrec
    def loop(current: List[Party[T]], result: ListBuffer[Party[T]]): List[Party[T]] =
      current match {
        case Nil => (result += p).toList
        case q :: next =>
          val order = partyOrder.compare(p, q)
          if (order < 0) {
            // p < q, insert here
            result += p
            (result ++= current).toList
          } else if (order > 0) {
            // p > q, the insert position is not found yet
            loop(next, result += q)
          } else {
            // p == q, ignore p and return the original queue
            parties
          }
      }

    parties = parties match {
      case Nil => p :: Nil
      case ps => loop(ps, ListBuffer.empty)
    }
  }

  override def toString: String = "Var(" + value + ")@" + hashCode
}

/**
 * A constant [[Extractable]] [[Var]] on `v`.
 */
class ConstVar[T](v: T) extends Var[T] with Extractable[T] {
  protected def observe(depth: Int, obs: Var.Observer[T]): Closable = {
    obs.claim(this)
    obs.publish(this, v, 0)
    Closable.nop
  }

  def apply(): T = v
}

/**
 * Java adaptation of `Var[T] with Updatable[T] with Extractable[T]`.
 */
class ReadWriteVar[T](init: T) extends UpdatableVar[T](init)
