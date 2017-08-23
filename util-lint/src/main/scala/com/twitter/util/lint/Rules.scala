package com.twitter.util.lint

import com.twitter.util.Local
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

  /**
   * Removes all rules matching the provided id.
   */
  def removeById(ruleId: String): Unit
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

  def removeById(ruleId: String): Unit = synchronized {
    rules = rules.filterNot(_.id == ruleId)
  }

}

object GlobalRules {
  private[this] val rules = new RulesImpl()

  /**
   * Gets the global [[Rules]] implementation.
   *
   * If it's call inside of a `withRules` context then it's a temporary
   * rule set, useful for writing isolated tests.
   */
  def get: Rules = localRules() match {
    case None => rules
    case Some(local) => local
  }

  /**
   * Note, this should only ever be updated by methods used for testing.
   */
  private[this] val localRules = new Local[Rules]

  /**
   * Changes the global rule set to instead return a local one.
   *
   * Takes the rules context with it when moved to a different thread via
   * Twitter concurrency primitives, like `flatMap` on a
   * [[com.twitter.util.Future]].
   */
  def withRules[A](replacement: Rules)(fn: => A): A = localRules.let(replacement) {
    fn
  }
}
