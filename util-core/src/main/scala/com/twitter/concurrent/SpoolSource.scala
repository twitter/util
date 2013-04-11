package com.twitter.concurrent

import com.twitter.util.{Future, Promise, Return}
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/**
 * A SpoolSource is a simple object for creating and populating a Spool-chain.  apply()
 * returns a Future[Spool] that is populated by calls to offer().  This class is thread-safe.
 */
class SpoolSource[A] {
  // a reference to the current outstanding promise for the next Future[Spool[A]] result
  private val promiseRef = new AtomicReference[Promise[Spool[A]]]

  // when the SpoolSource is closed, promiseRef will be permanently set to emptyPromise,
  // which always returns an empty spool.
  private val emptyPromise = new Promise(Return(Spool.empty[A]))

  // set the first promise to be fulfilled by the first call to offer()
  promiseRef.set(new Promise[Spool[A]])

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
   * If multiple threads call offer simultaneously, the operation is thread-safe but
   * the resulting order of values in the spool is non-deterministic.
   */
  final def offer(value: A) {
    val nextPromise = new Promise[Spool[A]]
    updatingTailCall(nextPromise) { currentPromise =>
      currentPromise.setValue(Spool.cons(value, nextPromise))
    }
  }

  /**
   * Closes this SpoolSource, which also terminates the generated Spool.  This method
   * is idempotent.
   */
  final def close() {
    updatingTailCall(emptyPromise) { currentPromise =>
      currentPromise.setValue(Spool.empty[A])
    }
  }

  /**
   * Raises exception on this SpoolSource, which also terminates the generated Spool.  This method
   * is idempotent.
   */
  final def raise(e: Throwable) {
    updatingTailCall(emptyPromise) { currentPromise =>
      currentPromise.setException(e)
    }
  }

  @tailrec
  private[this] def updatingTailCall(newPromise: Promise[Spool[A]])(f: Promise[Spool[A]] => Unit) {
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
