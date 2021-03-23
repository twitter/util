package com.twitter.util.reflect

import com.twitter.util.{Awaitable, FuturePool}
import com.twitter.util.reflect.testclasses._
import com.twitter.util.reflect.testclasses.has_underscore.ClassB
import com.twitter.util.reflect.testclasses.number_1.FooNumber
import com.twitter.util.reflect.testclasses.okNaming.Ok
import com.twitter.util.reflect.testclasses.ver2_3.{Ext, Response}
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner
import scala.reflect.runtime.universe._

@RunWith(classOf[JUnitRunner])
class TypesTest extends AnyFunSuite with Matchers {

  test("Types#asTypeTag handles classes") {
    typeTagEquals(classOf[String], Types.asTypeTag(classOf[String]))
    typeTagEquals(classOf[AnyRef], Types.asTypeTag(classOf[AnyRef]))
    typeTagEquals(classOf[AnyVal], Types.asTypeTag(classOf[AnyVal]))
    typeTagEquals(classOf[Unit], Types.asTypeTag(classOf[Unit]))
    typeTagEquals(classOf[Byte], Types.asTypeTag(classOf[Byte]))
    typeTagEquals(classOf[Short], Types.asTypeTag(classOf[Short]))
    typeTagEquals(classOf[Char], Types.asTypeTag(classOf[Char]))
    typeTagEquals(classOf[Int], Types.asTypeTag(classOf[Int]))
    typeTagEquals(classOf[Long], Types.asTypeTag(classOf[Long]))
    typeTagEquals(classOf[Float], Types.asTypeTag(classOf[Float]))
    typeTagEquals(classOf[Double], Types.asTypeTag(classOf[Double]))
    typeTagEquals(classOf[Boolean], Types.asTypeTag(classOf[Boolean]))
    typeTagEquals(classOf[java.lang.Object], Types.asTypeTag(classOf[java.lang.Object]))
    typeTagEquals(classOf[Any], Types.asTypeTag(classOf[Any]))
    typeTagEquals(classOf[Null], Types.asTypeTag(classOf[Null]))
    typeTagEquals(classOf[Nothing], Types.asTypeTag(classOf[Nothing]))

    // generics
    typeTagEquals(classOf[Baz[_, _]], Types.asTypeTag(classOf[Baz[_, _]]))
    typeTagEquals(classOf[Foo[_]], Types.asTypeTag(classOf[Foo[_]]))
    typeTagEquals(classOf[Bez[_, _]], Types.asTypeTag(classOf[Bez[_, _]]))
    typeTagEquals(classOf[TypedTrait[_, _]], Types.asTypeTag(classOf[TypedTrait[_, _]]))
  }

  test("Types#runtimeClass") {
    val tag = typeTag[Baz[_, _]]
    Types.runtimeClass(tag) should equal(classOf[Baz[_, _]])
    Types.runtimeClass[Baz[_, _]] should equal(classOf[Baz[_, _]])

    classOf[AnyRef].isAssignableFrom(Types.runtimeClass[AnyRef])
    classOf[AnyVal].isAssignableFrom(Types.runtimeClass[AnyVal])
    classOf[Unit].isAssignableFrom(Types.runtimeClass[Unit])
    classOf[Byte].isAssignableFrom(Types.runtimeClass[Byte])
    classOf[Short].isAssignableFrom(Types.runtimeClass[Short])
    classOf[Char].isAssignableFrom(Types.runtimeClass[Char])
    classOf[Int].isAssignableFrom(Types.runtimeClass[Int])
    classOf[Long].isAssignableFrom(Types.runtimeClass[Long])
    classOf[Float].isAssignableFrom(Types.runtimeClass[Float])
    classOf[Double].isAssignableFrom(Types.runtimeClass[Double])
    classOf[Boolean].isAssignableFrom(Types.runtimeClass[Boolean])
    classOf[java.lang.Object].isAssignableFrom(Types.runtimeClass[java.lang.Object])
    classOf[Null].isAssignableFrom(Types.runtimeClass[scala.runtime.Null$])
    classOf[Nothing].isAssignableFrom(Types.runtimeClass[scala.runtime.Nothing$])
    // `Any` is special and can result in a ClassNotFoundException
    //Types.runtimeClass[Any]
  }

