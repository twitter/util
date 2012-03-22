package com.twitter.jvm

import java.lang.management.ManagementFactory
import javax.management.openmbean.CompositeDataSupport
import javax.management.{ObjectName, RuntimeMBeanException}

/**
 * Retrieve the named JVM option.
 */
object Opt {
  private[this] val DiagnosticName =
    ObjectName.getInstance("com.sun.management:type=HotSpotDiagnostic")

  def apply(name: String): Option[String] = try Some {
    val o = ManagementFactory.getPlatformMBeanServer().invoke(
      DiagnosticName, "getVMOption",
      Array(name), Array("java.lang.String"))
    o.asInstanceOf[CompositeDataSupport].get("value").asInstanceOf[String]
  } catch {
    case _: IllegalArgumentException =>
      None
    case rbe: RuntimeMBeanException
    if rbe.getCause.isInstanceOf[IllegalArgumentException] =>
      None
  }
}
