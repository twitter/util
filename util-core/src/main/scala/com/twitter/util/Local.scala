package com.twitter.util

/**
 * Locals are more flexible thread-locals. They allow for saving &
 * restoring the state of *all* Locals. This is useful for threading
 * Locals through execution contexts. In this manner they are
 * propagated in delayed computations in {@link Promise}.
 */

import collection.mutable.ArrayBuffer

object Locals {
  private[this] val locals = new ArrayBuffer[Local[_]]

  def +=(local: Local[_]) = synchronized {
    locals += local
  }

  def save() = synchronized {
    val saved = new SavedLocals
    locals.foreach { saved += _.save() }
    saved
  }
}

class Local[T] {
  Locals += this

  protected[this] val threadLocal =
    new ThreadLocal[Option[T]] {
      override def initialValue: Option[T] = None
    }

  def update(value: T) = threadLocal.set(Some(value))
  def clear() = threadLocal.remove()
  def apply() = threadLocal.get()
  def save() = new SavedLocal[T](this)
}

class SavedLocal[T](local: Local[T]) {
  private[this] val savedValue = local()

  def restore() {
    savedValue match {
      case Some(value) => local() = value
      case None        => local.clear()
    }
  }
}

class SavedLocals extends ArrayBuffer[SavedLocal[_]] {
  def restore() = foreach { _.restore() }
}
