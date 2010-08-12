package com.twitter.util

object StateMachine {
  class InvalidStateTransition(message: String) extends Exception(message)
}

trait StateMachine {
  import StateMachine._

  protected abstract class State
  protected var state: State = _

  protected def transition[A](f: State => A) = {
    try {
      f(state)
    } catch {
      case e: MatchError =>
        throw new InvalidStateTransition(state.toString)
    }
  }
}