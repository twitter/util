package com.twitter.util

/**
 * Locals are more flexible thread-locals. They allow for saving &
 * restoring the state of *all* Locals. This is useful for threading
 * Locals through execution contexts. In this manner they are
 * propagated in delayed computations in {@link Promise}.
 */

import collection.mutable.ArrayBuffer

private[util] final object Locals {
  type Context = Array[Option[_]]

  @volatile private[this] var locals: Array[Local[_]] = Array()
  private[this] val saveCache = new ThreadLocal[Context]

  def invalidateCache() {
    saveCache.remove()
  }

  def +=(local: Local[_]) = synchronized {
    val newLocals = new Array[Local[_]](locals.size + 1)
    locals.copyToArray(newLocals)
    newLocals(newLocals.size - 1) = local
    locals = newLocals
  }

  private[this] val emptyCtx: Context = Array.empty

  def save(): Context = {
    val cached = saveCache.get
    if (cached != null)
      return cached

    if (locals.isEmpty)
      return emptyCtx

    val locals0 = locals
    val saved = new Context(locals0.size)
    var i = 0: Int
    while (i < locals0.size) {
      saved(i) = locals0(i)()
      i += 1
    }

    saveCache.set(saved)
    saved
  }

  def restore(saved: Context) {
    val locals0 = locals
    var i = 0
    while (i < saved.size) {
      locals0(i).setUnsafe(saved(i))
      i += 1
    }

    while (i < locals0.size) {
      locals0(i).clear()
      i += 1
    }

    saveCache.set(saved)
  }
}

final class Local[T] {
  private[this] val threadLocal =
    new ThreadLocal[Option[T]] {
      override def initialValue: Option[T] = None
    }

  // Note the order here is important.  This must come last as
  // ``this'' must be fully initialized before it's registered.
  Locals += this

  def update(value: T) = set(Some(value))
  def set(optValue: Option[T]) = {
    Locals.invalidateCache()
    threadLocal.set(optValue)
  }
  def clear() = {
    Locals.invalidateCache()
    threadLocal.remove()
  }
  def apply() = threadLocal.get()

  private[util] def setUnsafe(optValue: Option[_]) {
    set(optValue.asInstanceOf[Option[T]])
  }
}
