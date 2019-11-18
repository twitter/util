package com.twitter.logging

import scala.annotation.tailrec

/**
 * Typically mixed into `Exceptions` to indicate what [[Level]]
 * they should be logged at.
 *
 * @see Finagle's `com.twitter.finagle.Failure`.
 */
trait HasLogLevel {
  def logLevel: Level
}

object HasLogLevel {

  /**
   * Finds the first [[HasLogLevel]] for the given `Throwable` including
   * its chain of causes and returns its `logLevel`.
   *
   * @note this finds the first [[HasLogLevel]], and should be there
   *       be multiple in the chain of causes, it '''will not use'''
   *       the most severe.
   *
   * @return `None` if neither `t` nor any of its causes are a
   *        [[HasLogLevel]]. Otherwise, returns `Some` of the first
   *        one found.
   */
  @tailrec
  def unapply(t: Throwable): Option[Level] = t match {
    case null => None
    case hll: HasLogLevel => Some(hll.logLevel)
    case _ => unapply(t.getCause)
  }

}
