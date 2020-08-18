package com.twitter.inject

import com.google.inject.assistedinject.FactoryModuleBuilder
import com.google.inject.matcher.{Matcher, Matchers}
import com.google.inject.spi.TypeConverter
import com.google.inject.{Module, _}
import com.twitter.app.Flaggable
import com.twitter.util.logging.Logging
import java.lang.annotation.Annotation
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder, ScalaOptionBinder, typeLiteral}

/**
 * =Overview=
 * A support class for [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/Module.html Module]]
 * implementations which exposes a DSL for binding via type parameters. Extend this class,
 * override the `configure` method and call the `bind` methods, or define custom `@Provides`
 * annotated methods. This class also provides an integration with [[com.twitter.app.Flag]] types
 * which allows for passing external configuration to aid in the creation of bound types. Lastly, it
 * is also possible to define a list of other [[TwitterModule]] instances which this [[TwitterModule]]
 * "depends" on by setting the [[TwitterBaseModule.modules]] (or [[TwitterBaseModule.javaModules]])
 * to a non-empty list. This will ensure that when only this [[TwitterModule]] instance is used to
 * compose an [[Injector]] the "dependent" list of modules will also be installed.
 *
 * =Lifecycle=
 * A [[TwitterModule]] has a lifecycle. Executing this lifecycle is particularly important if the
 * [[TwitterModule]] creates any [[com.twitter.app.Flag]] instances.
 *
 * @note Attempting to bind the same type multiple times with no discriminating
 *       [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/BindingAnnotation.html com.google.inject.BindingAnnotation]]
 *       will result in an exception during injector construction.
 * @example
 * {{{
 *   object MyModule extends TwitterModule {
 *    flag[String](name = "card.gateway", help = "The processing gateway to use for credit cards.")
 *
 *    override protected def configure(): Unit = {
 *      bind[Service].to[ServiceImpl].in[Singleton]
 *      bind[CreditCardPaymentService]
 *      bind[Bar[Foo]].to[FooBarImpl]
 *      bind[PaymentService].to[CreditCardPaymentService]
 *    }
 *
 *    @Singleton
 *    @Provides
 *    def provideCreditCardServiceProcessor(
 *      @Flag("card.gateway") gateway: String
 *    ): CreditCardProcessor = {
 *      new CreditCardProcessor(gateway)
 *    }
 *   }
 * }}}
 * @see [[https://static.javadoc.io/com.google.inject/guice/4.1.0/com/google/inject/AbstractModule.html com.google.inject.AbstractModule]]
 * @see [[https://twitter.github.io/finatra/user-guide/getting-started/modules.html Writing Modules in Finatra]]
 * @define multiBinding
 * An API to bind multiple values separately, only to later inject them as a complete collection.
 * "Multibinding" is intended for use in your application's module:
 * {{{
 *  class SnacksModule extends TwitterModule {
 *    override protected def configure(): Unit = {
 *      bindMultiple[Snack].addBinding.toInstance(new Twix())
 *      bindMultiple[Snack].addBinding.toProvider[SnickersProvider]
 *      bindMultiple[Snack].addBinding.to[Skittles]
 *    }
 *  }
 * }}}
 *
 * With this binding, a `Set[Snack]` can now be injected:
 * {{{
 *   class SnackMachine @Inject()(Set[Snack] snacks)
 * }}}
 *
 * Contributing multibindings from different modules is also supported. For example, it is okay
 * for both `CandyModule` and `ChipsModule` to create their own `Multibinder[Snack]`, and to each
 * contribute bindings to the set of snacks. When that set is injected, it will contain elements
 * from both modules.
 *
 * The set's iteration order is consistent with the binding order. This is convenient when multiple
 * elements are contributed by the same module because that module can order its bindings
 * appropriately. Avoid relying on the iteration order of elements contributed by different modules,
 * since there is no equivalent mechanism to order modules.
 *
 * The set is unmodifiable. Elements can only be added to the set by configuring the multibinder.
 * Elements can never be removed from the set.
 *
 * Elements are resolved at set injection time. If an element is bound to a provider, that
 * provider's get method will be called each time the set is injected (unless the binding is
 * also scoped).
 *
 * Annotations can be used to create different sets of the same element type. Each distinct
 * annotation gets its own independent collection of elements.
 *
 * Elements '''MUST''' be distinct. If multiple bound elements have the same value, set injection will fail.
 *
 * Elements '''MUST''' be non-null. If any set element is null, set injection will fail.
 * @define optionalBinding
 * Calling this method will always supply the bindings: `Option[T]` and `Option[Provider[T]]`. If
 * `setBinding` or `setDefault` are called, it will also bind `T`.
 *
 * `setDefault` is intended for use by frameworks that need a default value. User code can call
 * `setBinding` to override the default.
 *
 * '''Warning:''' even if `setBinding` is called, the default binding will still exist in the
 * object graph. If it is a singleton, it will be instantiated in `Stage.PRODUCTION`.
 *
 * If `setDefault` or `setBinding` are linked to Providers, the Provider may return null. If it
 * does, the Option bindings will be a None. Binding `setBinding` to a Provider that returns
 * null will not cause `OptionalBinder` to fall back to the `setDefault` binding.
 *
 * If neither `setDefault` nor `setBinding` are called, it will try to link to a user-supplied
 * binding of the same type. If no binding exists, the options will be absent. Otherwise, if a
 * user-supplied binding of that type exists, or if `setBinding` or `setDefault` are called, the
 * options will return `Some(T)` if they are bound to a non-null value, otherwise `None`.
 *
 * Values are resolved at injection time. If a value is bound to a provider, that provider's
 * get method will be called each time the option is injected (unless the binding is also
 * scoped, or an option of provider is injected).
 *
 * Annotations are used to create different options of the same key/value type. Each distinct
 * annotation gets its own independent binding. For example:
 * {{{
 *  class FrameworkModule extends TwitterModule {
 *    override protected def configure(): Unit = {
 *      bindOption[Renamer]
 *    }
 *  }
 * }}}
 *
 * With this module, an `Option[Renamer]` can now be injected. With no other bindings, the
 * option will be None. Users can specify bindings in one of two ways:
 *
 * Option 1:
 * {{{
 *  class UserRenamerModule extends TwitterModule {
 *    override protected def configure(): Unit = {
 *      bind[Renamer].to[ReplacingRenamer]
 *    }
 *  }
 * }}}
 * or Option 2:
 * {{{
 *  class UserRenamerModule extends TwitterModule {
 *    override protected def configure(): Unit = {
 *      bindOption[Renamer].setBinding.to[ReplacingRenamer]
 *    }
 *  }
 * }}}
 *
 * With both options, the `Option[Renamer]` will be present and supply the `ReplacingRenamer`.
 *
 * Default values can be supplied using:
 * {{{
 *   class FrameworkModule extends TwitterModule {
 *    override protected def configure(): Unit = {
 *      bindOption[String, LookupUrl].setDefault.toInstance(DefaultLookupUrl)
 *    }
 *  }
 * }}}
 *
 * With the above module, code can inject an `@LookupUrl`-annotated String and it will supply
 * the `DefaultLookupUrl`. A user can change this value by binding:
 * {{{
 *  class UserLookupModule extends TwitterModule {
 *    override protected def configure(): Unit = {
 *      bindOption[String, LookupUrl].setBinding.toInstance(CustomLookupUrl)
 *    }
 *  }
 * }}}
 *
 * which will override the default value.
 *
 * If one module uses `setDefault` the only way to override the default is to use `setBinding`.
 * It is an error for a user to specify the binding without using
 * [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/multibindings/OptionalBinder.html com.google.inject.multibindings.OptionalBinder]]
 * if `setDefault` or `setBinding` are called. For example:
 * {{{
 *   class FrameworkModule extends TwitterModule {
 *    override protected def configure(): Unit = {
 *      bindOption[String, LookupUrl].setDefault.toInstance(DefaultLookupUrl)
 *    }
 *  }
 *  class UserLookupModule extends TwitterModule {
 *    override protected def configure(): Unit = {
 *      bind[String, LookupUrl].toInstance(CustomLookupUrl);
 *    }
 *  }
 * }}}
 *
 * would generate an error, because both the framework and the user are trying to bind `@LookupUrl` String.
 */
