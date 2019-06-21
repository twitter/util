package com.twitter.util

import org.scalatest.WordSpec

class StateMachineTest extends WordSpec {
  "StateMachine" should {
    class StateMachineHelper {
      class Machine extends StateMachine {
        case class State1() extends State
        case class State2() extends State
        state = State1()

        def command1(): Unit = {
          transition("command1") {
            case State1() => state = State2()
          }
        }
      }

      val stateMachine = new Machine()
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
