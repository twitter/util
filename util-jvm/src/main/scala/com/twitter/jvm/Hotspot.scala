package com.twitter.jvm

import com.twitter.conversions.storage._
import com.twitter.conversions.time._
import com.twitter.util.Time
import java.lang.management.ManagementFactory
import java.util.logging.{Level, Logger}
import javax.management.openmbean.CompositeDataSupport
import javax.management.{ObjectName, RuntimeMBeanException}
import scala.collection.JavaConverters._
import scala.language.reflectiveCalls

class Hotspot extends Jvm {
  private[this] val epoch = Time.fromMilliseconds(ManagementFactory.getRuntimeMXBean().getStartTime())

  private[this] type Counter = {
    def getName(): String
    def getUnits(): Object
    def getValue(): Object
  }

  private[this] type VMManagement = {
    def getInternalCounters(pat: String): java.util.List[Counter]
  }

  private[this] val DiagnosticBean = ObjectName.getInstance(
    "com.sun.management:type=HotSpotDiagnostic")

  private[this] val jvm: VMManagement = {
    val fld = try {
      // jdk5/6 have jvm field in ManagementFactory class
      Class.forName("sun.management.ManagementFactory").getDeclaredField("jvm")
    } catch {
      case _: NoSuchFieldException =>
        // jdk7 moves jvm field to ManagementFactoryHelper class
        Class.forName("sun.management.ManagementFactoryHelper").getDeclaredField("jvm")
    }
    fld.setAccessible(true)
    fld.get(null).asInstanceOf[VMManagement]
  }

  private[this] def opt(name: String) = try Some {
    val o = ManagementFactory.getPlatformMBeanServer().invoke(
      DiagnosticBean, "getVMOption",
      Array(name), Array("java.lang.String"))
    o.asInstanceOf[CompositeDataSupport].get("value").asInstanceOf[String]
  } catch {
    case _: IllegalArgumentException =>
      None
    case rbe: RuntimeMBeanException
    if rbe.getCause.isInstanceOf[IllegalArgumentException] =>
      None
  }

  private[this] def long(c: Counter) = c.getValue().asInstanceOf[Long]

  private[this] def counters(pat: String) = {
    val cs = jvm.getInternalCounters(pat).asScala
    cs.map { c => c.getName() -> c }.toMap
  }

  private[this] def counter(name: String): Option[Counter] =
    counters(name).get(name)

  object opts extends Opts {
    def compileThresh = opt("CompileThreshold") map(_.toInt)
  }

