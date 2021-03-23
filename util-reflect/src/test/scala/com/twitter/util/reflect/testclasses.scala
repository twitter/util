package com.twitter.util.reflect

import com.twitter.util.Future

trait Fungible[+T]
trait BarService
trait ToBarService {
  def toBarService: BarService
}

abstract class GeneratedFooService

trait DoEverything[+MM[_]] extends GeneratedFooService

object DoEverything extends GeneratedFooService { self =>

  trait ServicePerEndpoint extends ToBarService with Fungible[ServicePerEndpoint]

  class DoEverything$Client extends DoEverything[Future]
}

object testclasses {
  trait TestTraitA
  trait TestTraitB

  case class Foo[T](data: T)
  case class Baz[U, T](bar: U, foo: Foo[T])
  case class Bez[I, J](m: Map[I, J])

  trait TypedTrait[A, B]

  object ver2_3 {
    // this "package" naming will blow up class.getSimpleName
    // packages with underscore then a number make Java unhappy
    final case class Ext(a: String, b: String)
    case class Response(a: String, b: String)

    class This$Breaks$In$ManyWays
  }

  object okNaming {
    case class Ok(a: String, b: String)
  }

  object has_underscore {
    case class ClassB(a: String, b: String)
  }

  object number_1 {
    // this "package" naming will blow up class.getSimpleName
    // packages with underscore then number make Java unhappy
    final case class FooNumber(a: String)
  }

  final case class ClassA(a: String, b: String)
  case class Request(a: String)

  class Bar

  case class NoConstructor()

  case class CaseClassOneTwo(@Annotation1 one: String, @Annotation2 two: String)
  case class CaseClassOneTwoWithFields(@Annotation1 one: String, @Annotation2 two: String) {
    val city: String = "San Francisco"
    val state: String = "California"
  }
  case class CaseClassOneTwoWithAnnotatedField(@Annotation1 one: String, @Annotation2 two: String) {
    @Annotation3 val three: String = "three"
  }
  case class CaseClassThreeFour(@Annotation3 three: String, @Annotation4 four: String)
  case class CaseClassFive(@Annotation5 five: String)
  case class CaseClassOneTwoThreeFour(
    @Annotation1 one: String,
    @Annotation2 two: String,
    @Annotation3 three: String,
    @Annotation4 four: String)

  case class WithThings(
    @Annotation1 @Thing("thing1") thing1: String,
    @Annotation2 @Thing("thing2") thing2: String)
  case class WithWidgets(
    @Annotation3 @Widget("widget1") widget1: String,
    @Annotation4 @Widget("widget2") widget2: String)

  case class WithSecondaryConstructor(
    @Annotation1 one: Int,
    @Annotation2 two: Int) {
    def this(@Annotation3 three: String, @Annotation4 four: String) {
      this(three.toInt, four.toInt)
    }
  }

  object StaticSecondaryConstructor {
    // NOTE: this is a factory method and not a constructor, so annotations will not be picked up
    def apply(@Annotation3 three: String, @Annotation4 four: String): StaticSecondaryConstructor =
      StaticSecondaryConstructor(three.toInt, four.toInt)
  }
  case class StaticSecondaryConstructor(@Annotation1 one: Int, @Annotation2 two: Int)

  object StaticSecondaryConstructorWithMethodAnnotation {
    def apply(@Annotation3 three: String, @Annotation4 four: String): StaticSecondaryConstructor =
      StaticSecondaryConstructor(three.toInt, four.toInt)
  }
  case class StaticSecondaryConstructorWithMethodAnnotation(
    @Annotation1 one: Int,
    @Annotation2 two: Int) {
    // will not be found as Annotations only scans for declared field annotations, this is a method
    @Widget("widget1") def widget1: String = "this is widget 1 method"
  }

  case class GenericTestCaseClass[T](@Annotation1 one: T)
  case class GenericTestCaseClassWithMultipleArgs[T](@Annotation1 one: T, @Annotation2 two: Int)

  trait AncestorWithAnnotations {
    @Annotation1 def one: String
    @Annotation2 def two: String
  }

  case class CaseClassThreeFourAncestorOneTwo(
    one: String,
    two: String,
    @Annotation3 three: String,
    @Annotation4 four: String)
      extends AncestorWithAnnotations

  case class CaseClassAncestorOneTwo(@Annotation5 five: String) extends AncestorWithAnnotations {
    override val one: String = "one"
    override val two: String = "two"
  }
}
