package com.twitter.util

import java.lang.Thread
import java.util.concurrent.{CountDownLatch => JCountDownLatch}

import scala.annotation.tailrec

object Memoize {
  // A CountDownLatch that may not be awaited on by the thread that constructs it.
  // Used to catch reentrant calls to Snappable apply which would deadlock the thread.
  private[this] class GuardedCountDownLatch(count: Int) {
    private[this] val owner = Thread.currentThread
    private[this] val underlying = new JCountDownLatch(count)

    def countDown(): Unit = underlying.countDown()

    def await(): Unit = {
      if (Thread.currentThread eq owner) {
        throw new IllegalStateException(
          s"${owner}: Attempted to wait on a value this thread is currently computing"
        )
      }
      underlying.await()
    }
  }

  /**
   * A Snappable is a memoized function for which a
   * [[scala.collection.immutable.Map]] of the currently memoized computations
   * can be obtained.
   */
  trait Snappable[A, B] extends (A => B) {

    /**
     * Produces a snapshot of the currently memoized computations, as a
     * [[scala.collection.immutable.Map]]
     */
    def snap: Map[A, B]

    /**
     * Returns the most recent size of the currently memoized computations.
     * Useful for stats reporting.
     */
    def size: Long
  }

  /**
   * Thread-safe memoization for a function.
   *
   * This works like a lazy val indexed by the input value. The memo
   * is held as part of the state of the returned function, so keeping
   * a reference to the function will keep a reference to the
   * (unbounded) memo table. The memo table will never forget a
   * result, and will retain a reference to the corresponding input
   * values as well.
   *
   * If the computation has side-effects, they will happen exactly
   * once per input, even if multiple threads attempt to memoize the
   * same input at one time, unless the computation throws an
   * exception. If an exception is thrown, then the result will not be
   * stored, and the computation will be attempted again upon the next
   * access. Only one value will be computed at a time. The overhead
   * required to ensure that the effects happen only once is paid only
   * in the case of a miss (once per input over the life of the memo
   * table). Computations for different input values will not block
   * each other.
   *
   * The combination of these factors means that this method is useful
   * for functions that will only ever be called on small numbers of
   * inputs, are expensive compared to a hash lookup and the memory
   * overhead, and will be called repeatedly.
   */
  def apply[A, B](f: A => B): A => B = snappable[A, B](f)

  /**
   * Thread-safe memoization for a Function2.
   */
  def function2[A, B, C](f: (A, B) => C): (A, B) => C = scala.Function.untupled(apply(f.tupled))

  /**
   * Produces [[com.twitter.util.Memoize.Snappable]], thread-safe
   * memoization for a function.
   */
  def snappable[A, B](f: A => B): Snappable[A, B] =
    new Snappable[A, B] {
      private[this] var memo = Map.empty[A, Either[GuardedCountDownLatch, B]]

      override def size: Long = memo.size

      def snap: Map[A, B] =
        synchronized(memo) collect {
          case (a, Right(b)) => (a, b)
        }

      /**
       * What to do if we do not find the value already in the memo
       * table.
       */
      @tailrec private[this] def missing(a: A): B =
        synchronized {
          // With the lock, check to see what state the value is in.
          memo.get(a) match {
            case None =>
              // If it's missing, then claim the slot by putting in a
              // CountDownLatch that will be completed when the value is
              // available.
              val latch = new GuardedCountDownLatch(1)
              memo = memo + (a -> Left(latch))

              // The latch wrapped in Left indicates that the value
              // needs to be computed in this thread, and then the
              // latch counted down.
              Left(latch)

            case Some(other) =>
              // This is either the latch that will indicate that the
              // work has been done, or the computed value.
              Right(other)
          }
        } match {
          case Right(Right(b)) =>
            // The computation is already done.
            b

          case Right(Left(latch)) =>
            // Someone else is doing the computation.
            latch.await()

            // This recursive call will happen when there is an
            // exception computing the value, or if the value is
            // currently being computed.
            missing(a)

          case Left(latch) =>
            // Compute the value outside of the synchronized block.
            val b =
              try {
                f(a)
              } catch {
                case t: Throwable =>
                  // If there was an exception running the
                  // computation, then we need to make sure we do not
                  // starve any waiters before propagating the
                  // exception.
                  synchronized { memo = memo - a }
                  latch.countDown()
                  throw t
              }

            // Update the memo table to indicate that the work has
            // been done, and signal to any waiting threads that the
            // work is complete.
            synchronized { memo = memo + (a -> Right(b)) }
            latch.countDown()
            b
        }

      override def apply(a: A): B =
        // Look in the (possibly stale) memo table. If the value is
        // present, then it is guaranteed to be the final value. If it
        // is absent, call missing() to determine what to do.
        memo.get(a) match {
          case Some(Right(b)) => b
          case _ => missing(a)
        }
    }

  /**
   * Thread-safe memoization for a function taking Class objects,
   * using [[java.lang.ClassValue]].
   *
   * @param f the function to memoize using a `ClassValue`
   * @tparam T the return type of the function
   * @return a memoized function over classes that stores memoized values
   *         in a `ClassValue`
   */
  def classValue[T](f: Class[_] => T): Class[_] => T = {
    val cv = new ClassValue[T] {
      def computeValue(c: Class[_]): T = f(c)
    }

    cv.get
  }
}
