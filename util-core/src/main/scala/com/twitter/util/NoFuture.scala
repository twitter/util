package com.twitter.util

/**
 * A [[Future]] which can never be satisfied and is thus always in
 * the pending state.
 *
 * @see [[Future.never]] for an instance of it.
 */
class NoFuture extends Future[Nothing] {
  def respond(k: Try[Nothing] => Unit): Future[Nothing] = this
  def transform[B](f: Try[Nothing] => Future[B]): Future[B] = this
  protected def transformTry[B](f: Try[Nothing] => Try[B]): Future[B] = this

  def raise(interrupt: Throwable): Unit = ()

  // Awaitable
  private[this] def sleepThenTimeout(timeout: Duration): TimeoutException = {
    Thread.sleep(timeout.inMilliseconds)
    new TimeoutException(timeout.toString)
  }

  @throws(classOf[TimeoutException])
  @throws(classOf[InterruptedException])
  def ready(timeout: Duration)(implicit permit: Awaitable.CanAwait): this.type = {
    throw sleepThenTimeout(timeout)
  }

  @throws(classOf[Exception])
  def result(timeout: Duration)(implicit permit: Awaitable.CanAwait): Nothing = {
    throw sleepThenTimeout(timeout)
  }

  def poll: Option[Try[Nothing]] = None

  def isReady(implicit permit: Awaitable.CanAwait): Boolean = false
}
