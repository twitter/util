package com.twitter.concurrent

import com.twitter.util.{Future, Promise, Time, Timer, Duration}
import scala.util.Random

/**
 * An offer to communicate with another process. The offer is
 * parameterized on the type of the value communicated. An offer that
 * sends a value typically has type {{Unit}}. An offer is activated by
 * synchronizing it, which is done with `apply()`.
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
 * synchronization should always be done with `apply()`.
 *
 * Future cancellation is propagated, and failure is passed through. It
 * is up to the implementor of the Offer to decide on failure semantics,
 * but they are always passed through in all of the combinators.
 */
trait Offer[+T] { self =>
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
  @deprecated("use sync() instead")
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
        ourTx.cancel()
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
   * Enumerate this offer (ie.  synchronize while there are active
   * responders until the channel is closed) to the given
   * {{ChannelSource}}.  n.b.: this may buffer at most one value, and
   * on close, a buffered value may be dropped.
   *
   *  note: we could make this buffer free by introducing
   *  synchronization around the channel (this would require us to
   *  make generic the concept of synchronization, and would create
   *  far too much complexity for what would never be a use case)
   */
  @deprecated("Channels are deprecated")
  def enumToChannel[U >: T](ch: ChannelSource[U]) {
    // these are all protected by ch.serialized:
    var buf: Option[T] = None
    var active = false

    def enumerate() {
      // enumerate() is always enter ch.serialized.
      buf foreach { v =>
        ch.send(v)
        buf = None
      }

      this() foreach { v =>
        ch.serialized {
          if (active) {
            ch.send(v)
            enumerate()
          } else {
            buf = Some(v)
          }
        }
      }
    }

    // Only dispatch messages when we have listeners.
    ch.numObservers respond { n =>
      ch.serialized {
        n match {
          case 0 if active =>
            active = false
          case 1 if !active =>
            active = true
            enumerate()
          case _ => ()
        }
      }
    }

    ch.closes foreach { _ =>
      active = false
    }
  }

  /**
   * Create a channel, and enumerate this offer onto it.
   */
  @deprecated("Channels are deprecated")
  def toChannel: Channel[T] = {
    val ch = new ChannelSource[T]
    enumToChannel(ch)
    ch
  }

  /**
   * Synchronize this offer, blocking for the result. See {{apply()}}
   * and {{com.twitter.util.Future.apply()}}
   */
  def syncWait(): T = sync()()

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

  /**
   * The offer that chooses exactly one of the given offers. If there are any
   * Offers that are synchronizable immediately, one is chosen at random.
   */
  def choose[T](evs: Offer[T]*): Offer[T] = if (evs.isEmpty) Offer.never else new Offer[T] {
    def prepare(): Future[Tx[T]] = {
      val rng = new Random(Time.now.inNanoseconds)
      val prepd = rng.shuffle(evs map { ev => ev.prepare() })

      prepd find(_.isDefined) match {
        case Some(winner) =>
          for (loser <- prepd filter { _ ne winner }) {
            loser onSuccess { tx => tx.nack() }
            loser.cancel()
          }

          winner

        case None =>
          Future.select(prepd) flatMap { case (winner, losers) =>
            for (loser <- losers) {
              loser onSuccess { tx => tx.nack() }
              loser.cancel()
            }

            Future.const(winner)
          }
      }
    }
  }

  /**
   * `Offer.choose()` and synchronize it.
   */
  def select[T](ofs: Offer[T]*): Future[T] = choose(ofs:_*).sync()

  /**
   * An offer that is available after the given time out.
   */
  def timeout(timeout: Duration)(implicit timer: Timer): Offer[Unit] = new Offer[Unit] {
    private[this] val deadline = timeout.fromNow
    private[this] val tx = Tx.const(())

    def prepare() = {
      if (deadline <= Time.now) Future.value(tx) else {
        val p = new Promise[Tx[Unit]]
        val task = timer.schedule(deadline) {
          p.setValue(tx)
        }
        p.onCancellation { task.cancel() }
        p
      }
    }
  }
}
