package com.twitter.concurrent

import scala.util.Random

import com.twitter.util.{Await, Duration, Future, Promise, Time, Timer}

/**
 * An offer to communicate with another process. The offer is
 * parameterized on the type of the value communicated. An offer that
 * sends a value typically has type {{Unit}}. An offer is activated by
 * synchronizing it, which is done with `sync()`.
 *
 * Note that Offers are persistent values -- they may be synchronized
 * multiple times. They represent a standing offer of communication, not
 * a one-shot event.
 *
 * =The synchronization protocol=
 *
 * Synchronization is performed via a two-phase commit process.
 * `prepare()` commenses the transaction, and when the other party is
 * ready, it returns with a transaction object, `Tx[T]`. This must then
 * be ackd or nackd. If both parties acknowledge, `Tx.ack()` returns
 * with a commit object, containing the value. This finalizes the
 * transaction. Please see the `Tx` documentation for more details on
 * that phase of the protocol.
 *
 * Note that a user should never perform this protocol themselves --
 * synchronization should always be done with `sync()`.
 *
 * Future interrupts are propagated, and failure is passed through. It
 * is up to the implementor of the Offer to decide on failure semantics,
 * but they are always passed through in all of the combinators.
 */
trait Offer[+T] { self =>
  import Offer.LostSynchronization

  /**
   * Prepare a new transaction. This is the first stage of the 2 phase
   * commit. This is typically only called by the offer implementation
   * directly or by combinators.
   */
  def prepare(): Future[Tx[T]]

  /**
   * Synchronizes this offer, returning a future representing the result
   * of the synchronization.
   */
  def sync(): Future[T] =
    prepare() flatMap { tx =>
      tx.ack() flatMap {
        case Tx.Commit(v) => Future.value(v)
        case Tx.Abort => sync()
      }
    }

  /**
   * Synonym for `sync()`
   */
  @deprecated("use sync() instead", "5.x")
  def apply(): Future[T] = sync()

  /* Combinators */

  /**
   * Map this offer of type {{T}} into one of type {{U}}.  The
   * translation (performed by {{f}}) is done after the {{Offer[T]}} has
   * successfully synchronized.
   */
  def map[U](f: T => U): Offer[U] = new Offer[U] {
    def prepare() = self.prepare() map { tx =>
      new Tx[U] {
        import Tx.{Commit, Abort}
        def ack() = tx.ack() map {
          case Commit(t) => Commit(f(t))
          case Abort => Abort
        }

        def nack() { tx.nack() }
      }
    }
  }

  /**
   * Synonym for `map()`. Useful in combination with `Offer.choose()`
   * and `Offer.select()`
   */
  def apply[U](f: T => U): Offer[U] = map(f)

  /**
   * Like {{map}}, but to a constant (call-by-name).
   */
  def const[U](f: => U): Offer[U] = map { _ => f }

  /**
   * An offer that, when synchronized, attempts to synchronize {{this}}
   * immediately, and if it fails, synchronizes on {{other}} instead.  This is useful
   * for providing default values. Eg.:
   *
   * {{{
   * offer orElse Offer.const { computeDefaultValue() }
   * }}}
   */
  def orElse[U >: T](other: Offer[U]): Offer[U] = new Offer[U] {
    def prepare() = {
      val ourTx = self.prepare()
      if (ourTx.isDefined) ourTx else {
        ourTx foreach { tx => tx.nack() }
        ourTx.raise(LostSynchronization)
        other.prepare()
      }
    }
  }

  def or[U](other: Offer[U]): Offer[Either[T, U]] =
    Offer.choose(this map { Left(_) }, other map { Right(_) })

  /**
   * Synchronize on this offer indefinitely, invoking the given {{f}}
   * with each successfully synchronized value.  A receiver can use
   * this to enumerate over all received values.
   */
  def foreach(f: T => Unit) {
    sync() foreach { v =>
      f(v)
      foreach(f)
    }
  }

