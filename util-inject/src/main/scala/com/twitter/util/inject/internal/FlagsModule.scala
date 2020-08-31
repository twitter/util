package com.twitter.inject.internal

import com.google.inject.{AbstractModule, Key}
import com.twitter.util.inject.flag.Flags
import com.twitter.util.logging.Logging
import javax.inject.Provider

/**
 * Note this is purposely *not* an instance of a [[com.twitter.inject.TwitterModule]]
 * as we use no lifecycle callbacks nor does this define any [[com.twitter.app.Flag]]s.
 * Additionally, we do not want mistakenly use or bind the [[com.twitter.inject.TwitterModule#flags]]
 * reference thus we extend [[AbstractModule]] directly.
 *
 * This module is solely intended to create object graph bindings for the parsed value of
 * every non-global [[com.twitter.app.Flag]] contained in the passed [[com.twitter.app.Flags]] instance.
 * If the [[com.twitter.app.Flag]] instance does not have a parsed value a `Provider[Nothing]`
 * is provided which when de-referenced will throw an [[IllegalArgumentException]].
 */
private[inject] class FlagsModule(flags: com.twitter.app.Flags)
    extends AbstractModule
    with Logging {

  /**
   * Currently, we are unable to bind to anything other than a [[String]] type as Flags do not carry
   * enough type information due to type erasure. We would need to update [[com.twitter.app.Flag]]
   * or [[com.twitter.app.Flaggable]] to carry Manifest or TypeTag information which we could use
   * here to determine the correct type for building an instance Key. Or re-create the
   * [[com.twitter.app.Flaggable]] logic in a registered TypeConverter.
   *
   * In the interim, we install the Guice default type conversions along with the [[TwitterTypeConvertersModule]]
   * to provide a limited set of conversions by the injector from [[String]] to another type. Thus
   * you can inject a [[com.twitter.app.Flag]] of some non-[[String]] types. But this is not all of
   * the same types supported by [[com.twitter.app.Flaggable]] and [[com.twitter.app.Flag]]. Some
   * mismatch in the functionality is expected as the integration is done manually. That said,
   * defining a custom `Flaggable` instance for type X doesn't guarantee it can be injected without
   * registering a flag converter.
   *
   * @see [[TwitterTypeConvertersModule]]
   * @see [[https://github.com/google/guice/blob/master/core/src/com/google/inject/internal/TypeConverterBindingProcessor.java Guice Default Type Conversions]]
   */
  override def configure(): Unit = {
    // bind the c.t.inject.Flags instance to the object graph
    binder.bind(classOf[com.twitter.inject.Flags]).toInstance(com.twitter.inject.Flags(flags))

    // bind every parsed (non-global) flag that has a value keyed by flag name
    val asList = flags.getAll(includeGlobal = false).toSeq
    val flagsMap = (for (flag <- asList) yield {
      flag.name -> flag.getWithDefaultUnparsed
    }).toMap

    for ((flagName, valueOpt) <- flagsMap) {
      val key: Key[String] = Flags.key(flagName)
      valueOpt match {
        case Some(value) =>
          debug("Binding flag: " + flagName + " = " + value)
          binder.bind(key).toInstance(value)
        case None =>
          binder
            .bind(key)
            .toProvider(new Provider[Nothing] {
              override def get() =
                throw new IllegalArgumentException(
                  "flag: " + flagName + " has an unspecified value and is not eligible for @Flag injection"
                )
            })
      }
    }
  }
}
