package com.twitter.util.jackson.caseclass

import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TypesTest extends AnyFunSuite with Matchers {

  test("types#handles null") {
    val clazz = Types.wrapperType(null)
    clazz != null should be(true)
    clazz.getSimpleName should equal(classOf[Null].getSimpleName)
  }

  test("types#all types") {
    classOf[Null].isAssignableFrom(Types.wrapperType(null)) should be(true)

    classOf[java.lang.Byte].isAssignableFrom(Types.wrapperType(java.lang.Byte.TYPE)) should be(true)
    classOf[java.lang.Short]
      .isAssignableFrom(Types.wrapperType(java.lang.Short.TYPE)) should be(true)
    classOf[java.lang.Character]
      .isAssignableFrom(Types.wrapperType(java.lang.Character.TYPE)) should be(true)
    classOf[java.lang.Integer]
      .isAssignableFrom(Types.wrapperType(java.lang.Integer.TYPE)) should be(true)
    classOf[java.lang.Long].isAssignableFrom(Types.wrapperType(java.lang.Long.TYPE)) should be(true)
    classOf[java.lang.Double]
      .isAssignableFrom(Types.wrapperType(java.lang.Double.TYPE)) should be(true)
    classOf[java.lang.Boolean]
      .isAssignableFrom(Types.wrapperType(java.lang.Boolean.TYPE)) should be(true)
    classOf[java.lang.Void].isAssignableFrom(Types.wrapperType(java.lang.Void.TYPE)) should be(true)
  }
}