  /**
   * Synchronize (discarding the value), and then invoke the given
   * closure.  Convenient for loops.
   */
  def andThen(f: => Unit) {
    sync() onSuccess { _ => f }
  }

  /**
   * Synchronize this offer, blocking for the result. See {{sync()}}
   * and {{com.twitter.util.Future.apply()}}
   */
  def syncWait(): T = Await.result(sync())

  /* Scala actor-style syntax */

  /**
   * Alias for synchronize.
   */
  def ? = sync()

  /**
   * Synchronize, blocking for the result.
   */
  def ?? = syncWait()
}

object Offer {
  /**
   * A constant offer: synchronizes the given value always. This is
   * call-by-name and a new value is produced for each `prepare()`.
   */
  def const[T](x: => T): Offer[T] = new Offer[T] {
    def prepare() = Future.value(Tx.const(x))
  }

  /**
   * An offer that never synchronizes.
   */
  val never: Offer[Nothing] = new Offer[Nothing] {
    def prepare() = Future.never
  }

  private[this] val rng = new Random(Time.now.inNanoseconds)

  /**
   * The offer that chooses exactly one of the given offers. If there are any
   * Offers that are synchronizable immediately, one is chosen at random.
   */
  def choose[T](evs: Offer[T]*): Offer[T] = choose(rng, evs)

  /**
   * The offer that chooses exactly one of the given offers. If there are any
   * Offers that are synchronizable immediately, one is chosen at random.
   *
   * Package-exposed for testing.
   */
  private[concurrent] def choose[T](random: Random, evs: Seq[Offer[T]]): Offer[T] = {
    if (evs.isEmpty) Offer.never else new Offer[T] {
      def prepare(): Future[Tx[T]] = {
        // to avoid unnecessary allocations we do a bunch of manual looping and shuffling
        val inputSize = evs.size
        val prepd = new Array[Future[Tx[T]]](inputSize)
        val iter = evs.iterator
        var i = 0
        while (i < inputSize) {
          prepd(i) = iter.next().prepare()
          i += 1
        }
        while (i > 1) { // i starts at evs.size
          val nextPos = random.nextInt(i)
          val tmp = prepd(i - 1)
          prepd(i - 1) = prepd(nextPos)
          prepd(nextPos) = tmp
          i -= 1
        }
        i = 0
        var foundPos = -1
        while (foundPos < 0 && i < prepd.length) {
          val winner = prepd(i)
          if (winner.isDefined) foundPos = i
          i += 1
        }

        def updateLosers(winPos: Int, prepd: Array[Future[Tx[T]]]): Future[Tx[T]] = {
          val winner = prepd(winPos)
          var j = 0
          while (j < prepd.length) {
            val loser = prepd(j)
            if (loser ne winner) {
              loser onSuccess { tx => tx.nack() }
              loser.raise(LostSynchronization)
            }
            j += 1
          }
          winner
        }

        if (foundPos >= 0) {
          updateLosers(foundPos, prepd)
        } else {
          Future.selectIndex(prepd) flatMap { winPos =>
            updateLosers(winPos, prepd)
          }
        }
      }
    }
  }

  /**
   * `Offer.choose()` and synchronize it.
   */
  def select[T](ofs: Offer[T]*): Future[T] = choose(ofs:_*).sync()

  private[this] val FutureTxUnit = Future.value(Tx.Unit)

  /**
   * An offer that is available after the given time out.
   */
  def timeout(timeout: Duration)(implicit timer: Timer): Offer[Unit] = new Offer[Unit] {
    private[this] val deadline = timeout.fromNow

    def prepare() = {
      if (deadline <= Time.now) FutureTxUnit else {
        val p = new Promise[Tx[Unit]]
        val task = timer.schedule(deadline) { p.setValue(Tx.Unit) }
        p.setInterruptHandler { case _cause => task.cancel() }
        p
      }
    }
  }

  object LostSynchronization extends Exception {
    override def fillInStackTrace = this
  }
}
