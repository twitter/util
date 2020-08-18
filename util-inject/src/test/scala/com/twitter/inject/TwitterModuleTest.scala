package com.twitter.inject

import com.google.inject._
import com.google.inject.name.Names
import com.twitter.inject.servicefactory.ComplexServiceFactory
import com.twitter.inject.classes._
import com.twitter.inject.module.{ClassToConvert, SeqAbstractModule, TestTwitterModule}
import java.lang.annotation.Annotation
import javax.inject.{Named, Provider, Singleton}
import net.codingwell.scalaguice.ScalaModule
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import scala.collection.immutable
import scala.util.Random

object TwitterModuleTest {
  type AugmentedSomeClient = SomeClient with Augmentation
}

class TwitterModuleTest
    extends AnyFunSuite
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach {
  import TwitterModuleTest._

  private case class W[T](t: T)
  private val annotation = Names.named(Random.alphanumeric.take(12).mkString.toLowerCase())

  // Users should prefer the `c.t.inject.app.TestInjector` but it is not in scope here.
  private val injector: Injector = Injector(Guice.createInjector(TestTwitterModule))

  override def beforeAll(): Unit = {
    super.beforeAll()
    TestTwitterModule.singletonStartup(injector)
  }

  override def afterAll(): Unit = {
    TestTwitterModule.singletonShutdown(injector)
    super.afterAll()
  }

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

  test("get assisted factory instance from injector") {
    assertServiceFactory(injector.instance[ComplexServiceFactory])
    assertServiceFactory(injector.instance(classOf[ComplexServiceFactory]))
    assertServiceFactory(injector.instance(Key.get(classOf[ComplexServiceFactory])))
  }

  test("get additional instances from injector") {
    injector.instance[String](Names.named("str1")) should be("string1")
    injector.instance[String, TestBindingAnnotation] should be("prod string")
  }

  test("type conversion") {
    injector.instance[ClassToConvert](Names.named("name")) should be(ClassToConvert("Steve"))
  }

  test("allow binding target type using a type parameter") {
    val module = new TwitterModule {
      override protected def configure(): Unit = {
        bind[X].to[Y]
      }
    }

    Injector(Guice.createInjector(module)).instance[X]
  }

  test("allow binding target provider type using a type parameter") {
    val module = new TwitterModule {
      override protected def configure(): Unit = {
        bind[X].toProvider[YProvider]
      }
    }

    Injector(Guice.createInjector(module)).instance[X]
  }

  test("allow binding to provider of subtype using type parameter") {
    val module = new TwitterModule {
      override protected def configure(): Unit = {
        bind[Gen[String]].toProvider[ZProvider]
      }
    }

    Injector(Guice.createInjector(module)).instance(new Key[Gen[String]] {})
  }

  test("allow binding to provider with injected type literal") {
    val module = new TwitterModule {
      override protected def configure(): Unit = {
        bind[String].toProvider[TypeProvider[B]]
      }
    }

    Injector(Guice.createInjector(module)).instance(new Key[String] {})
  }

  test("allow binding in scope using a type parameter") {
    val module = new TwitterModule {
      override protected def configure(): Unit = {
        bind[X].to[Y].in[Singleton]
      }
    }

    Injector(Guice.createInjector(module)).instance[X]
  }

  test("allow binding a container with a generic singleton type") {
    val module = new TwitterModule {
      override protected def configure(): Unit = {
        bind[SealedTraitContainer[FinalSealedTrait.type]]
          .toProvider[SealedTraitContainerFinalSealedTraitProvider]
      }
    }

    Injector(Guice.createInjector(module))
      .instance(new Key[SealedTraitContainer[FinalSealedTrait.type]] {})
  }

  test("allow binding with annotation using a type parameter") {
    val module = new TwitterModule {
      override protected def configure(): Unit = {
        bind[X].annotatedWith[Named].to[Y]
      }
    }

    Injector(Guice.createInjector(module)).instance(Key.get(classOf[X], classOf[Named]))
  }

  test("allow use provider form javax.inject.Provider") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        bind[Foo].toProvider[FooProviderWithJavax]
      }
    }

    Injector(Guice.createInjector(module)).instance[Foo]
  }

  test("provide helpful error when bound to self") {
    val module = new TwitterModule {
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

  test("multibinder#bind empty [T]") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        bindMultiple[String]
      }
    }
    validate(Injector(Guice.createInjector(module)).instance[immutable.Set[String]])
  }

  test("multibinder#bind [T]") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        bindMultiple[String].addBinding.toInstance("A")
        bindMultiple[String].addBinding.toInstance("B")
      }
    }
    validate(Injector(Guice.createInjector(module)).instance[immutable.Set[String]], "A", "B")
  }

  test("multibinder#bind [T, Ann]") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        val multi = bindMultiple[String, TestBindingAnnotation]
        multi.addBinding.toInstance("A")
        multi.addBinding.toInstance("B")
      }
    }
    validate(
      Injector(Guice.createInjector(module)).instance[immutable.Set[String], TestBindingAnnotation],
      "A",
      "B")
  }

  test("multibinder#bind [T, Annotation]") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        val multi = bindMultiple[String](annotation)
        multi.addBinding.toInstance("A")
        multi.addBinding.toInstance("B")
      }
    }
    validate(
      Injector(Guice.createInjector(module)).instance[immutable.Set[String]](annotation),
      "A",
      "B")
  }

  test("multibinder#deduplicate") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        bindMultiple[Symbol].addBinding.toInstance('A)
        bindMultiple[Symbol].addBinding.toInstance('A)
      }
    }
    validate(Injector(Guice.createInjector(module)).instance[immutable.Set[Symbol]], 'A)
  }

  test("multibinder#permit duplicates") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        val multi = bindMultiple[Symbol].permitDuplicates() // silently discards duplicates
        multi.addBinding.toInstance('A)
        multi.addBinding.toInstance('A)
      }
    }
    validate(Injector(Guice.createInjector(module)).instance[immutable.Set[Symbol]], 'A)
  }

  test("multibinder#bind from multiple modules") {
    def newModule(i: Int) = new TwitterModule {
      override def configure(): Unit = {
        val multi = bindMultiple[Int]
        multi.addBinding.toInstance(i)
      }
    }

    validate(
      Injector(Guice.createInjector(newModule(1), newModule(2))).instance[immutable.Set[Int]],
      1,
      2)
  }

  test("multibinder#bind parameterized [T]") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        val mbStrings = bindMultiple[W[String]]
        mbStrings.addBinding.toInstance(W("A"))
        val mbInts = bindMultiple[W[Int]]
        mbInts.addBinding.toInstance(W(1))
      }
    }

    val injector = Injector(Guice.createInjector(module))
    validate(injector.instance[immutable.Set[W[String]]], W("A"))
    validate(injector.instance[immutable.Set[W[Int]]], W(1))
  }

  test("multibinder#bind parameterized [T, Ann]") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        val mbStrings = bindMultiple[W[String], TestBindingAnnotation]
        mbStrings.addBinding.toInstance(W("A"))
        val mbInts = bindMultiple[W[Int], TestBindingAnnotation]
        mbInts.addBinding.toInstance(W(1))
      }
    }

    val injector = Injector(Guice.createInjector(module))
    validate(injector.instance[immutable.Set[W[String]], TestBindingAnnotation], W("A"))
    validate(injector.instance[immutable.Set[W[Int]], TestBindingAnnotation], W(1))
  }

  test("multibinder#bind parameterized [T, Annotation]") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        val mbStrings = bindMultiple[W[String]](annotation)
        mbStrings.addBinding.toInstance(W("A"))
        val mbInts = bindMultiple[W[Int]](annotation)
        mbInts.addBinding.toInstance(W(1))
      }
    }

    val injector = Injector(Guice.createInjector(module))
    validate(injector.instance[immutable.Set[W[String]]](annotation), W("A"))
    validate(injector.instance[immutable.Set[W[Int]]](annotation), W(1))
  }

  test("optionalbinder#bind [T]") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        bindOption[String].setDefault.toInstance("A")
      }
    }

    validate(module)
  }

  test("optionalbinder#bind [T, Ann]") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        bindOption[String, Named].setDefault.toInstance("A")
      }
    }

    validateWithAnn[String, Named](module)
  }

  test("optionalbinder#bind [T, Annotation]") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        val opt = bindOption[String](annotation)
        opt.setDefault.toInstance("A")
      }
    }

    validateWithAnnotation(module, annotation, expected = "A")
  }

  test("optionalbinder#bind optional without default") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        bindOption[String]
      }
    }

    validateAbsent(module)
  }

  test("optionalbinder#override default value") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        val opt = bindOption[String]
        opt.setDefault.toInstance("B")
        opt.setBinding.toInstance("A")
      }
    }

    validate(module)
  }

  test("optionalbinder#override default value with second binder") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        bindOption[String].setDefault.toInstance("B")
        bindOption[String].setBinding.toInstance("A")
      }
    }

    validate(module)
  }

  test("optionalbinder#allow separately annotated bindings for the same [T]") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        val aOpt = bindOption[String](Names.named("A"))
        aOpt.setBinding.toInstance("A")
        val bOpt = bindOption[String](Names.named("B"))
        bOpt.setDefault.toInstance("B")
      }
    }

    validateWithAnnotation(module, Names.named("A"), expected = "A")
    validateWithAnnotation(module, Names.named("B"), expected = "B")
  }

  test("optionalbinder#bind parameterized [T]") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        val sOpt = bindOption[W[String]]
        sOpt.setDefault.toInstance(W("A"))
        val nOpt = bindOption[W[Int]]
        nOpt.setDefault.toInstance(W(1))
      }
    }

    validate(module, expected = W("A"))
    validate(module, expected = W(1))
  }

  test("optionalbinder#bind parameterized [T, Ann]") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        val sOpt = bindOption[W[String], TestBindingAnnotation]
        sOpt.setDefault.toInstance(W("A"))
        val nOpt = bindOption[W[Int], TestBindingAnnotation]
        nOpt.setDefault.toInstance(W(1))
      }
    }

    validateWithAnn[W[String], TestBindingAnnotation](module, expected = W("A"))
    validateWithAnn[W[Int], TestBindingAnnotation](module, expected = W(1))
  }

  test("optionalbinder#bind parameterized [T, Annotation]") {
    val module = new TwitterModule {
      override def configure(): Unit = {
        val sOpt = bindOption[W[String]](annotation)
        sOpt.setDefault.toInstance(W("A"))
        val nOpt = bindOption[W[Int]](annotation)
        nOpt.setDefault.toInstance(W(1))
      }
    }

    validateWithAnnotation(module, annotation, expected = W("A"))
    validateWithAnnotation(module, annotation, expected = W(1))
  }

  private[this] def assertServiceFactory(complexServiceFactory: ComplexServiceFactory): Unit = {
    complexServiceFactory.create("foo").execute should equal("done foo")
  }

  private[this] def validate[T](set: Set[T], expected: T*): Unit = {
    set should have size expected.length
    for (e <- expected) {
      set should contain(e)
    }
  }

  private[this] def validate[T: Manifest](module: Module, expected: T = "A"): Unit = {
    val injector = Injector(Guice.createInjector(module))

    // Check Option
    injector.instance[Option[T]] should contain(expected)
    injector.instance[Option[Provider[T]]].get.get() should equal(expected)
    injector.instance[Option[javax.inject.Provider[T]]].get.get() should equal(expected)

    // Check Option
    injector.instance[Option[T]].get should equal(expected)
    injector.instance[Option[Provider[T]]].get.get() should equal(expected)
    injector.instance[Option[javax.inject.Provider[T]]].get.get() should equal(expected)
  }

  private def validateWithAnn[T: Manifest, Ann <: Annotation: Manifest](
    module: Module,
    expected: T = "A"
  ): Unit = {
    val injector = Injector(Guice.createInjector(module))

    // Check Option
    injector.instance[Option[T], Ann] should contain(expected)
    injector.instance[Option[Provider[T]], Ann].get.get() should equal(expected)
    injector.instance[Option[javax.inject.Provider[T]], Ann].get.get() should equal(expected)

    // Check Option
    injector.instance[Option[T], Ann].get should equal(expected)
    injector.instance[Option[Provider[T]], Ann].get.get() should equal(expected)
    injector.instance[Option[javax.inject.Provider[T]], Ann].get.get() should equal(expected)
  }

  private def validateWithAnnotation[T: Manifest](
    module: Module,
    annotation: Annotation,
    expected: T
  ): Unit = {
    val injector = Injector(Guice.createInjector(module))

    // Check Option
    injector.instance[Option[T]](annotation) should contain(expected)
    injector.instance[Option[Provider[T]]](annotation).get.get() should equal(expected)
    injector.instance[Option[javax.inject.Provider[T]]](annotation).get.get() should equal(expected)

    // Check Option
    injector.instance[Option[T]](annotation).get should equal(expected)
    injector.instance[Option[Provider[T]]](annotation).get.get() should equal(expected)
    injector.instance[Option[javax.inject.Provider[T]]](annotation).get.get() should equal(expected)
  }

  private def validateAbsent[T: Manifest](module: Module, expected: T = "A"): Unit = {
    val injector = Injector(Guice.createInjector(module))

    // Check Option
    injector.instance[Option[T]] should be(None)
    injector.instance[Option[Provider[T]]] should be(None)
    injector.instance[Option[javax.inject.Provider[T]]] should be(None)

    // Check Option
    injector.instance[Option[T]].isDefined should be(false)
    injector.instance[Option[Provider[T]]].isDefined should be(false)
    injector.instance[Option[javax.inject.Provider[T]]].isDefined should be(false)
  }
}
