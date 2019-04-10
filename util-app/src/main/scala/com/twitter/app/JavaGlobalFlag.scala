package com.twitter.app

/**
 * In order to declare a new [[GlobalFlag]] in Java, it can be done with a bit
 * of ceremony by following a few steps:
 *
 *  1. the flag's class name must end with "$", e.g. `javaGlobalWithDefault$`.
 *  This is done to mimic the behavior of Scala's singleton `object`.
 *  1. the class must have a method, `public static Flag<?> globalFlagInstance()`,
 *  that returns the singleton instance of the [[Flag]].
 *  1. the class should have a `static final` field holding the singleton
 *  instance of the flag.
 *  1. the class should extend `JavaGlobalFlag`.
 *  1. to avoid extraneous creation of instances, thus forcing users to only
 *  access the singleton, the constructor should be `private` and the class
 *  should be `public final`.
 *
 * An example of a [[GlobalFlag]] declared in Java:
 * {{{
 * import com.twitter.app.Flag;
 * import com.twitter.app.Flaggable;
 * import com.twitter.app.JavaGlobalFlag;
 *
 * public final class exampleJavaGlobalFlag$ extends JavaGlobalFlag<String> {
 *   private exampleJavaGlobalFlag() {
 *     super("The default value", "The help string", Flaggable.ofString());
 *   }
 *   public static final Flag<String> Flag = new exampleJavaGlobalFlag();
 *   public static Flag<?> globalFlagInstance() {
 *     return Flag;
 *   }
 * }
 * }}}
 */
abstract class JavaGlobalFlag[T] private (
  defaultOrUsage: Either[() => T, String],
  help: String,
  flaggable: Flaggable[T])
    extends GlobalFlag[T](defaultOrUsage, help)(flaggable) {

  /**
   * A flag with a default value.
   *
   * $java_declaration
   *
   * @param default the default value used if the value is not specified by the user.
   * @param help documentation regarding usage of this flag.
   */
  protected def this(default: T, help: String, flaggable: Flaggable[T]) =
    this(Left(() => default), help, flaggable)

  /**
   * A flag without a default value.
   *
   * $java_declaration
   *
   * @param help documentation regarding usage of this flag.
   * @param clazz the type of the flag
   */
  protected def this(help: String, flaggable: Flaggable[T], clazz: Class[T]) =
    this(Right(clazz.getName), help, flaggable)

}
