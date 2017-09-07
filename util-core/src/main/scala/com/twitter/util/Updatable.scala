package com.twitter.util

/** Denotes an updatable container. */
trait Updatable[-T] {

  /** Update the container with value `t` */
  def update(t: T): Unit
}

object Updatable {

  /**
   * Singleton instance of an Updatable which discards any updates.
   */
  val Empty: Updatable[Any] = new Updatable[Any] {
    def update(result: Any): Unit = ()
  }

  /**
   * Returns an Updatable that discards any updates.
   */
  def empty[A](): Updatable[A] = Empty
}
