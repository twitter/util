package com.twitter.concurrent

/**
 * An [[AsyncMutex]] is a traditional mutex but with asynchronous
 * execution.
 *
 * Basic usage:
 * {{{
 *   val mutex = new AsyncMutex()
 *   ...
 *   mutex.acquireAndRun() {
 *     somethingThatReturnsFutureT()
 *   }
 * }}}
 *
 * @see [[AsyncSemaphore]] for a semaphore version.
 */
class AsyncMutex private (maxWaiters: Option[Int])
  extends AsyncSemaphore(1, maxWaiters)
{
  /**
   * Constructs a mutex with no limit on the max number
   * of waiters for permits.
   */
  def this() = this(None)

  /**
   * Constructs a mutex with `maxWaiters` as the limit on the
   * number of waiters for permits.
   */
  def this(maxWaiters: Int) = this(Some(maxWaiters))
}
