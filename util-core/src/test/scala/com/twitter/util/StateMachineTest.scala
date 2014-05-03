package com.twitter.util

import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class StateMachineTest extends WordSpec with ShouldMatchers {
  "StateMachine" should {
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
