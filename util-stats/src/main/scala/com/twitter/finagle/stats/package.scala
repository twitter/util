package com.twitter.finagle

import com.twitter.util.{Future, Stopwatch}
import java.util.concurrent.TimeUnit

/**
 * These stats are scoped to `com.twitter.finagle.stats` for historical reasons.
 *
 * They used to be in the `finagle-core` package, although we moved them
 * because we found they didn't depend on anything finagle-specific.  To ease
 * the transition, we kept the namespace.
 */
package object stats {}
