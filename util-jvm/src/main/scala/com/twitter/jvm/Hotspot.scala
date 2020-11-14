package com.twitter.jvm

import com.twitter.conversions.StorageUnitOps._
import com.twitter.conversions.DurationOps._
import com.twitter.util.Time
import java.lang.management.ManagementFactory
import java.util.logging.{Level, Logger}
import javax.management.openmbean.{CompositeData, CompositeDataSupport}
import javax.management.{
  Notification,
  NotificationEmitter,
  NotificationListener,
  ObjectName,
  RuntimeMBeanException
}
import javax.naming.OperationNotSupportedException
import scala.jdk.CollectionConverters._
import scala.language.reflectiveCalls

class Hotspot extends Jvm {
  private[this] val epoch =
    Time.fromMilliseconds(ManagementFactory.getRuntimeMXBean.getStartTime)

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
    try {
      sun.management.ManagementFactoryHelper
        .getVMManagement
        .asInstanceOf[VMManagement]
    } catch {
      case _: NoSuchMethodError => {
        val fld = Class.forName("sun.management.ManagementFactoryHelper")
          .getDeclaredField("jvm")
        fld.setAccessible(true)
        fld.get(null).asInstanceOf[VMManagement]
      }
    }
  }

  private[this] def opt(name: String) =
    try Some {
      val o = ManagementFactory.getPlatformMBeanServer
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
    try {
      val cs = jvm.getInternalCounters(pat).asScala
      cs.map { c => c.getName() -> c }.toMap
    } catch {
      case e: OperationNotSupportedException =>
        log.log(
          Level.WARNING,
          s"failed to get internal JVM counters as these are not available on your JVM.")
        Map.empty[String, Counter]
      case e: Throwable =>
        throw e
    }
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

  private[this] def newGcListener(f: Gc => Unit): NotificationListener =
    new NotificationListener {
      def handleNotification(notification: Notification, handback: Any): Unit = {
        if (Hotspot.isGcNotification(notification)) {
          val gc =
            Hotspot.gcFromNotificationInfo(notification.getUserData.asInstanceOf[CompositeData])
          f(gc)
        }
      }
    }

  override def foreachGc(f: Gc => Unit): Unit = {
    ManagementFactory.getGarbageCollectorMXBeans.asScala.foreach {
      case bean: NotificationEmitter =>
        bean.addNotificationListener(newGcListener(f), null, null)
      case _ => ()
    }
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

  private[this] object NilSafepointBean extends SafepointBean {
    def getSafepointSyncTime = 0L
    def getTotalSafepointTime = 0L
    def getSafepointCount = 0L
  }

  private val log = Logger.getLogger(getClass.getName)

  private[this] val safepointBean = {
    val runtimeBean = sun.management.ManagementFactoryHelper.getHotspotRuntimeMBean

    def asSafepointBean(x: AnyRef): SafepointBean = x match {

      case h: sun.management.HotspotRuntimeMBean => new SafepointBean() {
        override def getSafepointCount: Long = h.getSafepointCount
        override def getSafepointSyncTime: Long = h.getSafepointSyncTime
        override def getTotalSafepointTime: Long = h.getTotalSafepointTime
      }

      case a => a.asInstanceOf[SafepointBean]
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

  private[this] trait SafepointBean {
    def getSafepointSyncTime : Long
    def getTotalSafepointTime : Long
    def getSafepointCount : Long
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
    counters("").map { case (k, v) => (k -> v.getValue().toString) }

  def forceGc(): Unit = System.gc()
}

private object Hotspot {

  // this inlines the value of GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION
  // to avoid reflection
  def isGcNotification(notification: Notification): Boolean =
    notification.getType == "com.sun.management.gc.notification"

  private[this] val jvmStart =
    Time.fromMilliseconds(ManagementFactory.getRuntimeMXBean.getStartTime)

  private[this] val gcNotifInfoFromMethod =
    Class
      .forName("com.sun.management.GarbageCollectionNotificationInfo")
      .getMethod("from", classOf[CompositeData])

  private[this] val gcNotifInfoGetGcInfoMethod =
    Class
      .forName("com.sun.management.GarbageCollectionNotificationInfo")
      .getMethod("getGcInfo")

  private[this] val gcNotifInfoGetGcNameMethod =
    Class
      .forName("com.sun.management.GarbageCollectionNotificationInfo")
      .getMethod("getGcName")

  private[this] val gcInfoGetIdMethod =
    Class
      .forName("com.sun.management.GcInfo")
      .getMethod("getId")

  private[this] val gcInfoGetStartTimeMethod =
    Class
      .forName("com.sun.management.GcInfo")
      .getMethod("getStartTime")

  private[this] val gcInfoGetDurationMethod =
    Class
      .forName("com.sun.management.GcInfo")
      .getMethod("getDuration")

  /**
   * This uses reflection because we the `com.sun` classes may not be available
   * at compilation time.
   */
  def gcFromNotificationInfo(compositeData: CompositeData): Gc = {
    val gcNotifInfo /* com.sun.management.GarbageCollectionNotificationInfo */ =
      gcNotifInfoFromMethod.invoke(null /* static method */, compositeData)
    val gcInfo /* com.sun.management.GcInfo */ =
      gcNotifInfoGetGcInfoMethod.invoke(gcNotifInfo)
    Gc(
      count = gcInfoGetIdMethod.invoke(gcInfo).asInstanceOf[Long],
      name = gcNotifInfoGetGcNameMethod.invoke(gcNotifInfo).asInstanceOf[String],
      timestamp = jvmStart + gcInfoGetStartTimeMethod
        .invoke(gcInfo).asInstanceOf[Long].milliseconds,
      duration = gcInfoGetDurationMethod.invoke(gcInfo).asInstanceOf[Long].milliseconds
    )
  }

}
