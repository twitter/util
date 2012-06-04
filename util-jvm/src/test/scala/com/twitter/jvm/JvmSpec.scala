package com.twitter.jvm

import com.twitter.conversions.time._
import com.twitter.logging.{TestLogging, Level}
import com.twitter.util.Time
import java.util.concurrent.{ScheduledExecutorService, ScheduledFuture, TimeUnit}
import org.mockito.ArgumentCaptor
import org.mockito.{Mockito => Mock}
import org.specs.SpecificationWithJUnit
import org.specs.mock.Mockito
import scala.collection.mutable

class JvmSpec extends SpecificationWithJUnit with Mockito with TestLogging {
  "Jvm" should {
    object jvm extends Jvm {
      @volatile private[this] var currentSnap: Snapshot =
        Snapshot(Time.epoch, Heap(0, 0, Seq()), Seq())

      val opts = new Opts {
        def compileThresh = None
      }
      def snapCounters = Map()
      def setSnap(snap: Snapshot) {
        currentSnap= snap
      }

      override val executor = new MockScheduledExecutorService

      def snap = currentSnap

      def pushGc(gc: Gc) {
        val gcs = snap.lastGcs filter(_.name != gc.name)
        setSnap(snap.copy(lastGcs=gc +: gcs))
      }
    }

    "foreachGc" in {
      "Capture interleaving GCs with different names" in {
        val b = mutable.Buffer[Gc]()
        jvm.executor.schedules must beEmpty
        jvm foreachGc { b += _ }
        jvm.executor.schedules must haveSize(1)
        val Seq((r, _, _, _)) = jvm.executor.schedules
        r.run()
        b must beEmpty
        val gc = Gc(0, "pcopy", Time.now, 1.millisecond)
        jvm.pushGc(gc)
        r.run()
        b must haveSize(1)
        b(0) must be_==(gc)
        r.run()
        b must haveSize(1)
        val gc1 = gc.copy(name="CMS")
        jvm.pushGc(gc1)
        r.run()
        b must haveSize(2)
        b(1) must be_==(gc1)
        r.run()
        r.run()
        r.run()
        b must haveSize(2)
        jvm.pushGc(gc1.copy(count=1))
        jvm.pushGc(gc.copy(count=1))
        r.run()
        b must haveSize(4)
        b(2) must be_==(gc.copy(count=1))
        b(3) must be_==(gc1.copy(count=1))
      }

      "Complain when sampling rate is too low" in {
        traceLogger(Level.WARNING)

        jvm foreachGc { _ => /*ignore*/}
        jvm.executor.schedules must haveSize(1)
        val Seq((r, _, _, _)) = jvm.executor.schedules
        val gc = Gc(0, "pcopy", Time.now, 1.millisecond)
        r.run()
        jvm.pushGc(gc)
        r.run()
        jvm.pushGc(gc.copy(count=2))
        r.run()
        mustLog("Missed 1 collections for pcopy due to sampling")
      }
    }

    "monitorsGcs" in {
      "queries gcs in range, in reverse chronological order" in Time.withCurrentTimeFrozen { tc =>
        val query = jvm.monitorGcs(10.seconds)
        jvm.executor.schedules must haveSize(1)
        val Seq((r, _, _, _)) = jvm.executor.schedules
        val gc0 = Gc(0, "pcopy", Time.now, 1.millisecond)
        val gc1 = Gc(1, "CMS", Time.now, 1.millisecond)
        jvm.pushGc(gc1)
        jvm.pushGc(gc0)
        r.run()
        tc.advance(9.seconds)
        val gc2 = Gc(1, "pcopy", Time.now, 2.milliseconds)
        jvm.pushGc(gc2)
        r.run()
        query(10.seconds.ago) must be_==(Seq(gc2, gc1, gc0))
        tc.advance(2.seconds)
        val gc3 = Gc(2, "CMS", Time.now, 1.second)
        jvm.pushGc(gc3)
        r.run()
        query(10.seconds.ago) must be_==(Seq(gc3, gc2))
      }
    }
  }
}
