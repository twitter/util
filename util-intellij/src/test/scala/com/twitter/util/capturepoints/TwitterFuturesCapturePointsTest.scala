package com.twitter.util.capturepoints

import java.lang.reflect.Method
import org.scalatest.FunSuite
import scala.xml.XML

/**
 * This test suite is intended to test capture points defined in [TwitterFuturesCapturePoints.xml].
 * The test name corresponds to the capture point being tested and is named in the format
 * [Class.method]. Because of the limitations of reflection, we only test for [Capture class
 * and method] and [Insert class and method]. This means that the [key-expression] is not
 * tested for. As such, these tests will only fail if the class name or method for which a
 * capture point depends on has either changed or doesn't exist anymore.
 *
 * In the case that the [Insert class-name] or [Capture class-name] has been changed or
 * doesn't exist, this file will fail to compile.
 */
class TwitterFuturesCapturePointsTest extends FunSuite {

  /**
   *  extract [methodName] from `Class[_]`
   */
  private[this] def findMethod(m: Class[_], methodName: String = "apply"): Array[Method] = {
    m.getDeclaredMethods.filter(_.getName == methodName)
  }

  /**
   * test that [methodName] is a member of `ClassOf[com.twitter.util.className]`
   */
  private[this] def testMethod(
    methodType: String,
    className: String,
    methodName: String = "apply"
  ): Unit = {
    val m = Class.forName(className)
    val methodArray = findMethod(m, methodName)
    assert(
      methodArray.nonEmpty,
      s": couldn't find $methodType method ${m.getTypeName}.$methodName."
    )
  }

  val capturePointsXML =
    XML.load(getClass.getResourceAsStream("/util-intellij/TwitterFuturesCapturePoints.xml"))

  /**
   * perform "capture method" and "insert method" tests for each capture point
   */
  (capturePointsXML \ "capture-point").foreach { capturePoint =>
    val attrMap = capturePoint.attributes.asAttrMap
    val insertMethodName = attrMap("insert-method-name")
    val insertClassName = attrMap("insert-class-name")
    val captureMethodName = attrMap("method-name")
    val captureClassName = attrMap("class-name")
    val testName = s"$captureClassName.$captureMethodName"

    test(testName) {
      testMethod("capture", captureClassName, captureMethodName)
      testMethod("insert", insertClassName, insertMethodName)
    }
  }
}
