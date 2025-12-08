package com.twitter.util

import scala.util.control.NonFatal

/**
 * Successful Future that contains a value. Transformations are executed immediately (don't go
 * through the scheduler). Unlike `Future.const`, Future recursion *will* grow the stack (see
 * `ImmediateValueFutureTest` for an example of this) -- it is therefore extremely important that
 * you understand the full context of how this Future will be used in order to avoid this.
 *
 * DO NOT USE THIS without thoroughly understanding the risks!
 */
private[twitter] class ImmediateValueFuture[A](result: A) extends Future[A] {

  private[this] val ReturnResult = Return(result)

  def respond(f: Try[A] => Unit): Future[A] = {
    val saved = Local.save()
    try {
      f(ReturnResult)
    } catch Monitor.catcher
    finally {
      Local.restore(saved)
    }
    this
  }

  override def proxyTo[B >: A](other: Promise[B]): Unit = {
    other.update(ReturnResult)
  }

  def raise(interrupt: Throwable): Unit = ()

  override def rescue[B >: A](rescueException: PartialFunction[Throwable, Future[B]]): Future[B] = {
    this
  }

  protected def transformTry[B](f: Try[A] => Try[B]): Future[B] = {
    val saved = Local.save()
    try {
      f(ReturnResult) match {
        case Return(result) => new ImmediateValueFuture(result)
        case t @ Throw(_) => Future.const(t)
      }
    } catch {
      case NonFatal(e) => Future.const(Throw(e))
    } finally {
      Local.restore(saved)
    }
  }

  def transform[B](f: Try[A] => Future[B]): Future[B] = {
    val saved = Local.save()
    try {
      f(ReturnResult)
    } catch {
      case NonFatal(e) => Future.const(Throw(e))
    } finally {
      Local.restore(saved)
    }
  }

  def poll: Option[Try[A]] = Some(ReturnResult)

  override def toString: String = s"ImmediateValueFuture($result)"

  def ready(timeout: Duration)(implicit permit: Awaitable.CanAwait): this.type = this

  def result(timeout: Duration)(implicit permit: Awaitable.CanAwait): A = result

  def isReady(implicit permit: Awaitable.CanAwait): Boolean = true
}
