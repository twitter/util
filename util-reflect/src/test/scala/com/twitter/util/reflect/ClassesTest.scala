package com.twitter.util.reflect

import com.twitter.util.reflect.testclasses.has_underscore.ClassB
import com.twitter.util.reflect.testclasses.number_1.FooNumber
import com.twitter.util.reflect.testclasses.okNaming.Ok
import com.twitter.util.reflect.testclasses.ver2_3.{Ext, Response, This$Breaks$In$ManyWays}
import com.twitter.util.reflect.testclasses.{ClassA, Request}
import com.twitter.util.reflect.DoEverything.{DoEverything$Client, ServicePerEndpoint}
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ClassesTest extends AnyFunSuite with Matchers {

  test("Classes#simpleName") {
    // these cause class.getSimpleName to blow up, ensure we don't
    Classes.simpleName(classOf[Ext]) should equal("Ext")
    try {
      classOf[Ext].getSimpleName should equal("Ext")
    } catch {
      case _: InternalError =>
      // do nothing -- fails in JDK8 but not JDK11
    }
    Classes.simpleName(classOf[Response]) should equal("Response")
    try {
      classOf[Response].getSimpleName should equal("Response")
    } catch {
      case _: InternalError =>
      // do nothing -- fails in JDK8 but not JDK11
    }

    // show we don't blow up
    Classes.simpleName(classOf[Ok]) should equal("Ok")
    try {
      classOf[Ok].getSimpleName should equal("Ok")
    } catch {
      case _: InternalError =>
      // do nothing -- fails in JDK8 but not JDK11
    }

    // ensure we don't blow up
    Classes.simpleName(classOf[ClassB]) should equal("ClassB")
    try {
      classOf[ClassB].getSimpleName should equal("ClassB")
    } catch {
      case _: InternalError =>
      // do nothing -- fails in JDK8 but not JDK11
    }

    // this causes class.getSimpleName to blow up, ensure we don't
    Classes.simpleName(classOf[FooNumber]) should equal("FooNumber")
    try {
      classOf[FooNumber].getSimpleName should equal("FooNumber")
    } catch {
      case _: InternalError =>
      // do nothing -- fails in JDK8 but not JDK11
    }

    Classes.simpleName(classOf[Fungible[_]]) should equal("Fungible")
    Classes.simpleName(classOf[BarService]) should equal("BarService")
    Classes.simpleName(classOf[ToBarService]) should equal("ToBarService")
    Classes.simpleName(classOf[java.lang.Object]) should equal("Object")
    Classes.simpleName(classOf[GeneratedFooService]) should equal("GeneratedFooService")
    Classes.simpleName(classOf[com.twitter.util.reflect.DoEverything[_]]) should equal(
      "DoEverything")
    Classes.simpleName(com.twitter.util.reflect.DoEverything.getClass) should equal("DoEverything$")
    Classes.simpleName(classOf[DoEverything.ServicePerEndpoint]) should equal("ServicePerEndpoint")
    Classes.simpleName(classOf[ServicePerEndpoint]) should equal("ServicePerEndpoint")
    Classes.simpleName(classOf[ClassA]) should equal("ClassA")
    Classes.simpleName(classOf[ClassA]) should equal(classOf[ClassA].getSimpleName)
    Classes.simpleName(classOf[Request]) should equal("Request")
    Classes.simpleName(classOf[Request]) should equal(classOf[Request].getSimpleName)

    Classes.simpleName(classOf[java.lang.String]) should equal("String")
    Classes.simpleName(classOf[java.lang.String]) should equal(
      classOf[java.lang.String].getSimpleName)
    Classes.simpleName(classOf[String]) should equal("String")
    Classes.simpleName(classOf[String]) should equal(classOf[String].getSimpleName)
    Classes.simpleName(classOf[Int]) should equal("int")
    Classes.simpleName(classOf[Int]) should equal(classOf[Int].getSimpleName)

    Classes.simpleName(classOf[DoEverything.DoEverything$Client]) should equal(
      "DoEverything$Client")
    Classes.simpleName(classOf[DoEverything$Client]) should equal("DoEverything$Client")
    Classes.simpleName(classOf[DoEverything$Client]) should equal(
      classOf[DoEverything$Client].getSimpleName)

    val manyWays = Classes.simpleName(classOf[This$Breaks$In$ManyWays])
    (manyWays == "ManyWays" /* JDK8 */ ||
    manyWays == "This$Breaks$In$ManyWays" /* JDK11 */ ) should be(true)
    try {
      classOf[This$Breaks$In$ManyWays].getSimpleName should equal("This$Breaks$In$ManyWays")
    } catch {
      case _: InternalError =>
      // do nothing -- fails in JDK8 but not JDK11
    }
  }
}
