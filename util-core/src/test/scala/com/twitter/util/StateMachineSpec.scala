package com.twitter.util

import org.specs.SpecificationWithJUnit

class StateMachineSpec extends SpecificationWithJUnit {
  "StateMachine" should {
    val stateMachine = new StateMachine {
      case class State1() extends State
      case class State2() extends State
      state = State1()

      def command1() {
        transition("command1") {
          case State1() => "ok"
            state = State2()
        }
      }
    }

    "allows transitions that are permitted" in {
      stateMachine.command1() mustNot throwA[StateMachine.InvalidStateTransition]
    }

    "throws exceptions when a transition is not permitted" in {
      stateMachine.command1() mustNot throwA[StateMachine.InvalidStateTransition]
      stateMachine.command1() must throwA[StateMachine.InvalidStateTransition]
    }
  }
}
