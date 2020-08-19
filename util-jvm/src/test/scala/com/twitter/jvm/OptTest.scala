package com.twitter.jvm

import java.lang.{Boolean => JBool}
import java.lang.management.ManagementFactory
import javax.management.ObjectName
import org.scalatest.funsuite.AnyFunSuite

class OptTest extends AnyFunSuite {
  test("Opts") {
    if (System.getProperty("java.vm.name").contains("HotSpot")) {
      val DiagnosticName =
        ObjectName.getInstance("com.sun.management:type=HotSpotDiagnostic")

      val originalValue: String = Opt("MaxHeapFreeRatio").getOrElse("100")

      val option = ManagementFactory
        .getPlatformMBeanServer()
        .invoke(DiagnosticName, "getVMOption", Array("MaxHeapFreeRatio"), Array("java.lang.String"))

      val writable = option.getClass match {
        case clazz: Class[_] if clazz.getCanonicalName == "com.sun.management.VMOption" =>
          clazz.getMethod("isWriteable").invoke(option) match {
            case bool: JBool => bool: Boolean
            case _ => fail()
          }
        case _ => false
      }

      if (writable) {
        ManagementFactory
          .getPlatformMBeanServer()
          .invoke(
            DiagnosticName,
            "setVMOption",
            Array("MaxHeapFreeRatio", "99"),
            Array("java.lang.String", "java.lang.String")
          )

        assert(Opt("MaxHeapFreeRatio") == Some("99"))

        ManagementFactory
          .getPlatformMBeanServer()
          .invoke(
            DiagnosticName,
            "setVMOption",
            Array("MaxHeapFreeRatio", originalValue),
            Array("java.lang.String", "java.lang.String")
          )

        assert(Opt("MaxHeapFreeRatio") == Some(originalValue))

        assert(Opt("NonexistentOption") == None)
      }
    }
  }
}
