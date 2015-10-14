package com.twitter.util.lint

/**
 * The result of a lint [[Rule.apply rule]] that found an issue.
 *
 * @param details should ideally explain the specifics of what
 *   is wrong and what can be done to remediate the issue.
 */
case class Issue(details: String)
