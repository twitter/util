package com.twitter.app

import java.lang.reflect.{Field, Method, Modifier}
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

/**
 * Subclasses of GlobalFlag (that are defined in libraries) are "global" in the
 * sense that they are accessible by any application that depends on that library.
 * Regardless of where in a library a GlobalFlag is defined, a value for it can
 * be passed as a command-line flag by any binary that includes the library.
 * The set of defined GlobalFlags can be enumerated (via `GlobalFlag.getAll)` by
 * the application.
 *
 * A [[GlobalFlag]] must be declared as an `object` (see below for Java):
 * {{{
 * import com.twitter.app.GlobalFlag
 *
 * object myFlag extends GlobalFlag[String]("default value", "this is my global flag")
 * }}}
 *
 * All such global flag declarations in a given classpath are visible to and
 * used by [[com.twitter.app.App]].
 *
 * A flag's name (as set on the command line) is its fully-qualified classname.
 * For example, the flag
 *
 * {{{
 * package com.twitter.server
 *
 * import com.twitter.app.GlobalFlag
 *
 * object port extends GlobalFlag[Int](8080, "the TCP port to which we bind")
 * }}}
 *
 * is settable by the command-line flag `-com.twitter.server.port=8080`.
 *
 * Global flags may also be set by Java system properties with keys
 * named in the same way. However, values supplied by flags override
 * those supplied by system properties.
 *
 * @define java_declaration
 *
 * If you'd like to declare a new [[GlobalFlag]] in Java, see [[JavaGlobalFlag]].
 */
@GlobalFlagVisible
abstract class GlobalFlag[T] private[app] (
  defaultOrUsage: Either[() => T, String],
  help: String
)(
  implicit _f: Flaggable[T])
    extends Flag[T](null, help, defaultOrUsage, false) {

  override protected[this] def parsingDone: Boolean = true

  private[this] lazy val propertyValue =
    Option(System.getProperty(name)).flatMap { p =>
      try Some(flaggable.parse(p))
      catch {
        case NonFatal(exc) =>
          GlobalFlag.log.log(
            java.util.logging.Level.SEVERE,
            "Failed to parse system property " + name + " as flag",
            exc
          )
          None
      }
    }

  /**
   * $java_declaration
   *
   * @param default the default value used if the value is not specified by the user.
   * @param help documentation regarding usage of this [[Flag]].
   */
  def this(default: T, help: String)(implicit _f: Flaggable[T]) =
    this(Left(() => default), help)

  /**
   * $java_declaration
   *
   * @param help documentation regarding usage of this [[Flag]].
   */
  def this(help: String)(implicit _f: Flaggable[T], m: Manifest[T]) =
    this(Right(m.toString), help)

  /**
   * The "name", or "id", of this [[Flag]].
   *
   * While not marked `final`, if a subclass overrides this value, then
   * developers '''must''' set that flag via System properties as otherwise it
   * will not be recognized with command-line arguments.
   * e.g. `-DyourGlobalFlagName=flagName`
   */
  override val name: String = getClass.getName.stripSuffix("$")

  protected override def getValue: Option[T] = super.getValue match {
    case v @ Some(_) => v
    case _ => propertyValue
  }
}

object GlobalFlag {

  private[app] def get(f: String): Option[Flag[_]] = {
    def validMethod(m: Method): Boolean =
      m != null &&
        Modifier.isStatic(m.getModifiers) &&
        m.getReturnType == classOf[Flag[_]] &&
        m.getParameterCount == 0

    def tryMethod(clsName: String, methodName: String): Option[Flag[_]] =
      try {
        val cls = Class.forName(clsName)
        val m = cls.getMethod(methodName)
        if (validMethod(m))
          Some(m.invoke(null).asInstanceOf[Flag[_]])
        else
          None
      } catch {
        case _: ClassNotFoundException | _: NoSuchMethodException | _: IllegalArgumentException =>
          None
      }

    def validField(f: Field): Boolean =
      f != null && Modifier.isStatic(f.getModifiers) && classOf[Flag[_]].isAssignableFrom(f.getType)

    def tryModuleField(clsName: String): Option[Flag[_]] =
      try {
        val cls = Class.forName(clsName)
        val f = cls.getField("MODULE$")
        if (validField(f))
          Some(f.get(null).asInstanceOf[Flag[_]])
        else
          None
      } catch {
        case _: ClassNotFoundException | _: NoSuchFieldException | _: IllegalArgumentException =>
          None
      }

    val className = if (!f.endsWith("$")) f + "$" else f
    tryModuleField(className).orElse {
      // fallback for GlobalFlags declared in Java
      tryMethod(className, "globalFlagInstance")
    }
  }

  private val log = java.util.logging.Logger.getLogger("")

  private[app] def getAllOrEmptyArray(loader: ClassLoader): Seq[Flag[_]] = {
    try {
      getAll(loader)
    } catch {
      //NOTE: We catch Throwable as ExceptionInInitializerError and any errors really so that
      //we don't hide the real issue that a developer just added an unparseable arg.
      case e: Throwable =>
        log.log(java.util.logging.Level.SEVERE, "failure reading in flags", e)
        new ArrayBuffer[Flag[_]]
    }
  }

  private[app] def getAll(loader: ClassLoader): Seq[Flag[_]] = {

    // Since Scala object class names end with $, we search for them.
    // One thing we know for sure, Scala package objects can never be flags
    // so we filter those out.
    def couldBeFlag(className: String): Boolean =
      className.endsWith("$") && !className.endsWith("package$")

    val markerClass = classOf[GlobalFlagVisible]
    val flags = new ArrayBuffer[Flag[_]]

    // Search for Scala objects annotated with GlobalFlagVisible:
    val cp = new FlagClassPath()
    for (info <- cp.browse(loader) if couldBeFlag(info.className)) {
      try {
        val cls: Class[_] = Class.forName(info.className, false, loader)
        if (cls.isAnnotationPresent(markerClass)) {
          get(info.className) match {
            case Some(f) => flags += f
            case None => println("failed for " + info.className)
          }
        }
      } catch {
        case _: IllegalStateException | _: NoClassDefFoundError | _: ClassNotFoundException =>
      }
    }
    flags
  }
}
