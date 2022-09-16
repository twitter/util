package com.twitter.util

import scala.runtime.NonLocalReturnControl

/**
 * A `Future` that is already completed.
 *
 * These are cheap in construction compared to `Promises`.
 */
class ConstFuture[A](result: Try[A]) extends Future[A] {

  // It is not immediately obvious why `ConstFuture` uses the `Scheduler`
  // instead of executing the `k` immediately and inline.
  // The first is that this allows us to unwind the stack and thus do Future
  // "recursion". See
  // https://twitter.github.io/util/guide/util-cookbook/futures.html#future-recursion
  // for details. The second is that this keeps the execution order consistent
  // with `Promise`.
  def respond(f: Try[A] => Unit): Future[A] = {
    val saved = Local.save()
    saved.fiber.submitTask(() => {
      val current = Local.save()
      if (current ne saved) Local.restore(saved)

      val tracker = saved.resourceTracker
      val run =
        if (tracker eq None) f
        else ResourceTracker.wrapAndMeasureUsage(f, tracker.get)

      try run(result)
      catch Monitor.catcher
      finally Local.restore(current)
    })
    this
  }

  override def proxyTo[B >: A](other: Promise[B]): Unit = {
    // avoid an extra call to `isDefined` as `update` checks
    other.update(result)
  }

  def raise(interrupt: Throwable): Unit = ()

  // NOTE: since `f` is not run by the scheduler, the used cpu time will be tracked by
  // the continuation that executes this transformation
  protected def transformTry[B](f: Try[A] => Try[B]): Future[B] = {
    try Future.const(f(result))
    catch {
      case scala.util.control.NonFatal(e) => Future.const(Throw(e))
    }
  }

  def transform[B](f: Try[A] => Future[B]): Future[B] = {
    val p = new Promise[B]
    // see the note on `respond` for an explanation of why `Scheduler` is used.
    val saved = Local.save()
    saved.fiber.submitTask(() => {
      val current = Local.save()
      if (current ne saved) Local.restore(saved)

      val tracker = saved.resourceTracker
      val run =
        if (tracker eq None) f
        else ResourceTracker.wrapAndMeasureUsage(f, tracker.get)

      val computed =
        try run(result)
        catch {
          case e: NonLocalReturnControl[_] => Future.exception(new FutureNonLocalReturnControl(e))
          case scala.util.control.NonFatal(e) => Future.exception(e)
          case t: Throwable =>
            Monitor.handle(t)
            throw t
        } finally Local.restore(current)

      p.become(computed)
    })
    p
  }

  def poll: Option[Try[A]] = Some(result)

  override def isDefined: Boolean = true

  override def toString: String = s"ConstFuture($result)"

  // Awaitable
  @throws(classOf[TimeoutException])
  @throws(classOf[InterruptedException])
  def ready(timeout: Duration)(implicit permit: Awaitable.CanAwait): this.type = this

  @throws(classOf[Exception])
  def result(timeout: Duration)(implicit permit: Awaitable.CanAwait): A = result()

  def isReady(implicit permit: Awaitable.CanAwait): Boolean = true
}
