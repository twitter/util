package com.twitter.inject

import com.twitter.app.{Flag, Flaggable}
import scala.collection.mutable.ArrayBuffer

/** [[com.twitter.app.Flag]] integration for a [[TwitterModule]]. */
private[inject] trait TwitterModuleFlags {

  /* Mutable State */

  protected[inject] lazy val flags: ArrayBuffer[Flag[_]] = ArrayBuffer[Flag[_]]()

  /* Protected */

  /**
   * This is akin to the `c.t.inject.app.App#failfastOnFlagsNotParsed` and serves
   * a similar purpose but for new [[com.twitter.app.Flag]] instances created in this
   * [[TwitterModule]]. The value is 'true' by default. This is to ensure that the value of
   * a [[com.twitter.app.Flag]] instance created in this [[TwitterModule]] cannot be incorrectly
   * accessed before the application has parsed any passed command line input. This mirrors the
   * framework default for `com.twitter.inject.app.App#failfastOnFlagsNotParsed` for Flag
   * instances created within the application container.
   *
   * @return a [[Boolean]] indicating if [[com.twitter.app.Flag]] instances created in
   *         this [[TwitterModule]] should be set with [[com.twitter.app.Flag.failFastUntilParsed]]
   *         set to 'true' or 'false'. Default: 'true'.
   * @note This value SHOULD NOT be changed to 'false' without a very good reason.
   */
  protected[this] def failfastOnFlagsNotParsed: Boolean = true

  /**
   * A Java-friendly method for creating a named [[Flag]].
   *
   * @param name the name of the [[Flag]].
   * @param default a default value for the [[Flag]] when no value is given as an application
   *                argument.
   * @param help the help text explaining the purpose of the [[Flag]].
   * @return the created [[Flag]].
   */
  final def createFlag[T](
    name: String,
    default: T,
    help: String,
    flaggable: Flaggable[T]
  ): Flag[T] = {
    val flag =
      new Flag[T](name, help, default, failFastUntilParsed = failfastOnFlagsNotParsed)(flaggable)
    flags += flag
    flag
  }

  /**
   * A Java-friendly way to create a "mandatory" [[Flag]]. "Mandatory" flags MUST have a value
   * provided as an application argument (as they have no default value to be used).
   *
   * @param name the name of the [[Flag]].
   * @param help the help text explaining the purpose of the [[Flag]].
   * @param usage a string describing the type of the [[Flag]], i.e.: Integer.
   * @return the created [[Flag]].
   */
  final def createMandatoryFlag[T](
    name: String,
    help: String,
    usage: String,
    flaggable: Flaggable[T]
  ): Flag[T] = {
    val flag =
      new Flag[T](name, help, usage, failFastUntilParsed = failfastOnFlagsNotParsed)(flaggable)
    flags += flag
    flag
  }

  /**
   * Create a [[Flag]] and add it to this Module's flags list.
   *
   * @note Java users: see the more Java-friendly [[createFlag]] or [[createMandatoryFlag]].
   *
   * @param name the name of the [[Flag]].
   * @param default a default value for the [[Flag]] when no value is given as an application
   *                argument.
   * @param help the help text explaining the purpose of the [[Flag]].
   * @tparam T must be a [[Flaggable]] type.
   * @return the created [[Flag]].
   */
  final def flag[T: Flaggable](name: String, default: T, help: String): Flag[T] = {
    val flag = new Flag[T](name, help, default, failFastUntilParsed = failfastOnFlagsNotParsed)
    flags += flag
    flag
  }

  /**
   * Create a "mandatory" flag and add it to this Module's flags list."Mandatory" flags MUST have
   * a value provided as an application argument (as they have no default value to be used).
   *
   * @note Java users: see the more Java-friendly [[createFlag]] or [[createMandatoryFlag]].
   *
   * @param name the name of the [[Flag]].
   * @param help the help text explaining the purpose of the [[Flag]].
   * @tparam T must be a [[Flaggable]] type.
   * @return the created [[Flag]].
   */
  final def flag[T: Flaggable: Manifest](name: String, help: String): Flag[T] = {
    val flag =
      new Flag[T](
        name,
        help,
        manifest[T].runtimeClass.toString,
        failFastUntilParsed = failfastOnFlagsNotParsed)
    flags += flag
    flag
  }
}
