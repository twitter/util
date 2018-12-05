package com.twitter.concurrent

import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec

import com.twitter.util.{Future, Promise, Return}

object SpoolSource {
  private object DefaultInterruptHandler extends PartialFunction[Any, Nothing] {
    def isDefinedAt(x: Any) = false
    def apply(x: Any) = throw new MatchError(x)
  }
}

/**
 * A SpoolSource is a simple object for creating and populating a Spool-chain.  apply()
 * returns a Future[Spool] that is populated by calls to offer().  This class is thread-safe.
 * @param interruptHandler attached to every Promise in the produced Spool.
 */
class SpoolSource[A](interruptHandler: PartialFunction[Throwable, Unit]) {
  def this() = this(SpoolSource.DefaultInterruptHandler)

  private val closedp = new Promise[Unit]

  // a reference to the current outstanding promise for the next Future[Spool[A]] result
  private val promiseRef = new AtomicReference[Promise[Spool[A]]]

  // when the SpoolSource is closed, promiseRef will be permanently set to emptyPromise,
  // which always returns an empty spool.
  private val emptyPromise = new Promise(Return(Spool.empty[A]))

  // set the first promise to be fulfilled by the first call to offer()
  promiseRef.set({
    val p = new Promise[Spool[A]]
    p.setInterruptHandler(interruptHandler)
    p
  })

  /**
   * Gets the current outstanding Future for the next Spool value.  The returned Spool
   * will see all future values passed to offer(), up until close() is called.
   * Previous values passed to offer() will not be seen in the Spool.
   */
  def apply(): Future[Spool[A]] = promiseRef.get

  /**
   * Puts a value into the spool.  Unless this SpoolSource has been closed, the current
   * Future[Spool[A]] value will be fulfilled with a Spool that contains the
   * provided value.  If the SpoolSource has been closed, then this value is ignored.
   * If multiple threads call `offer` simultaneously, the operation is thread-safe but
   * the resulting order of values in the spool is non-deterministic.
   */
  final def offer(value: A): Unit = {

    val nextPromise = new Promise[Spool[A]]
    nextPromise.setInterruptHandler(interruptHandler)
    updatingTailCall(nextPromise) { currentPromise =>
      currentPromise.setValue(value *:: nextPromise)
    }
  }

  /**
   * Puts a value into the spool and closes this SpoolSource.  Unless
   * this SpoolSource has been closed, the current Future[Spool[A]]
   * value will be fulfilled with `value *:: Future.value(Spool.empty[A])`.
   * If the SpoolSource has been closed, then this value is ignored.
   * If multiple threads call offer simultaneously, the operation is
   * thread-safe but the resulting order of values in the spool is
   * non-deterministic.
   */
  final def offerAndClose(value: A): Unit = {

    updatingTailCall(emptyPromise) { currentPromise =>
      currentPromise.setValue(value *:: Future.value(Spool.empty[A]))
      closedp.setDone()
    }
  }

  /**
   * Closes this SpoolSource, which also terminates the generated Spool.  This method
   * is idempotent.
   */
  final def close(): Unit = {
    updatingTailCall(emptyPromise) { currentPromise =>
      currentPromise.setValue(Spool.empty[A])
      closedp.setDone()
    }
  }

  /**
   * Fulfilled when this SpoolSource has been closed or an exception
   * is raised.
   */
  val closed: Future[Unit] = closedp

  /**
   * Raises exception on this SpoolSource, which also terminates the generated Spool.  This method
   * is idempotent.
   */
  final def raise(e: Throwable): Unit = {
    updatingTailCall(emptyPromise) { currentPromise =>
      currentPromise.setException(e)
      closedp.setException(e)
    }
  }

  @tailrec
  private[this] def updatingTailCall(
    newPromise: Promise[Spool[A]]
  )(f: Promise[Spool[A]] => Unit
  ): Unit = {
    val currentPromise = promiseRef.get
    // if the current promise is emptyPromise, then this source has already been closed
    if (currentPromise ne emptyPromise) {
      if (promiseRef.compareAndSet(currentPromise, newPromise)) {
        f(currentPromise)
      } else {
        // try again
        updatingTailCall(newPromise)(f)
      }
    }
  }
}
