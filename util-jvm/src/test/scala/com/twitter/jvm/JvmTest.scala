package com.twitter.jvm

import com.twitter.conversions.time._
import com.twitter.logging.{Level, TestLogging}
import com.twitter.util.Time
import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class JvmTest extends WordSpec with TestLogging {
  "Jvm" should {
    class JvmHelper {
      object jvm extends Jvm {
        @volatile private[this] var currentSnap: Snapshot =
          Snapshot(Time.epoch, Heap(0, 0, Seq()), Seq())

        val opts = new Opts {
          def compileThresh = None
        }
        def snapCounters = Map()
        def setSnap(snap: Snapshot): Unit = {
          currentSnap = snap
        }

        override val executor = new MockScheduledExecutorService

        def snap = currentSnap

        def pushGc(gc: Gc): Unit = {
          val gcs = snap.lastGcs filter (_.name != gc.name)
          setSnap(snap.copy(lastGcs = gc +: gcs))
        }

        def forceGc() = ()
        def edenPool = NilJvm.edenPool
        def metaspaceUsage = NilJvm.metaspaceUsage
        def safepoint = NilJvm.safepoint
        def applicationTime = NilJvm.applicationTime
        def tenuringThreshold = NilJvm.tenuringThreshold
      }
    }

    "ProcessId" should {
      val supported = Seq("Mac OS X", "Linux")

      val osIsSupported =
        Option(System.getProperty("os.name")).exists(supported.contains)

      if (osIsSupported) {
        "define process id on supported platforms" in {
          assert(Jvm.ProcessId.isDefined)
        }
      }
    }

    "foreachGc" should {
      "Capture interleaving GCs with different names" in {
        val h = new JvmHelper
        import h._
        val b = mutable.Buffer[Gc]()
        assert(jvm.executor.schedules == List())
        jvm foreachGc { b += _ }
        assert(jvm.executor.schedules.size == 1)
        val Seq((r, _, _, _)) = jvm.executor.schedules
        r.run()
        assert(b == List())
        val gc = Gc(0, "pcopy", Time.now, 1.millisecond)
        jvm.pushGc(gc)
        r.run()
        assert(b.size == 1)
        assert(b(0) == gc)
        r.run()
        assert(b.size == 1)
        val gc1 = gc.copy(name = "CMS")
        jvm.pushGc(gc1)
        r.run()
        assert(b.size == 2)
        assert(b(1) == gc1)
        r.run()
        r.run()
        r.run()
        assert(b.size == 2)
        jvm.pushGc(gc1.copy(count = 1))
        jvm.pushGc(gc.copy(count = 1))
        r.run()
        assert(b.size == 4)
        assert(b(2) == gc.copy(count = 1))
        assert(b(3) == gc1.copy(count = 1))
      }

      "Complain when sampling rate is too low, every 30 minutes" in Time.withCurrentTimeFrozen {
        tc =>
          val h = new JvmHelper
          import h._

          traceLogger(Level.DEBUG)

          jvm foreachGc { _ => /*ignore*/
          }
          assert(jvm.executor.schedules.size == 1)
          val Seq((r, _, _, _)) = jvm.executor.schedules
          val gc = Gc(0, "pcopy", Time.now, 1.millisecond)
          r.run()
          jvm.pushGc(gc)
          r.run()
          jvm.pushGc(gc.copy(count = 2))
          r.run()
          assert(logLines() == Seq("Missed 1 collections for pcopy due to sampling"))
          jvm.pushGc(gc.copy(count = 10))
          assert(logLines() == Seq("Missed 1 collections for pcopy due to sampling"))
          r.run()
          tc.advance(29.minutes)
          r.run()
          assert(logLines() == Seq("Missed 1 collections for pcopy due to sampling"))
          tc.advance(2.minutes)
          jvm.pushGc(gc.copy(count = 12))
          r.run()
          assert(
            logLines() == Seq(
              "Missed 1 collections for pcopy due to sampling",
              "Missed 8 collections for pcopy due to sampling"
            )
          )
      }
    }

    "monitorsGcs" should {
      "queries gcs in range, in reverse chronological order" in Time.withCurrentTimeFrozen { tc =>
        val h = new JvmHelper
        import h._

        val query = jvm.monitorGcs(10.seconds)
        assert(jvm.executor.schedules.size == 1)
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
        assert(query(10.seconds.ago) == Seq(gc2, gc1, gc0))
        tc.advance(2.seconds)
        val gc3 = Gc(2, "CMS", Time.now, 1.second)
        jvm.pushGc(gc3)
        r.run()
        assert(query(10.seconds.ago) == Seq(gc3, gc2))
      }
    }

    "safepoint" should {
      "show an increase in total time spent after a gc" in {
        val j = Jvm()
        val preGc = j.safepoint
        val totalTimePreGc = preGc.totalTimeMillis
        System.gc()
        val postGc = j.safepoint
        val totalTimePostGc = postGc.totalTimeMillis
        assert(totalTimePostGc > totalTimePreGc)
      }
    }
  }
}
