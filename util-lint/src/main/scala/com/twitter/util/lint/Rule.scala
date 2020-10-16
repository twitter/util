package com.twitter.util.lint

import java.util.regex.Pattern

/**
 * A single lint rule, that when [[Rule.apply run]] evaluates
 * whether or not there are any issues.
 */
trait Rule {

  /**
   * Runs this lint check.
   *
   * @return An empty `Seq` if no issues are found.
   */
  def apply(): Seq[Issue]

  /** The broad category that this rule belongs in. */
  def category: Category

  /**
   * A '''short''' name for this rule intended to be used for
   * generating a machine readable [[id]].
   */
  def name: String

  /**
   * Produce a "machine readable" id from [[name]].
   */
  def id: String =
    Rule.WhitespacePattern.matcher(name.toLowerCase.trim).replaceAll("-")

  /** A description of the issue and what problems it may cause. */
  def description: String
}

object Rule {

  /**
   * Factory method for creating a [[Rule]].
   *
   * @param fn is evaluated to determine if there are any issues.
   */
  def apply(category: Category, shortName: String, desc: String)(fn: => Seq[Issue]): Rule = {
    val _cat = category
    new Rule {
      def apply(): Seq[Issue] = fn
      def name: String = shortName
      def category: Category = _cat
      def description: String = desc
    }
  }

  private[Rule] val WhitespacePattern =
    Pattern.compile("\\s")

}