  test("Types#eq") {
    Types.eq[String](typeTag[String]) should be(true)
    Types.eq[AnyRef](TypeTag.AnyRef) should be(true)
    Types.eq[AnyVal](TypeTag.AnyVal) should be(true)
    Types.eq[Unit](TypeTag.Unit) should be(true)
    Types.eq[Byte](TypeTag.Byte) should be(true)
    Types.eq[Short](TypeTag.Short) should be(true)
    Types.eq[Char](TypeTag.Char) should be(true)
    Types.eq[Int](TypeTag.Int) should be(true)
    Types.eq[Long](TypeTag.Long) should be(true)
    Types.eq[Float](TypeTag.Float) should be(true)
    Types.eq[Double](TypeTag.Double) should be(true)
    Types.eq[Boolean](TypeTag.Boolean) should be(true)
    Types.eq[java.lang.Object](TypeTag.Object) should be(true)
    Types.eq[Any](TypeTag.Any) should be(true)
    Types.eq[Null](TypeTag.Null) should be(true)
    Types.eq[Nothing](TypeTag.Nothing) should be(true)
    // generics
    Types.eq[Baz[_, _]](typeTag[Baz[_, _]]) should be(true)
    Types.eq[Foo[_]](typeTag[Foo[_]]) should be(true)
    Types.eq[Bez[_, _]](typeTag[Bez[_, _]]) should be(true)
    Types.eq[TypedTrait[_, _]](typeTag[TypedTrait[_, _]]) should be(true)

    Types.eq[Int](TypeTag.Float) should be(false)
    Types.eq[String](TypeTag.Int) should be(false)
    Types.eq[java.lang.Object](TypeTag.Int) should be(false)
    Types.eq[String](TypeTag.Object) should be(false)
  }

  test("Types#parameterizedTypeNames") {
    val clazz = classOf[Baz[_, _]]
    Types.parameterizedTypeNames(clazz) should equal(Array("U", "T"))

    val constructors = clazz.getConstructors
    constructors.size should equal(1)
    constructors.foreach { cons =>
      cons.getParameters.length should equal(2)
      val first = cons.getParameters.head
      val second = cons.getParameters.last
      Types.parameterizedTypeNames(first.getParameterizedType) should equal(Array("U"))
      Types.parameterizedTypeNames(second.getParameterizedType) should equal(Array("T"))
    }

    Types.parameterizedTypeNames(classOf[TypedTrait[_, _]]) should equal(Array("A", "B"))
    Types.parameterizedTypeNames(classOf[Map[_, _]]) should equal(Array("K", "V"))

    val clazz2 = classOf[Bez[_, _]]
    Types.parameterizedTypeNames(clazz2) should equal(Array("I", "J"))
    val constructors2 = clazz2.getConstructors
    constructors2.size should equal(1)
    constructors2.foreach { cons =>
      cons.getParameters.length should equal(1)
      val first = cons.getParameters.head
      Types.parameterizedTypeNames(first.getParameterizedType) should equal(Array("I", "J"))
    }
  }

  test("Types#isCaseClass") {
    Types.isCaseClass(classOf[Ext]) should be(true)
    Types.isCaseClass(classOf[Response]) should be(true)

    Types.isCaseClass(classOf[Ok]) should be(true)

    Types.isCaseClass(classOf[ClassB]) should be(true)

    Types.isCaseClass(classOf[FooNumber]) should be(true)

    Types.isCaseClass(classOf[ClassA]) should be(true)
    Types.isCaseClass(classOf[Request]) should be(true)

    Types.isCaseClass(classOf[Awaitable[_]]) should be(false)
    Types.isCaseClass(classOf[FuturePool]) should be(false)

    Types.isCaseClass(classOf[AnyRef]) should be(false)
    Types.isCaseClass(classOf[AnyVal]) should be(false)
    Types.isCaseClass(classOf[Unit]) should be(false)
    Types.isCaseClass(classOf[Byte]) should be(false)
    Types.isCaseClass(classOf[Short]) should be(false)
    Types.isCaseClass(classOf[Char]) should be(false)
    Types.isCaseClass(classOf[Int]) should be(false)
    Types.isCaseClass(classOf[java.lang.Integer]) should be(false)
    Types.isCaseClass(classOf[Float]) should be(false)
    Types.isCaseClass(classOf[Double]) should be(false)
    Types.isCaseClass(classOf[Boolean]) should be(false)
    Types.isCaseClass(classOf[java.lang.Object]) should be(false)
    Types.isCaseClass(classOf[Any]) should be(false)
    Types.isCaseClass(classOf[Null]) should be(false)
    Types.isCaseClass(classOf[Nothing]) should be(false)
    Types.isCaseClass(classOf[String]) should be(false)
    Types.isCaseClass(classOf[java.lang.String]) should be(false)

    Types.isCaseClass(classOf[Seq[_]]) should be(false)
    Types.isCaseClass(classOf[Seq[Ext]]) should be(false)
    Types.isCaseClass(classOf[List[_]]) should be(false)
    Types.isCaseClass(classOf[List[Ext]]) should be(false)
    Types.isCaseClass(classOf[Iterable[_]]) should be(false)
    Types.isCaseClass(classOf[Iterable[Ext]]) should be(false)
    Types.isCaseClass(classOf[Array[_]]) should be(false)
    Types.isCaseClass(classOf[Array[Ext]]) should be(false)
    Types.isCaseClass(classOf[Option[_]]) should be(false)
    Types.isCaseClass(classOf[Option[ClassB]]) should be(false)
    Types.isCaseClass(classOf[Either[_, _]]) should be(false)
    Types.isCaseClass(classOf[Either[Request, Response]]) should be(false)
    Types.isCaseClass(classOf[Map[_, _]]) should be(false)
    Types.isCaseClass(classOf[Map[ClassA, FooNumber]]) should be(false)
    Types.isCaseClass(classOf[Tuple1[_]]) should be(false)
    Types.isCaseClass(classOf[Tuple2[_, _]]) should be(false)
    Types.isCaseClass(classOf[(_, _, _)]) should be(false)
    Types.isCaseClass(classOf[(_, _, _, _)]) should be(false)
    Types.isCaseClass(classOf[(_, _, _, _, _)]) should be(false)
    Types.isCaseClass(classOf[(_, _, _, _, _, _)]) should be(false)
    Types.isCaseClass(classOf[Tuple7[_, _, _, _, _, _, _]]) should be(false)
    Types.isCaseClass(classOf[Tuple8[_, _, _, _, _, _, _, _]]) should be(false)
    Types.isCaseClass(classOf[Tuple9[_, _, _, _, _, _, _, _, _]]) should be(false)
    Types.isCaseClass(classOf[Tuple10[_, _, _, _, _, _, _, _, _, _]]) should be(false)
  }

