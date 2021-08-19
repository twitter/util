package com.twitter.finagle.stats

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import scala.collection.compat._
import scala.util.matching.Regex

/**
 * Caches the results of evaluating a given regex
 *
 * Caches whether the regex matches each of a list of strings.  When a map is
 * passed to the CachedRegex, it will strip out all of the strings that match
 * the regex.
 */
private[stats] class CachedRegex(regex: Regex)
    extends (collection.Map[String, Number] => collection.Map[String, Number]) {
  // public for testing
  // a mapping from a metric key to whether the regex matches it or not
  // we allow this to race as long as we're confident that the result is correct
  // since a given key will always compute the same value, this is safe to race
  val regexMatchCache = new ConcurrentHashMap[String, java.lang.Boolean]

  private[this] val filterFn: String => Boolean = { key =>
    // we unfortunately need to rely on escape analysis to shave this allocation
    // because otherwise java.util.Map#get that returns a null for a plausibly
    // primitive type in scala gets casted to a null
    //
    // the alternative is to first check containsKey and then do a subsequent
    // lookup, but that risks an actually potentially bad race condition
    // (a potential false negative)
    val cached = regexMatchCache.get(key)
    val matched = if (cached ne null) {
      Boolean.unbox(cached)
    } else {
      val result = regex.pattern.matcher(key).matches()
      // adds new entries
      regexMatchCache.put(key, result)
      result
    }
    !matched
  }

  def apply(samples: collection.Map[String, Number]): collection.Map[String, Number] = {
    // don't really want this to be executed in parallel
    regexMatchCache.forEachKey(
      Long.MaxValue,
      new Consumer[String] {
        def accept(key: String): Unit = {
          if (!samples.contains(key)) {
            regexMatchCache.remove(key)
          }
        }
      })
    samples.view.filterKeys(filterFn).toMap
  }
}
