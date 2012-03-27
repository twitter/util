package com.twitter.util

/**
 * Locals are more flexible thread-locals. They allow for saving &
 * restoring the state of *all* Locals. This is useful for threading
 * Locals through execution contexts. In this manner they are
 * propagated in delayed computations in {@link Promise}.
 *
 * Note: the implementation is optimized for situations in which
 * save and restore optimizations are dominant.
 */

import collection.mutable.ArrayBuffer
import java.util.Arrays

private[util] final object Local {
  type Context = Array[Option[_]]
  private[this] val localCtx = new ThreadLocal[Context]
  @volatile private[this] var size: Int = 0

  def save(): Context = localCtx.get
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

final class Local[T] {
  private[this] val me = Local.add()

  def update(value: T) { set(Some(value)) }
  def set(optValue: Option[T]) { Local.set(me, optValue) }
  def clear() { Local.clear(me) }
  def apply() = Local.get(me).asInstanceOf[Option[T]]
}
