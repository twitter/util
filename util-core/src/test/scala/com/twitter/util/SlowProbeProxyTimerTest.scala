package com.twitter.util

import com.twitter.conversions.time._
import org.scalatest.FunSuite
import scala.collection.mutable

class SlowProbeProxyTimerTest extends FunSuite {

  private type Task = () => Unit
  private val NullTask = new TimerTask { def cancel(): Unit = () }
  private val maxRuntime = 20.milliseconds

  private class TestSlowProbeProxyTimer extends SlowProbeProxyTimer(maxRuntime) {

    val scheduledTasks: mutable.Queue[Task] = new mutable.Queue[Task]()
    var slowTaskDuration: Option[Duration] = None
    var slowTaskExecuting: Option[Duration] = None

    protected def slowTaskCompleted(elapsed: Duration): Unit = { slowTaskDuration = Some(elapsed) }
    protected def slowTaskExecuting(elapsed: Duration): Unit = { slowTaskExecuting = Some(elapsed) }

    protected val self: Timer = new Timer {
      protected def scheduleOnce(when: Time)(f: => Unit): TimerTask = {
        scheduledTasks.enqueue(() => f)
        NullTask
      }

      protected def schedulePeriodically(when: Time, period: Duration)(f: => Unit): TimerTask =
        schedule(when)(f)

      def stop(): Unit = ()
    }
  }

  test("tasks that don't exceed the deadline are not counted and the slow-task hook is not fired") {
    val meteredTimer = new TestSlowProbeProxyTimer
    val now = Time.now

    Time.withTimeFunction(now) { control =>
      meteredTimer.schedule(Time.now) {
        control.advance(maxRuntime)
      }

      assert(meteredTimer.slowTaskDuration.isEmpty)
      assert(meteredTimer.slowTaskExecuting.isEmpty)

      val task = meteredTimer.scheduledTasks.dequeue()
      task() // execute the task

      assert(meteredTimer.slowTaskDuration.isEmpty)
      assert(meteredTimer.slowTaskExecuting.isEmpty) // no work was scheduled
    }
  }

  test("slow tasks are counted even if other work is not scheduled") {
    val meteredTimer = new TestSlowProbeProxyTimer
    val now = Time.now

    val taskDuration = maxRuntime + 1.millisecond

    Time.withTimeFunction(now) { control =>
      meteredTimer.schedule(Time.now) {
        control.advance(taskDuration)
      }

      assert(meteredTimer.slowTaskDuration.isEmpty)
      assert(meteredTimer.slowTaskExecuting.isEmpty)

      val task = meteredTimer.scheduledTasks.dequeue()
      task() // execute the task

      assert(meteredTimer.slowTaskDuration == Some(taskDuration))
      assert(meteredTimer.slowTaskExecuting.isEmpty) // no work was scheduled
    }
  }

  test("scheduling work during a slow task fires the slow-tast hook") {
    val meteredTimer = new TestSlowProbeProxyTimer
    val now = Time.now

    val taskDuration = maxRuntime + 1.millisecond

    Time.withTimeFunction(now) { control =>
      meteredTimer.schedule(Time.now) {
        // A task that takes 21 milliseconds to schedule more work.
        control.advance(taskDuration)
        meteredTimer.schedule(Time.now) { () /* Boring task :/ */ }
      }

      assert(meteredTimer.slowTaskDuration.isEmpty)
      assert(meteredTimer.slowTaskExecuting.isEmpty)

      val task = meteredTimer.scheduledTasks.dequeue()
      task() // execute the task

      assert(meteredTimer.slowTaskDuration == Some(taskDuration))
      assert(meteredTimer.slowTaskExecuting == Some(taskDuration))
    }
  }
}
