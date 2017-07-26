package com.twitter.util

object StateMachine {
  class InvalidStateTransition(fromState: String, command: String)
      extends Exception("Transitioning from " + fromState + " via command " + command)
}

trait StateMachine {
  import StateMachine._

  protected abstract class State
  protected var state: State = _

  protected def transition[A](command: String)(f: PartialFunction[State, A]) = synchronized {
    if (f.isDefinedAt(state)) {
      f(state)
    } else {
      throw new InvalidStateTransition(state.toString, command)
    }
  }
}
