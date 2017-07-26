package com.twitter.util

/** Denotes an updatable container. */
trait Updatable[T] {

  /** Update the container with value `t` */
  def update(t: T): Unit
}

object Updatable {

  /**
   * Construct an Updatable that discards the update.
   */
  def empty[A](): Updatable[A] = new Updatable[A] {
    def update(result: A) = ()
  }
}
