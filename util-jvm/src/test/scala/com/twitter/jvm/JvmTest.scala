package com.twitter.jvm

import com.twitter.conversions.time._
import com.twitter.logging.{TestLogging, Level}
import com.twitter.util.Time
import java.util.concurrent.{ScheduledExecutorService, ScheduledFuture, TimeUnit}
import org.mockito.ArgumentCaptor
import org.mockito.{Mockito => Mock}
import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class JvmTest extends WordSpec with ShouldMatchers with TestLogging {
  "Jvm" should {
    class JvmHelper {
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
      
        def forceGc() = ()
        def edenPool = NilJvm.edenPool
      }
    }

    "foreachGc" should {
      "Capture interleaving GCs with different names" in {
        val h = new JvmHelper
        import h._
        val b = mutable.Buffer[Gc]()
        jvm.executor.schedules shouldEqual List()
        jvm foreachGc { b += _ }
        jvm.executor.schedules should have size (1)
        val Seq((r, _, _, _)) = jvm.executor.schedules
        r.run()
        b shouldEqual List()
        val gc = Gc(0, "pcopy", Time.now, 1.millisecond)
        jvm.pushGc(gc)
        r.run()
        b should have size (1)
        b(0) shouldEqual(gc)
        r.run()
        b should have size (1)
        val gc1 = gc.copy(name="CMS")
        jvm.pushGc(gc1)
        r.run()
        b should have size (2)
        b(1) shouldEqual(gc1)
        r.run()
        r.run()
        r.run()
        b should have size (2)
        jvm.pushGc(gc1.copy(count=1))
        jvm.pushGc(gc.copy(count=1))
        r.run()
        b should have size (4)
        b(2) shouldEqual(gc.copy(count=1))
        b(3) shouldEqual(gc1.copy(count=1))
      }

      "Complain when sampling rate is too low, every 30 minutes" in Time.withCurrentTimeFrozen { tc =>
        val h = new JvmHelper
        import h._

        traceLogger(Level.WARNING)

        jvm foreachGc { _ => /*ignore*/}
        jvm.executor.schedules should have size (1)
        val Seq((r, _, _, _)) = jvm.executor.schedules
        val gc = Gc(0, "pcopy", Time.now, 1.millisecond)
        r.run()
        jvm.pushGc(gc)
        r.run()
        jvm.pushGc(gc.copy(count=2))
        r.run()
        logLines() shouldEqual(Seq("Missed 1 collections for pcopy due to sampling"))
        jvm.pushGc(gc.copy(count=10))
        logLines() shouldEqual(Seq("Missed 1 collections for pcopy due to sampling"))
        r.run()
        tc.advance(29.minutes)
        r.run()
        logLines() shouldEqual(Seq("Missed 1 collections for pcopy due to sampling"))
        tc.advance(2.minutes)
        jvm.pushGc(gc.copy(count=12))
        r.run()
        logLines() shouldEqual(Seq(
          "Missed 1 collections for pcopy due to sampling",
          "Missed 8 collections for pcopy due to sampling"
        ))
      }
    }

    "monitorsGcs" should {
      "queries gcs in range, in reverse chronological order" in Time.withCurrentTimeFrozen { tc =>
        val h = new JvmHelper
        import h._

        val query = jvm.monitorGcs(10.seconds)
        jvm.executor.schedules should have size (1)
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
        query(10.seconds.ago) shouldEqual(Seq(gc2, gc1, gc0))
        tc.advance(2.seconds)
        val gc3 = Gc(2, "CMS", Time.now, 1.second)
        jvm.pushGc(gc3)
        r.run()
        query(10.seconds.ago) shouldEqual(Seq(gc3, gc2))
      }
    }
  }
}
