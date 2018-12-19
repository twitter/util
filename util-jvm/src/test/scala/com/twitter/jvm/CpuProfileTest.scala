package com.twitter.jvm

import com.twitter.conversions.DurationOps._
import com.twitter.util.Time
import java.io.ByteArrayOutputStream
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CpuProfileTest extends FunSuite {
  test("record") {

    // record() calls Time.now 3 times initially, and then 3 times on every loop iteration.
    val times: Stream[Int] = (0 #:: Stream.from(0)).flatMap(x => List(x, x, x))
    val iter = times.iterator
    val start = Time.now
    def nextTime: Time = start + iter.next().milliseconds * 10

    val t = new Thread("CpuProfileTest") {
      override def run(): Unit = {
        Thread.sleep(10000)
      }
    }
    t.setDaemon(true)
    t.start()

    // Profile for 100ms at 100 Hz => 10ms period; produces 10 samples.
    val profile: CpuProfile = Time.withTimeFunction(nextTime) { _ =>
      CpuProfile.record(100.milliseconds, 100, Thread.State.TIMED_WAITING)
    }

    assert(profile.count == 10)
    assert(profile.missed == 0)

    val baos = new ByteArrayOutputStream
    profile.writeGoogleProfile(baos)
    assert(baos.toString.contains("CpuProfileTest.scala"))
    assert(baos.toString.contains("Thread.sleep"))
  }

  test("isRunnable") {
    def newElem(className: String, methodName: String) =
      new StackTraceElement(className, methodName, "SomeFile.scala", 1)

    assert(CpuProfile.isRunnable(newElem("foo", "bar")))

    assert(!CpuProfile.isRunnable(newElem("sun.nio.ch.EPollArrayWrapper", "epollWait")))
    assert(!CpuProfile.isRunnable(newElem("sun.nio.ch.KQueueArrayWrapper", "kevent0")))
  }
}
