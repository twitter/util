package com.twitter.finagle.util

import com.twitter.app

/**
 * A deny list of implementations to ignore. Keys are the fully qualified class names.
 * Any other implementations that are found via `LoadService.apply` are eligible to be used.
 *
 * As an example, here's how to filter out `OstrichStatsReceiver`, `OstrichExporter` and
 * `CommonsStatsReceiver` using a global flag:
 *
 * {{{
 * -Dcom.twitter.finagle.util.loadServiceDenied=com.twitter.finagle.stats.OstrichStatsReceiver,com.twitter.finagle.stats.OstrichExporter,com.twitter.finagle.stats.CommonsStatsReceiver
 * }}}
 *
 * We need to pass the arguments as Java property values instead of as Java application
 * arguments (regular TwitterServer flags) because [[app.LoadService]] may be loaded before
 * application arguments are parsed.
 *
 * @note this lives in `com.twitter.finagle.util` for historical reasons as this
 *       flag began its life in Finagle and in order to keep backwards compatibility
 *       it remains in that package.
 */
object loadServiceDenied extends app.GlobalFlag[Set[String]](
  Set.empty[String],
  "A deny list of implementations to ignore. Keys are the fully qualified class names. " +
    "Any other implementations that are found via `LoadService.apply` are eligible to be used.")

/**
 * A set of paths for [[com.twitter.app.LoadService]] to ignore
 * while scanning. May be useful for performance or problematic
 * paths.
 *
 * Defaults to an empty `Set`.
 *
 * @note this lives in `com.twitter.finagle.util` for historical reasons as this
 *       flag began its life in Finagle and in order to keep backwards compatibility
 *       it remains in that package.
 */
object loadServiceIgnoredPaths extends app.GlobalFlag[Seq[String]](
  Seq.empty[String],
  "Additional packages to be excluded from recursive directory scan")