abstract class TwitterModule
    extends AbstractModule
    with TwitterBaseModule
    with ScalaModule
    with Logging {

  /**
   * Uses the given [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/Module.html com.google.inject.Module]]
   * to configure more bindings. This is not supported for instances of type [[TwitterModule]] and
   * will throw an [[UnsupportedOperationException]] if attempted on an instance of [[TwitterModule]].
   * This is to properly support the [[TwitterModuleLifecycle]] for [[TwitterModule]] instances.
   *
   * @note [[TwitterModule.install(module: Module)]] can still be used for non-[[TwitterModule]] instances, and is
   * sometimes preferred due to `install` being deferred until after flag parsing occurs.
   */
  @throws[UnsupportedOperationException]
  override protected def install(module: Module): Unit = {
    module match {
      case _: TwitterModule =>
        throw new UnsupportedOperationException(
          "Install not supported for TwitterModules. Please use 'override val modules = Seq(module1, module2, ...)'"
        )
      case _ =>
        super.install(module)
    }
  }

  /* Protected */

  /**
   * Provides for installing and building a factory that combines the caller's arguments with
   * injector-supplied values to construct objects. This is preferable to calling `install`
   * on the [[TwitterModule]] which provides the factory as install is not supported for
   * [[TwitterModule]] types.
   *
   * @tparam T type of the assisted injection factory
   *
   * @see [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/assistedinject/AssistedInject.html com.google.inject.assistedinject.AssistedInject]]
   * @see [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/assistedinject/FactoryModuleBuilder.html com.google.inject.assistedinject.FactoryModuleBuilder]]
   * @see [[https://github.com/google/guice/wiki/AssistedInject Assisted Injection]]
   */
  protected def bindAssistedFactory[T: Manifest](): Unit = {
    super.install(new FactoryModuleBuilder().build(manifest[T].runtimeClass))
  }

  /**
   * Binds a type converter. The injector will use the given converter to
   * convert string constants to matching types as needed.
   *
   * @param converter converts values
   * @tparam T type to match that the converter can handle
   */
  protected def addTypeConverter[T: Manifest](converter: TypeConverter): Unit = {
    convertToTypes(Matchers.only(typeLiteral[T]), converter)
  }

  /**
   * Binds a type converter derived from a [[Flaggable]], making it possible to inject flags of all
   * kinds. The injector will use a provided `Flaggable` to perform type conversion during an
   * injection.
   *
   * For example (in Scala):
   *
   * {{{
   *   addFlagConverter[List[(Int, Int)]] // support injecting flags of type List[(Int, Int)]
   * }}}
   *
   * @see [[addFlagConverter(Matcher,Flaggable)]] for variant that's more suitable for Java.
   */
  protected def addFlagConverter[T <: AnyRef: Manifest](implicit F: Flaggable[T]): Unit = {
    addTypeConverter[T](new TypeConverter {
      def convert(s: String, typeLiteral: TypeLiteral[_]): AnyRef = F.parse(s)
    })
  }

  /**
   * Binds a type converter derived from a [[Flaggable]], making it possible to inject flags of all
   * kinds. The injector will use a provided `Flaggable` to perform type conversion during an
   * injection.
   *
   * For example (in Java):
   *
   * {{{
   *   import java.util.List;
   *   import com.google.inject.TypeLiteral;
   *   import com.google.inject.matcher.Matchers;
   *   import com.twitter.app.Flaggable;
   *
   *   addFlagConverter(
   *     Matchers.only(new TypeLiteral<List<scala.Tuple2<Integer, Integer>>>() {}),
   *     Flaggable.ofJavaList(Flaggable.ofTuple(Flaggable.ofJavaInteger(), Flaggable.ofJavaInteger())
   *   );
   * }}}
   *
   * @see [[addFlagConverter(Manifest,Flaggable]] for a variant that's more suitable for Scala.
   */
  protected def addFlagConverter[T <: AnyRef](
    typeMatcher: Matcher[_ >: TypeLiteral[_]],
    F: Flaggable[T]
  ): Unit = {
    convertToTypes(
      typeMatcher,
      new TypeConverter {
        def convert(s: String, typeLiteral: TypeLiteral[_]): AnyRef = F.parse(s)
      }
    )
  }

  /**
   * Returns a new [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/multibindings/Multibinder.html com.google.inject.multibindings.Multibinder]]
   * that collects instances of type [[T]] in a [[scala.collection.immutable.Set]] that is itself
   * bound with no binding annotation.
   *
   * $multiBinding
   *
   * @see [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/multibindings/Multibinder.html com.google.inject.multibindings.Multibinder]]
   */
  protected def bindMultiple[T: Manifest]: ScalaMultibinder[T] =
    ScalaMultibinder.newSetBinder[T](binderAccess)

  /**
   * Returns a new [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/multibindings/Multibinder.html com.google.inject.multibindings.Multibinder]]
   * that collects instances of type [[T]] in a [[scala.collection.immutable.Set]] that is itself bound with a binding
   * annotation [[A]].
   *
   * $multiBinding
   *
   * @see [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/multibindings/Multibinder.html com.google.inject.multibindings.Multibinder]]
   */
  protected def bindMultiple[T: Manifest, A <: Annotation: Manifest]: ScalaMultibinder[T] =
    ScalaMultibinder.newSetBinder[T, A](binderAccess)

  /**
   * Returns a new [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/multibindings/Multibinder.html com.google.inject.multibindings.Multibinder]]
   * that collects instances of type [[T]] in a [[scala.collection.immutable.Set]] that is itself
   * bound with a binding annotation.
   *
   * $multiBinding
   *
   * @see [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/multibindings/Multibinder.html com.google.inject.multibindings.Multibinder]]
   */
  protected def bindMultiple[T: Manifest](annotation: Annotation): ScalaMultibinder[T] =
    ScalaMultibinder.newSetBinder[T](binderAccess, annotation)

  /**
   * Returns a new [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/multibindings/OptionalBinder.html com.google.inject.multibindings.OptionalBinder]]
   * that binds an instance of [[T]] in a [[scala.Option]].
   *
   * $optionalBinding
   *
   * @see [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/multibindings/OptionalBinder.html com.google.inject.multibindings.OptionalBinder]]
   */
  protected def bindOption[T: Manifest]: ScalaOptionBinder[T] =
    ScalaOptionBinder.newOptionBinder[T](binderAccess)

  /**
   * Returns a new [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/multibindings/OptionalBinder.html com.google.inject.multibindings.OptionalBinder]]
   * that binds an instance of [[T]] in a [[scala.Option]] that is itself bound with a binding
   * annotation [[A]].
   *
   * $optionalBinding
   *
   * @see [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/multibindings/OptionalBinder.html com.google.inject.multibindings.OptionalBinder]]
   */
  protected def bindOption[T: Manifest, A <: Annotation: Manifest]: ScalaOptionBinder[T] =
    ScalaOptionBinder.newOptionBinder[T, A](binderAccess)

  /**
   * Returns a new [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/multibindings/OptionalBinder.html com.google.inject.multibindings.OptionalBinder]]
   * that binds an instance of [[T]] in a [[scala.Option]] that is itself bound with a
   * binding annotation.
   *
   * $optionalBinding
   *
   * @see [[https://google.github.io/guice/api-docs/4.1/javadoc/com/google/inject/multibindings/OptionalBinder.html com.google.inject.multibindings.OptionalBinder]]
   */
  protected def bindOption[T: Manifest](annotation: Annotation): ScalaOptionBinder[T] =
    ScalaOptionBinder.newOptionBinder[T](binderAccess, annotation)
}
