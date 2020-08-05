package com.twitter.util.inject

import com.google.inject._
import com.google.inject.name.Names
import com.twitter.util.inject.classes.{TypeProvider, _}
import com.twitter.util.inject.module.TestModule
import javax.inject.{Inject, Named, Singleton}
import net.codingwell.scalaguice.ScalaModule
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

object SeqAbstractModule extends AbstractModule with ScalaModule {
  @Singleton
  @Provides
  def provideSeq: Seq[Array[Byte]] =
    Seq.empty[Array[Byte]]
}

trait MyServiceInterface {

  def add2(int: Int): Int
}

class MyServiceImpl extends MyServiceInterface {
  override def add2(int: Int): Int = {
    int + 2
  }
}

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

object InjectorTest {
  type AugmentedSomeClient = SomeClient with Augmentation
}

@RunWith(classOf[JUnitRunner])
class InjectorTest extends AnyFunSuite with Matchers {
  import InjectorTest._

  private case class W[T](t: T)
  // Users should prefer the `c.t.inject.app.TestInjector` but it is not in scope here.
  private val injector: Injector = Injector(Guice.createInjector(new TestModule {}))

  test("byte array generics") {
    import net.codingwell.scalaguice.InjectorExtensions._
    val i = Guice.createInjector(SeqAbstractModule)

    i.instance[Seq[Array[Byte]]] should equal(Seq.empty[Array[Byte]])
  }

  test("can't bind class with trait and class") {
    intercept[CreationException] {
      Guice.createInjector(
        new AbstractModule with ScalaModule {
          @Provides
          @Singleton
          def provideSomeClient: SomeClient with Augmentation = {
            new SomeClient with Augmentation
          }
        },
        new AbstractModule with ScalaModule {
          @Provides
          @Singleton
          def provideSomeClient: SomeClient = {
            new SomeClient
          }
        }
      )
    }
  }

  test("class with trait type") {
    import net.codingwell.scalaguice.InjectorExtensions._
    val i = Guice.createInjector(
      Stage.PRODUCTION,
      new AbstractModule with ScalaModule {
        @Provides
        @Singleton
        def provideSomeClient: SomeClient with Augmentation = {
          new SomeClient with Augmentation
        }
      })

    val r1 = i.instance[SomeClient with Augmentation]
    r1.get should equal("Hello, Alice. Welcome to Wonderland.")

    val r2 = i.instance[AugmentedSomeClient]
    r2.get should equal("Hello, Alice. Welcome to Wonderland.")
  }

  test("class only type") {
    import net.codingwell.scalaguice.InjectorExtensions._
    val i = Guice.createInjector(
      Stage.PRODUCTION,
      new AbstractModule with ScalaModule {
        @Provides
        @Singleton
        def provideSomeClient: SomeClient = {
          new SomeClient
        }
      })

    // fails since only SomeClient is bound
    val e = intercept[ProvisionException] {
      i.instance[Baz]
    }
    e.getCause.isInstanceOf[ClassCastException] should be(true)
  }

  test("get additional instances from injector") {
    injector.instance[String](Names.named("str1")) should be("string1")
    injector.instance[String, TestBindingAnnotation] should be("prod string")
    injector.instance[String](Names.named("name")) should equal("Steve")
  }

  test("allow binding target type using a type parameter") {
    val module = new TestModule {
      override protected def configure(): Unit = {
        bind[X].to[Y]
      }
    }

    Injector(Guice.createInjector(module)).instance[X]
  }

  test("allow binding target provider type using a type parameter") {
    val module = new TestModule {
      override protected def configure(): Unit = {
        bind[X].toProvider[YProvider]
      }
    }

    Injector(Guice.createInjector(module)).instance[X]
  }

  test("allow binding to provider of subtype using type parameter") {
    val module = new TestModule {
      override protected def configure(): Unit = {
        bind[Gen[String]].toProvider[ZProvider]
      }
    }

    Injector(Guice.createInjector(module)).instance(new Key[Gen[String]] {})
  }

  test("allow binding to provider with injected type literal") {
    val module = new TestModule {
      override protected def configure(): Unit = {
        bind[String].toProvider[TypeProvider[B]]
      }
    }

    Injector(Guice.createInjector(module)).instance(new Key[String] {})
  }

  test("allow binding in scope using a type parameter") {
    val module = new TestModule {
      override protected def configure(): Unit = {
        bind[X].to[Y].in[Singleton]
      }
    }

    Injector(Guice.createInjector(module)).instance[X]
  }

  test("allow binding a container with a generic singleton type") {
    val module = new TestModule {
      override protected def configure(): Unit = {
        bind[SealedTraitContainer[FinalSealedTrait.type]]
          .toProvider[SealedTraitContainerFinalSealedTraitProvider]
      }
    }

    Injector(Guice.createInjector(module))
      .instance(new Key[SealedTraitContainer[FinalSealedTrait.type]] {})
  }

  test("allow binding with annotation using a type parameter") {
    val module = new TestModule {
      override protected def configure(): Unit = {
        bind[X].annotatedWith[Named].to[Y]
      }
    }

    Injector(Guice.createInjector(module)).instance(Key.get(classOf[X], classOf[Named]))
  }

  test("allow use provider form javax.inject.Provider") {
    val module = new TestModule {
      override def configure(): Unit = {
        bind[Foo].toProvider[FooProviderWithJavax]
      }
    }

    Injector(Guice.createInjector(module)).instance[Foo]
  }

  test("provide helpful error when bound to self") {
    val module = new TestModule {
      override def configure(): Unit = {
        bind[X].to[X]
      }
    }
    val e = intercept[CreationException] {
      Injector(Guice.createInjector(module)).instance[X]
    }
    val messages = e.getErrorMessages
    messages.size should equal(1)
    e.getStackTrace.exists(_.getClassName == this.getClass.getName)
    val sources = messages.iterator.next.getSource
    sources.contains(this.getClass.getName) should be(true)
  }
}
