package com.twitter.jvm

import com.twitter.conversions.StringOps._
import com.twitter.finagle.stats.MetricBuilder.GaugeType
import com.twitter.finagle.stats.Milliseconds
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.stats.exp.Expression
import com.twitter.finagle.stats.exp.ExpressionSchema
import java.lang.management.BufferPoolMXBean
import java.lang.management.ManagementFactory
import scala.collection.mutable
import scala.jdk.CollectionConverters._

object JvmStats {
  // set used for keeping track of jvm gauges (otherwise only weakly referenced)
  private[this] val gauges = mutable.Set.empty[Any]

  @volatile
  private[this] var allocations: Allocations = _

  def register(statsReceiver: StatsReceiver): Unit = {
    val stats = statsReceiver.scope("jvm")

    val mem = ManagementFactory.getMemoryMXBean()

    def heap = mem.getHeapMemoryUsage()
    val heapStats = stats.scope("heap")
    gauges.add(heapStats.addGauge("committed") { heap.getCommitted().toFloat })
    gauges.add(heapStats.addGauge("max") { heap.getMax().toFloat })
    gauges.add(heapStats.addGauge("used") { heap.getUsed().toFloat })

    def nonHeap = mem.getNonHeapMemoryUsage()
    val nonHeapStats = stats.scope("nonheap")
    gauges.add(nonHeapStats.addGauge("committed") { nonHeap.getCommitted().toFloat })
    gauges.add(nonHeapStats.addGauge("max") { nonHeap.getMax().toFloat })
    gauges.add(nonHeapStats.addGauge("used") { nonHeap.getUsed().toFloat })

    val threads = ManagementFactory.getThreadMXBean()
    val threadStats = stats.scope("thread")
    gauges.add(threadStats.addGauge("daemon_count") { threads.getDaemonThreadCount().toFloat })
    gauges.add(threadStats.addGauge("count") { threads.getThreadCount().toFloat })
    gauges.add(threadStats.addGauge("peak_count") { threads.getPeakThreadCount().toFloat })

    val runtime = ManagementFactory.getRuntimeMXBean()
    val uptime = stats.addGauge("uptime") { runtime.getUptime().toFloat }
    gauges.add(uptime)
    gauges.add(stats.addGauge("start_time") { runtime.getStartTime().toFloat })

    val os = ManagementFactory.getOperatingSystemMXBean()
    gauges.add(stats.addGauge("num_cpus") { os.getAvailableProcessors().toFloat })
    os match {
      case unix: com.sun.management.UnixOperatingSystemMXBean =>
        gauges.add(stats.addGauge("fd_count") { unix.getOpenFileDescriptorCount.toFloat })
        gauges.add(stats.addGauge("fd_limit") { unix.getMaxFileDescriptorCount.toFloat })
      case _ =>
    }

    val compilerStats = stats.scope("compiler");
    gauges.add(compilerStats.addGauge("graal") {
      System.getProperty("jvmci.Compiler") match {
        case "graal" => 1
        case _ => 0
      }
    })

    ManagementFactory.getCompilationMXBean() match {
      case null =>
      case compilation =>
        val compilationStats = stats.scope("compilation")
        gauges.add(compilationStats.addGauge("time_msec") {
          compilation.getTotalCompilationTime().toFloat
        })
    }

    val classes = ManagementFactory.getClassLoadingMXBean()
    val classLoadingStats = stats.scope("classes")
    gauges.add(classLoadingStats.addGauge("total_loaded") {
      classes.getTotalLoadedClassCount().toFloat
    })
    gauges.add(classLoadingStats.addGauge("total_unloaded") {
      classes.getUnloadedClassCount().toFloat
    })
    gauges.add(
      classLoadingStats.addGauge("current_loaded") { classes.getLoadedClassCount().toFloat }
    )

    val memPool = ManagementFactory.getMemoryPoolMXBeans.asScala
    val memStats = stats.scope("mem")
    val currentMem = memStats.scope("current")
    val postGCStats = memStats.scope("postGC")
    memPool.foreach { pool =>
      val name = pool.getName.regexSub("""[^\w]""".r) { m => "_" }
      if (pool.getCollectionUsage != null) {
        def usage = pool.getCollectionUsage // this is a snapshot, we can't reuse the value
        gauges.add(postGCStats.addGauge(name, "used") { usage.getUsed.toFloat })
      }
      if (pool.getUsage != null) {
        def usage = pool.getUsage // this is a snapshot, we can't reuse the value
        gauges.add(currentMem.addGauge(name, "used") { usage.getUsed.toFloat })
        gauges.add(currentMem.addGauge(name, "max") { usage.getMax.toFloat })
      }
    }
    gauges.add(postGCStats.addGauge("used") {
      memPool.flatMap(p => Option(p.getCollectionUsage)).map(_.getUsed).sum.toFloat
    })
    gauges.add(currentMem.addGauge("used") {
      memPool.flatMap(p => Option(p.getUsage)).map(_.getUsed).sum.toFloat
    })

    // the Hotspot JVM exposes the full size that the metaspace can grow to
    // which differs from the value exposed by `MemoryUsage.getMax` from above
    val jvm = Jvm()
    jvm.metaspaceUsage.foreach { usage =>
      gauges.add(memStats.scope("metaspace").addGauge("max_capacity") {
        usage.maxCapacity.inBytes.toFloat
      })
    }

    val spStats = stats.scope("safepoint")
    gauges.add(spStats.addGauge("sync_time_millis") { jvm.safepoint.syncTimeMillis.toFloat })
    gauges.add(spStats.addGauge("total_time_millis") { jvm.safepoint.totalTimeMillis.toFloat })
    gauges.add(spStats.addGauge("count") { jvm.safepoint.count.toFloat })

    ManagementFactory.getPlatformMXBeans(classOf[BufferPoolMXBean]) match {
      case null =>
      case jBufferPool =>
        val bufferPoolStats = memStats.scope("buffer")
        jBufferPool.asScala.foreach { bp =>
          val name = bp.getName
          gauges.add(bufferPoolStats.addGauge(name, "count") { bp.getCount.toFloat })
          gauges.add(bufferPoolStats.addGauge(name, "used") { bp.getMemoryUsed.toFloat })
          gauges.add(bufferPoolStats.addGauge(name, "max") { bp.getTotalCapacity.toFloat })
        }
    }

    val gcPool = ManagementFactory.getGarbageCollectorMXBeans.asScala
    val gcStats = stats.scope("gc")
    gcPool.foreach { gc =>
      val name = gc.getName.regexSub("""[^\w]""".r) { m => "_" }
      val poolCycles =
        gcStats.metricBuilder(GaugeType).withCounterishGauge.gauge(name, "cycles") {
          gc.getCollectionCount.toFloat
        }
      val poolMsec = gcStats.metricBuilder(GaugeType).withCounterishGauge.gauge(name, "msec") {
        gc.getCollectionTime.toFloat
      }

      ExpressionSchema(s"gc_cycles", Expression(poolCycles.metadata))
        .withLabel(ExpressionSchema.Role, "jvm")
        .withLabel("gc_pool", name)
        .withDescription(
          s"The total number of collections that have occurred for the $name gc pool")
        .build()
      ExpressionSchema(s"gc_latency", Expression(poolMsec.metadata))
        .withLabel(ExpressionSchema.Role, "jvm")
        .withLabel("gc_pool", name)
        .withUnit(Milliseconds)
        .withDescription(s"The total elapsed time spent doing collections for the $name gc pool")
        .build()

      gauges.add(poolCycles)
      gauges.add(poolMsec)
    }

    // note, these could be -1 if the collector doesn't have support for it.
    val cycles = gcStats.metricBuilder(GaugeType).withCounterishGauge.gauge("cycles") {
      gcPool.map(_.getCollectionCount).filter(_ > 0).sum.toFloat
    }
    val msec = gcStats.metricBuilder(GaugeType).withCounterishGauge.gauge("msec") {
      gcPool.map(_.getCollectionTime).filter(_ > 0).sum.toFloat
    }

    ExpressionSchema("jvm_uptime", Expression(uptime.metadata))
      .withLabel(ExpressionSchema.Role, "jvm")
      .withUnit(Milliseconds)
      .withDescription("The uptime of the JVM in MS")
      .build()
    ExpressionSchema("gc_cycles", Expression(cycles.metadata))
      .withLabel(ExpressionSchema.Role, "jvm")
      .withDescription("The total number of collections that have occurred")
      .build()
    ExpressionSchema("gc_latency", Expression(msec.metadata))
      .withLabel(ExpressionSchema.Role, "jvm")
      .withUnit(Milliseconds)
      .withDescription("The total elapsed time spent doing collections")
      .build()

    gauges.add(cycles)
    gauges.add(msec)
    allocations = new Allocations(gcStats)
    allocations.start()
    if (allocations.trackingEden) {
      val allocationStats = memStats.scope("allocations")
      val eden = allocationStats.scope("eden")
      gauges.add(eden.addGauge("bytes") { allocations.eden.toFloat })
    }

    // return ms from ns while retaining precision
    gauges.add(stats.addGauge("application_time_millis") { jvm.applicationTime.toFloat / 1000000 })
    gauges.add(stats.addGauge("tenuring_threshold") { jvm.tenuringThreshold.toFloat })
  }

}
