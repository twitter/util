package com.twitter.util.lint

/**
 * Used to indicate the broad category a lint [[Rule]] belongs to.
 */
sealed trait Category

object Category {

  /**
   * Indicative of a possible performance issue.
   */
  case object Performance extends Category

  /**
   * Indicative of a possible configuration issue.
   */
  case object Configuration extends Category

  /**
   * Indicative of a runtime failure.
   */
  case object Runtime extends Category
}
