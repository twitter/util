package com.twitter.util

import java.util.concurrent.atomic.AtomicReference
import scala.collection.generic.CanBuildFrom
import scala.collection.immutable

/**
 * Trait Var represents a variable. It is a reference cell which is
 * composable: dependent Vars (derived through flatMap) are
 * recomputed automatically when independent variables change -- they
 * implement a form of self-adjusting computation.
 *
 * Vars may also be observed, notifying users whenever the variable
 * changes.
 *
 * @note Vars do not always perform the minimum amount of
 * re-computation.
 *
 * @note There are no well-defined error semantics for Var. Vars
 * are computed lazily, and the updating thread will receive any
 * exceptions thrown while computing derived Vars.
 */
trait Var[+T] { self =>
  /**
   * Extract the current value of the Var
   * Calling this method is discouraged outside of testing.  A more idiomatically correct
   * technique is to call `observe` to both retrieve the current value, and react to
   * future changes.
   */
  def apply(): T = {
    var opt: Option[T] = None
    observe(v => opt = Some(v)).close()
    opt.get
  }

  /** 
   * Observe this Var. `f` is invoked each time the variable changes.
   */
  final def observe(f: T => Unit): Closable = observe(0, f)

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
  protected def observe(depth: Int, f: T => Unit): Closable

  /** Synonymous with observe */
  def foreach(f: T => Unit) = observe(f)

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

    override def apply(): U = f(self())()

    def observe(depth: Int, o: U => Unit) = {
      val inner = new AtomicReference(Closable.nop)
      val outer = self.observe(depth, { t =>
        inner.getAndSet(f(t).observe(depth+1, o)).close()
      })

      Closable.sequence(
        outer,
        Closable.make { deadline =>
          inner.getAndSet(Closable.nop).close(deadline)
        }
      )
    }
  }

  /**
   * Observe this Var into the given AtomicReference.
   * Observation stops when the returned closable is closed.
   */
  def observeTo[U >: T](ref: AtomicReference[U]): Closable =
    this observe { newv => ref.set(newv) }

  /**
   * A one-shot predicate observation. The returned future
   * is satisfied with the first observed value of Var that obtains
   * the predicate `pred`. Observation stops when the future is 
   * satisfied.
   *
   * Interrupting the future will also satisfy the future (with the
   * interrupt exception) and close the observation.
   */
  def observeUntil(pred: T => Boolean): Future[T] = {
    val p = Promise[T]()
    p.setInterruptHandler {
      case exc => p.updateIfEmpty(Throw(exc))
    }
    
    val o = observe { 
      case el if pred(el) => p.updateIfEmpty(Return(el))
      case _ => 
    }

    p ensure {
      o.close()
    }
  }
}

object Var {
  /**
   * Create a new, updatable Var with an initial value. We call
   * such Vars independent -- derived Vars being dependent
   * on these.
   */
  def apply[T](init: T): Var[T] with Updatable[T] = new UpdatableVar[T] {
    value = init
  }

  /**
   * Create a new, constant, v-valued Var.
   */
  def value[T](v: T): Var[T] = new Var[T] {
    override def apply(): T = v
    protected def observe(depth: Int, f: T => Unit): Closable = {
      f(v)
      Closable.nop
    }    
  }

  def unapply[T](v: Var[T]): Option[T] = Some(v())

  /** 
   * Collect a collection of Vars into a Var of collection.
   */
  def collect[T, CC[X] <: Traversable[X]](vars: CC[Var[T]])
      (implicit newBuilder: CanBuildFrom[CC[T], T, CC[T]], cm: ClassManifest[T])
      : Var[CC[T]] = async(newBuilder().result) { v =>
    val N = vars.size
    val cur = new Array[T](N)
    var filling = true
    def build() = {
      val b = newBuilder()
      b ++= cur
      b.result()
    }

    def publish(i: Int, newi: T) = synchronized {
      cur(i) = newi
      if (!filling) v() = build()
    }

    val closes = new Array[Closable](N)
    var i = 0
    for (u <- vars) {
      val j = i
      closes(j) = u observe { newj => publish(j, newj) }
      i += 1
    }

    synchronized {
      filling = false
      v() = build()
    }

    Closable.all(closes:_*)
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
   * counted so that simultaneous observervations do not result in
   * multiple invocations of `update`. When the last observer stops
   * observing, the [[com.twitter.util.Closable Closable]] returned
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
   * [[com.twitter.util.Closable Closable]] is closed.
   */
  def async[T](empty: T)(update: Updatable[T] => Closable): Var[T] = new Var[T] {
    import create._
    private var state: State[T] = Idle
    
    private val closable = Closable.make { deadline =>
      synchronized {
        state match {
          case Idle =>
            Future.Done
          case Observing(1, _, c) =>
            state = Idle
            c.close(deadline)
          case Observing(n, v, c) =>
            state = Observing(n-1, v, c)
            Future.Done
        }
      }
    }

    protected def observe(depth: Int, f: T => Unit): Closable = {
      val v = synchronized {
        state match {
          case Idle =>
            val v = Var(empty)
            val c = update(v)
            state = Observing(1, v, c)
            v
          case Observing(n, v, c) =>
            state = Observing(n+1, v, c)
            v
        }
      }

      val c = v.observe(depth, f)
      Closable.sequence(c, closable)
    }
  }
}

/** Denotes an updatable container. */
trait Updatable[T] {
  /** Update the container with value `t` */
  def update(t: T)
}

private object UpdatableVar {
  case class Observer[T](
      depth: Int, 
      k: T => Unit, 
      version: Long
  ) extends (T => Unit) {
    def apply(t: T) = k(t)
  }

  implicit def observerOrdering[T] = new Ordering[Observer[T]] {
    // This is safe because observers are compared
    // only from the same source of versions.
    def compare(a: Observer[T], b: Observer[T]): Int = {
      val c1 = a.depth compare b.depth
      if (c1 != 0) return c1
      a.version compare b.version
    }
  }
}

private trait UpdatableVar[T] extends Var[T] with Updatable[T] {
  import UpdatableVar._
  
  private[this] var version = 0L
  @volatile protected var value: T = _
  @volatile private[this] var observers =
    immutable.SortedSet.empty[Observer[T]]

  override def apply(): T = value

  def update(t: T) {
    val obs = synchronized {
      if (value == t) return
      value = t
      observers
    }

    for (o <- obs if observers contains o)
      o(t)
  }

  protected def observe(depth: Int, k: T => Unit): Closable = {
    val (o, v) = synchronized {
      val o = Observer(depth, k, version)
      version += 1
      observers += o
      (o, value)
    }

    o(v)

    Closable.make { deadline =>
      synchronized {
        observers -= o
      }
      Future.Done
    }
  }
  
  override def toString = "Var("+value+")"
}
