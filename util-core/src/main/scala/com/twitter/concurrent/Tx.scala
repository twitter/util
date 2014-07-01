package com.twitter.concurrent

import com.twitter.util.{Future, Promise}

/**
 * A `Tx` is used to mediate multi-party transactions with the following
 * protocol:
 *
 *   1. A transaction is complete when all parties have completed
 *   acknowledgment.
 *   2. If any party aborts (nack), the entire transaction is considered
 *   aborted.
 *   3. Once a transaction has been acknowledged by a party, that
 *   acknowledgment must be honored: The party cannot subsequently nack.
 */
trait Tx[+T] {
  import Tx.Result

  /**
   * Acknowledge the transaction, committing the party should the other
   * parties also acknowledge. The (potentially delayed) result of the
   * complete transaction is returned upon acknowledgment. A party may not
   * `nack()` after `ack()`.
   */
  def ack(): Future[Result[T]]

  /**
   * Abort the transaction. It is invalid to abort a transaction after
   * acknowledging it.
   */
  def nack()
}

object Tx {
  sealed abstract trait Result[+T]
  case object Abort extends Result[Nothing]
  case class Commit[T](value: T) extends Result[T]

  /**
   * A transaction that will always `ack()` with `Abort`.
   */
  val aborted: Tx[Nothing] = new Tx[Nothing] {
    def ack() = Future.value(Abort)
    def nack() {}
  }

  /**
   * A `Tx` that will always commit the given value immediately.
   */
  def const[T](msg: T): Tx[T] = new Tx[T] {
    def ack() = Future.value(Commit(msg))
    def nack() {}
  }

  /**
   * A constant `Tx` with the value of `Unit`.
   */
  val Unit = const(())

  object AlreadyDone extends Exception("Tx is already done")
  object AlreadyAckd extends Exception("Tx was already ackd")
  object AlreadyNackd extends Exception("Tx was already nackd")

  /**
   * Create a two party transaction to exchange the value `msg`.
   *
   * @return a `Tx` object for each participant, (sender, receiver)
   */
  def twoParty[T](msg: T): (Tx[Unit], Tx[T]) = {
    sealed trait State
    case object Idle extends State
    case class Ackd(who: AnyRef, confirm: Boolean => Unit) extends State
    case class Nackd(who: AnyRef) extends State
    case object Done extends State

    var state: State = Idle
    val lock = new {}

    class Party[T](msg: T) extends Tx[T] {
      def ack(): Future[Result[T]] = lock.synchronized {
        state match {
          case Idle =>
            val p = new Promise[Result[T]]
            state = Ackd(this, {
              case true => p.setValue(Commit(msg))
              case false => p.setValue(Abort)
            })
            p

          case Ackd(who, confirm) if who ne this =>
            confirm(true)
            state = Done
            Future.value(Commit(msg))

          case Nackd(who) if who ne this =>
            state = Done
            Future.value(Abort)

          case Ackd(_, _) =>
            throw AlreadyAckd

          case Nackd(_) =>
            throw AlreadyNackd

          case Done =>
            throw AlreadyDone
        }
      }

      def nack() {
        lock.synchronized {
          state match {
            case Idle => state = Nackd(this)
            case Nackd(who) if who ne this => state = Done
            case Ackd(who, confirm) if who ne this =>
              confirm(false)
              state = Done
            case Ackd(_, _) =>
              throw AlreadyAckd
            case Nackd(_) =>
              throw AlreadyNackd
            case Done =>
              throw AlreadyDone
          }
        }
      }
    }

    (new Party(()), new Party(msg))
  }
}
