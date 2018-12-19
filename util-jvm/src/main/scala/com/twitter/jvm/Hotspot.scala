package com.twitter.jvm

import com.twitter.conversions.StorageUnitOps._
import com.twitter.conversions.DurationOps._
import com.twitter.util.Time
import java.lang.management.ManagementFactory
import java.util.logging.{Level, Logger}
import javax.management.openmbean.CompositeDataSupport
import javax.management.{ObjectName, RuntimeMBeanException}
import scala.collection.JavaConverters._
import scala.language.reflectiveCalls

class Hotspot extends Jvm {
  private[this] val epoch =
    Time.fromMilliseconds(ManagementFactory.getRuntimeMXBean().getStartTime())

  private[this] type Counter = {
    def getName(): String
    def getUnits(): Object
    def getValue(): Object
  }

  private[this] type VMManagement = {
    def getInternalCounters(pat: String): java.util.List[Counter]
  }

  private[this] val DiagnosticBean =
    ObjectName.getInstance("com.sun.management:type=HotSpotDiagnostic")

  private[this] val jvm: VMManagement = {
    val fld = Class
      .forName("sun.management.ManagementFactoryHelper")
      .getDeclaredField("jvm")
    fld.setAccessible(true)
    fld.get(null).asInstanceOf[VMManagement]
  }

  private[this] def opt(name: String) =
    try Some {
      val o = ManagementFactory
        .getPlatformMBeanServer()
        .invoke(DiagnosticBean, "getVMOption", Array(name), Array("java.lang.String"))
      o.asInstanceOf[CompositeDataSupport].get("value").asInstanceOf[String]
    } catch {
      case _: IllegalArgumentException =>
        None
      case rbe: RuntimeMBeanException if rbe.getCause.isInstanceOf[IllegalArgumentException] =>
        None
    }

  private[this] def long(c: Counter) = c.getValue().asInstanceOf[Long]

  private[this] def counters(pat: String) = {
    val cs = jvm.getInternalCounters(pat).asScala
    cs.map { c =>
      c.getName() -> c
    }.toMap
  }

  private[this] def counter(name: String): Option[Counter] =
    counters(name).get(name)

  object opts extends Opts {
    def compileThresh: Option[Int] = opt("CompileThreshold").map(_.toInt)
  }

  private[this] def ticksToDuration(ticks: Long, freq: Long) =
    (1000000L * ticks / freq).microseconds

  private[this] def getGc(which: Int, cs: Map[String, Counter]) = {
    def get(what: String) = cs.get("sun.gc.collector.%d.%s".format(which, what))

    for {
      invocations <- get("invocations").map(long)
      lastEntryTicks <- get("lastEntryTime").map(long)
      name <- get("name").map(_.getValue().toString)
      time <- get("time").map(long)
      freq <- cs.get("sun.os.hrt.frequency").map(long)
      duration = ticksToDuration(time, freq)
      lastEntryTime = ticksToDuration(lastEntryTicks, freq)
      kind = "%d.%s".format(which, name)
    } yield Gc(invocations, kind, epoch + lastEntryTime, duration)
  }

  def snap: Snapshot = {
    val cs = counters("")
    val heap = for {
      invocations <- cs.get("sun.gc.collector.0.invocations").map(long)
      capacity <- cs.get("sun.gc.generation.0.space.0.capacity").map(long)
      used <- cs.get("sun.gc.generation.0.space.0.used").map(long)
    } yield {
      val allocated = invocations * capacity + used
      // This is a somewhat poor estimate, since for example the
      // capacity can change over time.

      val tenuringThreshold = cs.get("sun.gc.policy.tenuringThreshold").map(long)

      val ageHisto = for {
        thresh <- tenuringThreshold.toSeq
        i <- 1L.to(thresh)
        bucket <- cs.get("sun.gc.generation.0.agetable.bytes.%02d".format(i))
      } yield long(bucket)

      Heap(allocated, tenuringThreshold getOrElse -1, ageHisto)
    }

    val timestamp = for {
      freq <- cs.get("sun.os.hrt.frequency").map(long)
      ticks <- cs.get("sun.os.hrt.ticks").map(long)
    } yield epoch + ticksToDuration(ticks, freq)

    // TODO: include causes for GCs?
    Snapshot(
      timestamp.getOrElse(Time.epoch),
      heap.getOrElse(Heap(0, 0, Nil)),
      getGc(0, cs).toSeq ++ getGc(1, cs).toSeq
    )
  }

  private[this] object NilSafepointBean {
    def getSafepointSyncTime = 0L
    def getTotalSafepointTime = 0L
    def getSafepointCount = 0L
  }

  private val log = Logger.getLogger(getClass.getName)

  private[this] val safepointBean = {
    val runtimeBean = Class
      .forName("sun.management.ManagementFactoryHelper")
      .getMethod("getHotspotRuntimeMBean")
      .invoke(null)

    def asSafepointBean(x: AnyRef) = {
      x.asInstanceOf[{
        def getSafepointSyncTime: Long
        def getTotalSafepointTime: Long
        def getSafepointCount: Long
      }]
    }
    try {
      asSafepointBean(runtimeBean)
    } catch {
      // Handles possible name changes in new jdk versions
      case t: Throwable =>
        log.log(Level.WARNING, "failed to get runtimeBean", t)
        asSafepointBean(NilSafepointBean)
    }
  }

  def safepoint: Safepoint = {
    val syncTime = safepointBean.getSafepointSyncTime
    val totalTime = safepointBean.getTotalSafepointTime
    val safepointsReached = safepointBean.getSafepointCount
    Safepoint(syncTimeMillis = syncTime, totalTimeMillis = totalTime, count = safepointsReached)
  }

  val edenPool: Pool = new Pool {
    def state(): PoolState = {
      val cs = counters("")
      val state = for {
        invocations <- cs.get("sun.gc.collector.0.invocations").map(long)
        capacity <- cs.get("sun.gc.generation.0.space.0.capacity").map(long)
        used <- cs.get("sun.gc.generation.0.space.0.used").map(long)
      } yield PoolState(invocations, capacity.bytes, used.bytes)

      state getOrElse NilJvm.edenPool.state()
    }
  }

  def metaspaceUsage: Option[Jvm.MetaspaceUsage] = {
    val cs = counters("")
    for {
      used <- cs.get("sun.gc.metaspace.used").map(long)
      cap <- cs.get("sun.gc.metaspace.capacity").map(long)
      maxCap <- cs.get("sun.gc.metaspace.maxCapacity").map(long)
    } yield Jvm.MetaspaceUsage(used.bytes, cap.bytes, maxCap.bytes)
  }

  def applicationTime: Long = counter("sun.rt.applicationTime").map(long).getOrElse(0L)

  def tenuringThreshold: Long = counter("sun.gc.policy.tenuringThreshold").map(long).getOrElse(0L)

  def snapCounters: Map[String, String] =
    counters("").mapValues(_.getValue().toString)

  def forceGc(): Unit = System.gc()
}
