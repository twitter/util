package com.twitter.util

final object Local {
  type Context = Array[Option[_]]
  private[this] val localCtx = new ThreadLocal[Context]
  @volatile private[this] var size: Int = 0

  /**
   * Return a snapshot of the current Local state.
   */
  def save(): Context = localCtx.get

  /**
   * Restore the Local state to a given Context of values.
   */
  def restore(saved: Context): Unit = localCtx.set(saved)

  private def add() = synchronized {
    size += 1
    size-1
  }

  private def set(i: Int, v: Option[_]) {
    assert(i < size)
    var ctx = localCtx.get

    if (ctx == null)
      ctx = new Array[Option[_]](size)
    else {
      val oldCtx = ctx
      ctx = new Array[Option[_]](size)
      System.arraycopy(oldCtx, 0, ctx, 0, oldCtx.size)
    }

    ctx(i) = v
    localCtx.set(ctx)
  }

  private def get(i: Int): Option[_] = {
    val ctx = localCtx.get
    if (ctx == null || ctx.size <= i)
      return None

    val v = ctx(i)
    if (v == null) None else v
  }

  private def clear(i: Int) {
    set(i, None)
  }
}

/**
 * Locals are more flexible thread-locals. They allow for saving and
 * restoring the state of *all* Locals. This is useful for threading
 * Locals through execution contexts. In this manner they are
 * propagated in delayed computations in [[com.twitter.util.Promise]].
 *
 * Note: the implementation is optimized for situations in which
 * save and restore optimizations are dominant.
 */
final class Local[T] {
  private[this] val me = Local.add()

  /**
   * Update the Local with a given value.
   */
  def update(value: T) { set(Some(value)) }

  /**
   * Update the Local with a given optional value.
   */
  def set(optValue: Option[T]) { Local.set(me, optValue) }

  /**
   * Get the Local's optional value.
   */
  def apply(): Option[T] = Local.get(me).asInstanceOf[Option[T]]

  /**
   * Execute a block with a specific Local value, restoring the current state
   * upon completion.
   */
  def let[U](value: T)(f: => U): U = {
    val saved = apply()
    set(Some(value))
    try f finally set(saved)
  }

  /**
   * Execute a block with the Local cleared, restoring the current state upon
   * completion.
   */
  def letClear[U](f: => U): U = {
    val saved = apply()
    clear()
    try f finally set(saved)
  }

  /**
   * Clear the Local's value. Other `Local`s are not modified.
   */
  def clear() { Local.clear(me) }
}
