package com.twitter.util

/**
 * Locals are more flexible thread-locals. They allow for saving &
 * restoring the state of *all* Locals. This is useful for threading
 * Locals through execution contexts. In this manner they are
 * propagated in delayed computations in {@link Promise}.
 */

import collection.mutable.ArrayBuffer

private[util] final object Locals {
  @volatile private[this] var locals: Array[Local[_]] = Array()

  def +=(local: Local[_]) = synchronized {
    val newLocals = new Array[Local[_]](locals.size + 1)
    locals.copyToArray(newLocals)
    newLocals(newLocals.size - 1) = local
    locals = newLocals
  }

  private[this] val emptyLocals = new SavedLocals(Array.empty)

  def save(): SavedLocals =
    if (locals.isEmpty) {
      emptyLocals
    } else {
      val locals0 = locals
      val saved = new Array[SavedLocal[_]](locals0.size)
      var i = 0: Int
      while (i < locals0.size) {
        saved(i) = locals0(i).save
        i += 1
      }

      new SavedLocals(saved)
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

  def update(value: T) = threadLocal.set(Some(value))
  def set(optValue: Option[T]) = threadLocal.set(optValue)
  def clear() = threadLocal.remove()
  def apply() = threadLocal.get()
  def save() = new SavedLocal[T](this)
}

private[util] class SavedLocal[T](local: Local[T]) {
  private[this] val savedValue = local()

  def restore() {
    savedValue match {
      case Some(value) => local() = value
      case None        => local.clear()
    }
  }
}

private[util] final class SavedLocals(locals: Array[SavedLocal[_]]) {
  def restore(): Unit = {
    var i = 0: Int
    while (i < locals.size) {
      locals(i).restore()
      i += 1
    }
  }
}
