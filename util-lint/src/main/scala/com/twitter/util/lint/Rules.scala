package com.twitter.util.lint

import scala.collection.immutable.{Iterable, List}

/**
 * A collection of [[Rule rules]].
 *
 * Implementations must be thread-safe for clients to use.
 *
 * Most usage will be via the implementation provided
 * by [[GlobalRules.get]].
 */
trait Rules {
  /**
   * Return all rules [[add added]].
   *
   * No guarantees are given with regards to the ordering of the
   * returned rules.
   */
  def iterable: Iterable[Rule]

  /**
   * Add the given rule.
   *
   * Duplicates are allowed.
   */
  def add(rule: Rule): Unit
}

class RulesImpl extends Rules {

  // thread-safety via synchronization on `this`
  private[this] var rules = List.empty[Rule]

  def iterable: Iterable[Rule] = synchronized {
    rules
  }

  def add(rule: Rule): Unit = synchronized {
    rules = rule :: rules
  }

}

object GlobalRules {

  private[this] val rules = new RulesImpl()

  /**
   * Gets the global [[Rules]] implementation.
   */
  def get: Rules = rules

}