  test("Types#notCaseClass") {
    Types.notCaseClass(classOf[Ext]) should be(false)
    Types.notCaseClass(classOf[Response]) should be(false)

    Types.notCaseClass(classOf[Ok]) should be(false)

    Types.notCaseClass(classOf[ClassB]) should be(false)

    Types.notCaseClass(classOf[FooNumber]) should be(false)

    Types.notCaseClass(classOf[ClassA]) should be(false)
    Types.notCaseClass(classOf[Request]) should be(false)

    Types.notCaseClass(classOf[Awaitable[_]]) should be(true)
    Types.notCaseClass(classOf[FuturePool]) should be(true)

    Types.notCaseClass(classOf[java.lang.String]) should be(true)
    Types.notCaseClass(classOf[String]) should be(true)
    Types.notCaseClass(classOf[Int]) should be(true)

    Types.notCaseClass(classOf[String]) should be(true)
    Types.notCaseClass(classOf[Boolean]) should be(true)
    Types.notCaseClass(classOf[java.lang.Integer]) should be(true)
    Types.notCaseClass(classOf[Int]) should be(true)
    Types.notCaseClass(classOf[Double]) should be(true)
    Types.notCaseClass(classOf[Float]) should be(true)
    Types.notCaseClass(classOf[Long]) should be(true)
    Types.notCaseClass(classOf[Seq[_]]) should be(true)
    Types.notCaseClass(classOf[Seq[Ext]]) should be(true)
    Types.notCaseClass(classOf[List[_]]) should be(true)
    Types.notCaseClass(classOf[List[Ext]]) should be(true)
    Types.notCaseClass(classOf[Iterable[_]]) should be(true)
    Types.notCaseClass(classOf[Iterable[Ext]]) should be(true)
    Types.notCaseClass(classOf[Array[_]]) should be(true)
    Types.notCaseClass(classOf[Array[Ext]]) should be(true)
    Types.notCaseClass(classOf[Option[_]]) should be(true)
    Types.notCaseClass(classOf[Option[ClassB]]) should be(true)
    Types.notCaseClass(classOf[Either[_, _]]) should be(true)
    Types.notCaseClass(classOf[Either[Request, Response]]) should be(true)
    Types.notCaseClass(classOf[Map[_, _]]) should be(true)
    Types.notCaseClass(classOf[Map[ClassA, FooNumber]]) should be(true)
    Types.notCaseClass(classOf[Tuple1[_]]) should be(true)
    Types.notCaseClass(classOf[Tuple2[_, _]]) should be(true)
    Types.notCaseClass(classOf[(_, _, _)]) should be(true)
    Types.notCaseClass(classOf[(_, _, _, _)]) should be(true)
    Types.notCaseClass(classOf[(_, _, _, _, _)]) should be(true)
    Types.notCaseClass(classOf[(_, _, _, _, _, _)]) should be(true)
    Types.notCaseClass(classOf[Tuple7[_, _, _, _, _, _, _]]) should be(true)
    Types.notCaseClass(classOf[Tuple8[_, _, _, _, _, _, _, _]]) should be(true)
    Types.notCaseClass(classOf[Tuple9[_, _, _, _, _, _, _, _, _]]) should be(true)
    Types.notCaseClass(classOf[Tuple10[_, _, _, _, _, _, _, _, _, _]]) should be(true)
  }

  private[this] def typeTagEquals[T](clazz: Class[T], typeTag: TypeTag[T]): Boolean =
    clazz.isAssignableFrom(typeTag.mirror.runtimeClass(typeTag.tpe.typeSymbol.asClass))
}
