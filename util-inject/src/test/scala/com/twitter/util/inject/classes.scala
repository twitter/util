package com.twitter.util.inject

import com.google.inject.{Provider, TypeLiteral}
import javax.inject.{Inject, Named}

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
