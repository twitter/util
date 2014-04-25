package com.twitter.util

import org.scalatest.{WordSpec, Matchers}

class StateMachineSpec extends WordSpec with Matchers {
  "StateMachine" should  {
    class StateMachineHelper {
      val stateMachine = new StateMachine {
        case class State1() extends State
        case class State2() extends State
        state = State1()

        def command1() {
          transition("command1") {
            case State1() => state = State2()
          }
        }
      }
    }

    "allows transitions that are permitted" in {
      val h = new StateMachineHelper
      import h._

      stateMachine.command1()
    }

    "throws exceptions when a transition is not permitted" in {
      val h = new StateMachineHelper
      import h._

      stateMachine.command1()
      intercept[StateMachine.InvalidStateTransition] {
        stateMachine.command1()
      }
    }
  }
}
