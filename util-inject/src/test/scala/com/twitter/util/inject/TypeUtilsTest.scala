package com.twitter.util.inject

import org.scalacheck.Arbitrary
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scala.reflect.runtime.universe._

trait A
trait B

class TypeUtilsTest extends AnyFunSuite with ScalaCheckDrivenPropertyChecks {

  test("asManifest handles AnyVal/Any/Null/Nothing") {
    forAll(Arbitrary.arbAnyVal.arbitrary) { anyVal =>
      assert(manifestedTypesEquals(anyVal))
    }
    assert(TypeUtils.asManifest[AnyVal] == manifest[AnyVal])
    assert(TypeUtils.asManifest[Any] == manifest[Any])
    assert(TypeUtils.asManifest[Null] == manifest[Null])
    assert(TypeUtils.asManifest[Nothing] == manifest[Nothing])
  }

  test("asManifest handles NoClassDefFound exceptions") {
    val t = typeTag[A with B]
    intercept[NoClassDefFoundError] {
      t.mirror.runtimeClass(t.tpe)
    }
    assert(TypeUtils.asManifest[A with B] == manifest[Any])
  }

  test("asTypeTag handles classes") {
    typeTagEquals(classOf[String], TypeUtils.asTypeTag(classOf[String]))
    typeTagEquals(classOf[AnyVal], TypeUtils.asTypeTag(classOf[AnyVal]))
    typeTagEquals(classOf[Any], TypeUtils.asTypeTag(classOf[Any]))
    typeTagEquals(classOf[Null], TypeUtils.asTypeTag(classOf[Null]))
    typeTagEquals(classOf[Nothing], TypeUtils.asTypeTag(classOf[Nothing]))
  }

  def manifestedTypesEquals[T: TypeTag: Manifest](a: T): Boolean = {
    TypeUtils.asManifest[T] == manifest[T]
  }

  def typeTagEquals[T](clazz: Class[T], typeTag: TypeTag[T]): Boolean = {
    clazz.isAssignableFrom(typeTag.mirror.runtimeClass(typeTag.tpe.typeSymbol.asClass))
  }
}
