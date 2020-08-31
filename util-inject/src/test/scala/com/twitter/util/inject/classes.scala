package com.twitter.inject

import com.google.inject.{Provider, TypeLiteral}
import javax.inject.{Inject, Named}

class SomeClient {
  protected val underlying: String = "Hello, Alice."
  def get: String = underlying
}

trait Augmentation { self: SomeClient =>
  override def get: String = s"${self.underlying} Welcome to Wonderland."
  def onlyHere: String = "Only on augmented classes."
}

class Baz @Inject() (client: SomeClient with Augmentation) {
  client.onlyHere
}
trait MyServiceInterface {

  def add2(int: Int): Int
}

class MyServiceImpl extends MyServiceInterface {
  override def add2(int: Int): Int = {
    int + 2
  }
}
trait MultiService {
  val name: String
}

class OneMultiService extends MultiService {
  override val name = "one"
}

class TwoMultiService extends MultiService {
  override val name = "two"
}
object classes {
  object Outer {
    trait X
    class Y extends X
    trait Gen[T] {
      def get: T
    }

    class C extends Gen[String] {
      def get = "String"
    }
  }

  trait X
  class Y extends X

  class YProvider extends Provider[Y] {
    def get = new Y
  }

  trait Gen[T] {
    def get: T
  }

  class TypeProvider[T] @Inject() (typ: TypeLiteral[T]) extends Provider[String] {
    def get = typ.toString
  }

  class Z extends Gen[String] {
    def get = "String"
  }

  class GenStringProvider extends Provider[Gen[String]] {
    def get = new Z
  }

  class ZProvider extends Provider[Z] {
    def get = new Z
  }

  trait Foo {
    def foo(): String
  }

  class FooProviderWithJavax extends javax.inject.Provider[Foo] {
    def get(): Foo = new Foo {
      def foo() = "foo"
    }
  }

  case class TwoStrings @Inject() (@Named("first") first: String, @Named("second") second: String)

  trait D

  sealed trait SealedTrait

  final case object FinalSealedTrait extends SealedTrait

  case class SealedTraitContainer[T <: SealedTrait](inner: T)

  class SealedTraitContainerFinalSealedTraitProvider
      extends javax.inject.Provider[SealedTraitContainer[FinalSealedTrait.type]] {
    def get(): SealedTraitContainer[FinalSealedTrait.type] = SealedTraitContainer(FinalSealedTrait)
  }
}
