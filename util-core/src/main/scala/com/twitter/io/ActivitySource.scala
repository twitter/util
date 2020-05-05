package com.twitter.io

import com.twitter.util._

/**
 * An ActivitySource provides access to observerable named variables.
 */
trait ActivitySource[+T] {

  /**
   * Returns an [[com.twitter.util.Activity]] for a named T-typed variable.
   */
  def get(name: String): Activity[T]

  /**
   * Produces an ActivitySource which queries this ActivitySource, falling back to
   * a secondary ActivitySource only when the primary result is ActivitySource.Failed.
   *
   * @param that the secondary ActivitySource
   */
  def orElse[U >: T](that: ActivitySource[U]): ActivitySource[U] =
    new ActivitySource.OrElse(this, that)
}

object ActivitySource {

  /**
   * A Singleton exception to indicate that an ActivitySource failed to find
   * a named variable.
   */
  object NotFound extends IllegalStateException

  /**
   * An ActivitySource for observing file contents. Once observed,
   * each file will be polled once per period.
   */
  def forFiles(
    period: Duration = Duration.fromSeconds(60)
  )(
    implicit timer: Timer
  ): ActivitySource[Buf] =
    new CachingActivitySource(new FilePollingActivitySource(period)(timer))

  /**
   * Create an ActivitySource for ClassLoader resources.
   */
  def forClassLoaderResources(
    cl: ClassLoader = ClassLoader.getSystemClassLoader
  ): ActivitySource[Buf] =
    new CachingActivitySource(new ClassLoaderActivitySource(cl))

  private[ActivitySource] class OrElse[T, U >: T](
    primary: ActivitySource[T],
    failover: ActivitySource[U])
      extends ActivitySource[U] {
    def get(name: String): Activity[U] = {
      primary.get(name) transform {
        case Activity.Failed(_) => failover.get(name)
        case state => Activity(Var.value(state))
      }
    }
  }
}