  private[this] def ticksToDuration(ticks: Long, freq: Long) =
    (1000000*ticks/freq).microseconds

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
    } yield Gc(invocations, kind, epoch+lastEntryTime, duration)
  }

  def snap: Snapshot = {
    val cs = counters("")
    val heap = for {
      invocations <- cs.get("sun.gc.collector.0.invocations").map(long)
      capacity <- cs.get("sun.gc.generation.0.space.0.capacity").map(long)
      used <- cs.get("sun.gc.generation.0.space.0.used").map(long)
    } yield {
      val allocated = invocations*capacity + used
      // This is a somewhat poor estimate, since for example the
      // capacity can change over time.

      val tenuringThreshold = cs.get("sun.gc.policy.tenuringThreshold").map(long)

      val ageHisto = for {
        thresh <- tenuringThreshold.toSeq
        i <- 1L to thresh
        bucket <- cs.get("sun.gc.generation.0.agetable.bytes.%02d".format(i))
      } yield long(bucket)

      Heap(allocated, tenuringThreshold getOrElse -1, ageHisto)
    }

    val timestamp = for {
      freq <- cs.get("sun.os.hrt.frequency").map(long)
      ticks <- cs.get("sun.os.hrt.ticks").map(long)
    } yield epoch+ticksToDuration(ticks, freq)

    // TODO: include causes for GCs?
    Snapshot(
      timestamp.getOrElse(Time.epoch),
      heap.getOrElse(Heap(0, 0, Seq())),
      getGc(0, cs).toSeq ++ getGc(1, cs).toSeq)
  }

  private[this] object NilSafepointBean {
    def getSafepointSyncTime = 0L
    def getTotalSafepointTime = 0L
    def getSafepointCount = 0L
  }

  private val log = Logger.getLogger(getClass.getName)

  private[this] val safepointBean = {
    val runtimeBean = 
      try { 
        Class.forName("sun.management.ManagementFactory")
          .getMethod("getHotspotRuntimeMBean")
          .invoke(null)
          // jdk 6 has HotspotRuntimeMBean in the ManagementFactory class
      } catch {
        case _: Throwable => 
          Class.forName("sun.management.ManagementFactoryHelper")
            .getMethod("getHotspotRuntimeMBean")
            .invoke(null)
            // jdks 7 and 8 have HotspotRuntimeMBean in the ManagementFactoryHelper class
      }
    
    def asSafepointBean(x: AnyRef) = { x.asInstanceOf[{
      def getSafepointSyncTime: Long; 
      def getTotalSafepointTime: Long;
      def getSafepointCount: Long}]
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
    Safepoint(syncTimeMillis=syncTime, totalTimeMillis=totalTime, count=safepointsReached)
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

  def snapCounters: Map[String, String] =
    counters("") mapValues(_.getValue().toString)

  def forceGc(): Unit = System.gc()
}

/*

A sample of counters available on hotspot:

scala> res0 foreach { case (k, v) => printf("%s = %s\n", k, v) }
java.ci.totalTime = 4453822466
java.cls.loadedClasses = 3459
java.cls.sharedLoadedClasses = 0
java.cls.sharedUnloadedClasses = 0
java.cls.unloadedClasses = 0
java.property.java.class.path = .
java.property.java.endorsed.dirs = /System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home/lib/endorsed
java.property.java.ext.dirs = /Library/Java/Extensions:/System/Library/Java/Extensions:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home/lib/ext
java.property.java.home = /System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home
java.property.java.library.path = /Applications/YourKit_Java_Profiler_9.5.6.app/bin/mac:.:/Library/Java/Extensions:/System/Library/Java/Extensions:/usr/lib/java
java.property.java.version = 1.6.0_29
java.property.java.vm.info = mixed mode
java.property.java.vm.name = Java HotSpot(TM) 64-Bit Server VM
java.property.java.vm.specification.name = Java Virtual Machine Specification
java.property.java.vm.specification.vendor = Sun Microsystems Inc.
java.property.java.vm.specification.version = 1.0
java.property.java.vm.vendor = Apple Inc.
java.property.java.vm.version = 20.4-b02-402
java.rt.vmArgs = -Xserver -Djava.net.preferIPv4Stack=true -Dsbt.log.noformat=true -Xmx2G -XX:MaxPermSize=256m -XX:+UseConcMarkSweepGC -XX:+UseParNewGC -XX:MaxTenuringThreshold=3 -Xbootclasspath/a:/Users/marius/t/src/.scala/scala-2.8.1.final/lib/jline.jar:/Users/marius/t/src/.scala/scala-2.8.1.final/lib/scala-compiler.jar:/Users/marius/t/src/.scala/scala-2.8.1.final/lib/scala-dbc.jar:/Users/marius/t/src/.scala/scala-2.8.1.final/lib/scala-library.jar:/Users/marius/t/src/.scala/scala-2.8.1.final/lib/scala-swing.jar:/Users/marius/t/src/.scala/scala-2.8.1.final/lib/scalap.jar -Dscala.usejavacp=true -Dscala.home=/Users/marius/t/src/.scala/scala-2.8.1.final -Denv.emacs=
java.rt.vmFlags =
java.threads.daemon = 3
java.threads.live = 4
java.threads.livePeak = 5
java.threads.started = 9
sun.ci.compilerThread.0.compiles = 389
sun.ci.compilerThread.0.method =
sun.ci.compilerThread.0.time = 52805
sun.ci.compilerThread.0.type = 1
sun.ci.compilerThread.1.compiles = 442
sun.ci.compilerThread.1.method = java/lang/String compareTo
sun.ci.compilerThread.1.time = 49709
sun.ci.compilerThread.1.type = 1
sun.ci.lastFailedMethod =
sun.ci.lastFailedType = 0
sun.ci.lastInvalidatedMethod =
sun.ci.lastInvalidatedType = 0
sun.ci.lastMethod = java/nio/DirectByteBuffer ix
sun.ci.lastSize = 10
sun.ci.lastType = 1
sun.ci.nmethodCodeSize = 793568
sun.ci.nmethodSize = 2272496
sun.ci.osrBytes = 3470
sun.ci.osrCompiles = 11
sun.ci.osrTime = 75314565
sun.ci.standardBytes = 262871
sun.ci.standardCompiles = 819
sun.ci.standardTime = 4394092506
sun.ci.threads = 2
sun.ci.totalBailouts = 0
sun.ci.totalCompiles = 830
sun.ci.totalInvalidates = 0
sun.cls.appClassBytes = 174471
sun.cls.appClassLoadCount = 165
sun.cls.appClassLoadTime = 16577583
sun.cls.appClassLoadTime.self = 8760586
sun.cls.classInitTime = 153888518
sun.cls.classInitTime.self = 75974400
sun.cls.classLinkedTime = 76083753
sun.cls.classLinkedTime.self = 61672107
sun.cls.classVerifyTime = 14198241
sun.cls.classVerifyTime.self = 8650545
sun.cls.defineAppClassTime = 4361539
sun.cls.defineAppClassTime.self = 579811
sun.cls.defineAppClasses = 35
sun.cls.initializedClasses = 2994
sun.cls.isUnsyncloadClassSet = 0
sun.cls.jniDefineClassNoLockCalls = 0
sun.cls.jvmDefineClassNoLockCalls = 3
sun.cls.jvmFindLoadedClassNoLockCalls = 0
sun.cls.linkedClasses = 3353
sun.cls.loadInstanceClassFailRate = 0
sun.cls.loadedBytes = 9556504
sun.cls.lookupSysClassTime = 165493723
sun.cls.methodBytes = 7128160
sun.cls.nonSystemLoaderLockContentionRate = 0
sun.cls.parseClassTime = 260534425
sun.cls.parseClassTime.self = 225060288
sun.cls.sharedClassLoadTime = 259627
sun.cls.sharedLoadedBytes = 0
sun.cls.sharedUnloadedBytes = 0
sun.cls.sysClassBytes = 16107656
sun.cls.sysClassLoadTime = 518807017
sun.cls.systemLoaderLockContentionRate = 0
sun.cls.time = 547792239
sun.cls.unloadedBytes = 0
sun.cls.unsafeDefineClassCalls = 3
sun.cls.verifiedClasses = 3353
sun.gc.cause = No GC
sun.gc.collector.0.invocations = 13
sun.gc.collector.0.lastEntryTime = 3235357990
sun.gc.collector.0.lastExitTime = 3242811518
sun.gc.collector.0.name = PCopy
sun.gc.collector.0.time = 74741924
sun.gc.collector.1.invocations = 0
sun.gc.collector.1.lastEntryTime = 0
sun.gc.collector.1.lastExitTime = 0
sun.gc.collector.1.name = CMS
sun.gc.collector.1.time = 0
sun.gc.generation.0.agetable.bytes.00 = 0
sun.gc.generation.0.agetable.bytes.01 = 2154984
sun.gc.generation.0.agetable.bytes.02 = 0
sun.gc.generation.0.agetable.bytes.03 = 0
sun.gc.generation.0.agetable.bytes.04 = 0
sun.gc.generation.0.agetable.bytes.05 = 0
sun.gc.generation.0.agetable.bytes.06 = 0
sun.gc.generation.0.agetable.bytes.07 = 0
sun.gc.generation.0.agetable.bytes.08 = 0
sun.gc.generation.0.agetable.bytes.09 = 0
sun.gc.generation.0.agetable.bytes.10 = 0
sun.gc.generation.0.agetable.bytes.11 = 0
sun.gc.generation.0.agetable.bytes.12 = 0
sun.gc.generation.0.agetable.bytes.13 = 0
sun.gc.generation.0.agetable.bytes.14 = 0
sun.gc.generation.0.agetable.bytes.15 = 0
sun.gc.generation.0.agetable.size = 16
sun.gc.generation.0.capacity = 21757952
sun.gc.generation.0.maxCapacity = 174456832
sun.gc.generation.0.minCapacity = 21757952
sun.gc.generation.0.name = new
sun.gc.generation.0.space.0.capacity = 17432576
sun.gc.generation.0.space.0.initCapacity = 0
sun.gc.generation.0.space.0.maxCapacity = 139591680
sun.gc.generation.0.space.0.name = eden
sun.gc.generation.0.space.0.used = 6954760
sun.gc.generation.0.space.1.capacity = 2162688
sun.gc.generation.0.space.1.initCapacity = 0
sun.gc.generation.0.space.1.maxCapacity = 17432576
sun.gc.generation.0.space.1.name = s0
sun.gc.generation.0.space.1.used = 0
sun.gc.generation.0.space.2.capacity = 2162688
sun.gc.generation.0.space.2.initCapacity = 0
sun.gc.generation.0.space.2.maxCapacity = 17432576
sun.gc.generation.0.space.2.name = s1
sun.gc.generation.0.space.2.used = 2162688
sun.gc.generation.0.spaces = 3
sun.gc.generation.0.threads = 8
sun.gc.generation.1.capacity = 65404928
sun.gc.generation.1.maxCapacity = 1973026816
sun.gc.generation.1.minCapacity = 65404928
sun.gc.generation.1.name = old
sun.gc.generation.1.space.0.capacity = 65404928
sun.gc.generation.1.space.0.initCapacity = 65404928
sun.gc.generation.1.space.0.maxCapacity = 1973026816
sun.gc.generation.1.space.0.name = old
sun.gc.generation.1.space.0.used = 25122784
sun.gc.generation.1.spaces = 1
sun.gc.generation.2.capacity = 33161216
sun.gc.generation.2.maxCapacity = 268435456
sun.gc.generation.2.minCapacity = 21757952
sun.gc.generation.2.name = perm
sun.gc.generation.2.space.0.capacity = 33161216
sun.gc.generation.2.space.0.initCapacity = 21757952
sun.gc.generation.2.space.0.maxCapacity = 268435456
sun.gc.generation.2.space.0.name = perm
sun.gc.generation.2.space.0.used = 32439520
sun.gc.generation.2.spaces = 1
sun.gc.lastCause = unknown GCCause
sun.gc.policy.collectors = 2
sun.gc.policy.desiredSurvivorSize = 1081344
sun.gc.policy.generations = 3
sun.gc.policy.maxTenuringThreshold = 3
sun.gc.policy.name = ParNew:CMS
sun.gc.policy.tenuringThreshold = 1
sun.gc.tlab.alloc = 756560
sun.gc.tlab.allocThreads = 1
sun.gc.tlab.fastWaste = 0
sun.gc.tlab.fills = 28
sun.gc.tlab.gcWaste = 0
sun.gc.tlab.maxFastWaste = 0
sun.gc.tlab.maxFills = 28
sun.gc.tlab.maxGcWaste = 0
sun.gc.tlab.maxSlowAlloc = 4
sun.gc.tlab.maxSlowWaste = 116
sun.gc.tlab.slowAlloc = 4
sun.gc.tlab.slowWaste = 116
sun.os.hrt.frequency = 1000000000
sun.os.hrt.ticks = 6949680691
sun.property.sun.boot.class.path = /System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/jsfd.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/classes.jar:/System/Library/Frameworks/JavaVM.framework/Frameworks/JavaRuntimeSupport.framework/Resources/Java/JavaRuntimeSupport.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/ui.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/laf.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/sunrsasign.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/jsse.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/jce.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/charsets.jar:/Users/marius/t/src/.scala/scala-2.8.1.final/lib/jline.jar:/Users/marius/t/src/.scala/scala-2.8.1.final/lib/scala-compiler.jar:/Users/marius/t/src/.scala/scala-2.8.1.final/lib/scala-dbc.jar:/Users/marius/t/src/.scala/scala-2.8.1.final/lib/scala-library.jar:/Users/marius/t/src/
sun.property.sun.boot.library.path = /System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Libraries
sun.rt._sync_ContendedLockAttempts = 53
sun.rt._sync_Deflations = 11
sun.rt._sync_EmptyNotifications = 0
sun.rt._sync_FailedSpins = 0
sun.rt._sync_FutileWakeups = 4
sun.rt._sync_Inflations = 16
sun.rt._sync_MonExtant = 256
sun.rt._sync_MonInCirculation = 0
sun.rt._sync_MonScavenged = 0
sun.rt._sync_Notifications = 15
sun.rt._sync_Parks = 43
sun.rt._sync_PrivateA = 0
sun.rt._sync_PrivateB = 0
sun.rt._sync_SlowEnter = 0
sun.rt._sync_SlowExit = 0
sun.rt._sync_SlowNotify = 0
sun.rt._sync_SlowNotifyAll = 0
sun.rt._sync_SuccessfulSpins = 0
sun.rt.applicationTime = 6804389569
sun.rt.createVmBeginTime = 1320946078890
sun.rt.createVmEndTime = 1320946078960
sun.rt.internalVersion = Java HotSpot(TM) 64-Bit Server VM (20.4-b02-402) for macosx-amd64 JRE (1.6.0), built on Nov  1 2011 10:50:41 by "root" with gcc 4.2.1 (Based on Apple Inc. build 5658) (LLVM build 2335.15.00)
sun.rt.interruptedBeforeIO = 0
sun.rt.interruptedDuringIO = 0
sun.rt.javaCommand = scala.tools.nsc.MainGenericRunner -classpath /Users/marius/t/src/util/util-core/target/classes:/Users/marius/t/src/util/util-jvm/target/classes:/Users/marius/t/src/project/boot/scala-2.8.1/lib/scala-library.jar
sun.rt.jvmCapabilities = 1000000000000000000000000000000000000000000000000000000000000000
sun.rt.jvmVersion = 335806466
sun.rt.safepointSyncTime = 3107558
sun.rt.safepointTime = 82605199
sun.rt.safepoints = 21
sun.rt.threadInterruptSignaled = 0
sun.rt.vmInitDoneTime = 1320946078941
sun.threads.vmOperationTime = 76932327


## after a System.gc()

sun.gc.cause = No GC
sun.gc.collector.0.invocations = 13
sun.gc.collector.0.lastEntryTime = 3235357990
sun.gc.collector.0.lastExitTime = 3242811518
sun.gc.collector.0.name = PCopy
sun.gc.collector.0.time = 74741924
sun.gc.collector.1.invocations = 0
sun.gc.collector.1.lastEntryTime = 0
sun.gc.collector.1.lastExitTime = 0
sun.gc.collector.1.name = CMS
sun.gc.collector.1.time = 0
sun.gc.generation.0.agetable.bytes.00 = 0
sun.gc.generation.0.agetable.bytes.01 = 2154984
sun.gc.generation.0.agetable.bytes.02 = 0
sun.gc.generation.0.agetable.bytes.03 = 0
sun.gc.generation.0.agetable.bytes.04 = 0
sun.gc.generation.0.agetable.bytes.05 = 0
sun.gc.generation.0.agetable.bytes.06 = 0
sun.gc.generation.0.agetable.bytes.07 = 0
sun.gc.generation.0.agetable.bytes.08 = 0
sun.gc.generation.0.agetable.bytes.09 = 0
sun.gc.generation.0.agetable.bytes.10 = 0
sun.gc.generation.0.agetable.bytes.11 = 0
sun.gc.generation.0.agetable.bytes.12 = 0
sun.gc.generation.0.agetable.bytes.13 = 0
sun.gc.generation.0.agetable.bytes.14 = 0
sun.gc.generation.0.agetable.bytes.15 = 0
sun.gc.generation.0.agetable.size = 16
sun.gc.generation.0.capacity = 21757952
sun.gc.generation.0.maxCapacity = 174456832
sun.gc.generation.0.minCapacity = 21757952
sun.gc.generation.0.name = new
sun.gc.generation.0.space.0.capacity = 17432576
sun.gc.generation.0.space.0.initCapacity = 0
sun.gc.generation.0.space.0.maxCapacity = 139591680
sun.gc.generation.0.space.0.name = eden
sun.gc.generation.0.space.0.used = 6954760
sun.gc.generation.0.space.1.capacity = 2162688
sun.gc.generation.0.space.1.initCapacity = 0
sun.gc.generation.0.space.1.maxCapacity = 17432576
sun.gc.generation.0.space.1.name = s0
sun.gc.generation.0.space.1.used = 0
sun.gc.generation.0.space.2.capacity = 2162688
sun.gc.generation.0.space.2.initCapacity = 0
sun.gc.generation.0.space.2.maxCapacity = 17432576
sun.gc.generation.0.space.2.name = s1
sun.gc.generation.0.space.2.used = 2162688
sun.gc.generation.0.spaces = 3
sun.gc.generation.0.threads = 8
sun.gc.generation.1.capacity = 65404928
sun.gc.generation.1.maxCapacity = 1973026816
sun.gc.generation.1.minCapacity = 65404928
sun.gc.generation.1.name = old
sun.gc.generation.1.space.0.capacity = 65404928
sun.gc.generation.1.space.0.initCapacity = 65404928
sun.gc.generation.1.space.0.maxCapacity = 1973026816
sun.gc.generation.1.space.0.name = old
sun.gc.generation.1.space.0.used = 25122784
sun.gc.generation.1.spaces = 1
sun.gc.generation.2.capacity = 33161216
sun.gc.generation.2.maxCapacity = 268435456
sun.gc.generation.2.minCapacity = 21757952
sun.gc.generation.2.name = perm
sun.gc.generation.2.space.0.capacity = 33161216
sun.gc.generation.2.space.0.initCapacity = 21757952
sun.gc.generation.2.space.0.maxCapacity = 268435456
sun.gc.generation.2.space.0.name = perm
sun.gc.generation.2.space.0.used = 32439520
sun.gc.generation.2.spaces = 1
sun.gc.lastCause = unknown GCCause
sun.gc.policy.collectors = 2
sun.gc.policy.desiredSurvivorSize = 1081344
sun.gc.policy.generations = 3
sun.gc.policy.maxTenuringThreshold = 3
sun.gc.policy.name = ParNew:CMS
sun.gc.policy.tenuringThreshold = 1
sun.gc.tlab.alloc = 756560
sun.gc.tlab.allocThreads = 1
sun.gc.tlab.fastWaste = 0
sun.gc.tlab.fills = 28
sun.gc.tlab.gcWaste = 0
sun.gc.tlab.maxFastWaste = 0
sun.gc.tlab.maxFills = 28
sun.gc.tlab.maxGcWaste = 0
sun.gc.tlab.maxSlowAlloc = 4
sun.gc.tlab.maxSlowWaste = 116
sun.gc.tlab.slowAlloc = 4
sun.gc.tlab.slowWaste = 116

*/
