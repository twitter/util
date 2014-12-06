package com.twitter.jvm

import java.lang.management.ManagementFactory
import javax.management.ObjectName
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class OptsTest extends FunSuite  {
  test("Opts") {
    if (System.getProperty("java.vm.name").contains("HotSpot")) {
      val DiagnosticName =
        ObjectName.getInstance("com.sun.management:type=HotSpotDiagnostic")

      val originalValue: String = Opt("MaxHeapFreeRatio").getOrElse("100")

      ManagementFactory.getPlatformMBeanServer().invoke(
        DiagnosticName, "setVMOption",
        Array("MaxHeapFreeRatio", "99"),
        Array("java.lang.String", "java.lang.String"))

      assert(Opt("MaxHeapFreeRatio") === Some("99"))

      ManagementFactory.getPlatformMBeanServer().invoke(
        DiagnosticName, "setVMOption",
        Array("MaxHeapFreeRatio", originalValue),
        Array("java.lang.String", "java.lang.String"))

      assert(Opt("MaxHeapFreeRatio") === Some(originalValue))

      assert(Opt("NonexistentOption") === None)
    }
  }
}
