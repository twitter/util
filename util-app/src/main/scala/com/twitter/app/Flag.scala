package com.twitter.app

import java.net.InetSocketAddress

import scala.collection.immutable.TreeSet
import scala.collection.mutable.{ArrayBuffer, HashMap}

import com.twitter.util._

/**
 * A type class providing evidence for parsing type `T` as a flag value.
 *
 * Any class that is to be provided as a flaggable value must have an
 * accompanying implicit `Flaggable` (contained within a companion object of the
 * class in question) for converting a string to an object of that type. For
 * instance, to make a hypothetical type called `Foo` flaggable:
 *
 * {{{
 * class Foo {
 *   ...
 * }
 *
 * object Foo {
 *   implicit val flagOfFoo = new Flaggable[Foo] {
 *     def parse(v: String): Foo = {
 *       ...
 *     }
 *   }
 * }
 * }}}
 *
 * For simple implicit definitions based on existing `String => T` functions,
 * use the `Flaggable.mandatory` function:
 *
 * {{{
 * object Foo {
 *   def parse(v: String: Foo = {
 *      ...
 *   }
 *
 *   implicit val ofFoo = Flaggable.mandatory(Foo.parse(_))
 * }
 * }}}
 *
 * [1] http://en.wikipedia.org/wiki/Type_class
 */
trait Flaggable[T] {
  /**
   * Parse a string (i.e. a value set on the command line) into an object of
   * type `T`.
   */
  def parse(s: String): T

  /**
   * Create a string-representation of an object of type `T`. Used in
   * `Flag.toString`.
   */
  def show(t: T): String = t.toString

  /**
   * An optional default value for the Flaggable.
   */
  def default: Option[T] = None
}

/**
 * Default `Flaggable` implementations.
 */
object Flaggable {
  def mandatory[T](f: String => T) = new Flaggable[T] {
    def parse(s: String) = f(s)
  }

  implicit val ofBoolean = new Flaggable[Boolean] {
    override def default = Some(true)
    def parse(s: String) = s.toBoolean
  }

  implicit val ofString = mandatory(identity)
  implicit val ofInt = mandatory(_.toInt)
  implicit val ofLong = mandatory(_.toLong)
  implicit val ofFloat = mandatory(_.toFloat)
  implicit val ofDouble = mandatory(_.toDouble)
  implicit val ofDuration = mandatory(Duration.parse(_))
  implicit val ofStorageUnit = mandatory(StorageUnit.parse(_))

  private val defaultTimeFormat = new TimeFormat("yyyy-MM-dd HH:mm:ss Z")
  implicit val ofTime = mandatory(defaultTimeFormat.parse(_))

  implicit object ofInetSocketAddress extends Flaggable[InetSocketAddress] {
    def parse(v: String) =  v.split(":") match {
      case Array("", p) =>
        new InetSocketAddress(p.toInt)
      case Array(h, p) =>
        new InetSocketAddress(h, p.toInt)
      case _ =>
        throw new IllegalArgumentException
    }

    override def show(addr: InetSocketAddress) =
      "%s:%d".format(
        Option(addr.getAddress) match {
          case Some(a) if a.isAnyLocalAddress => ""
          case _ => addr.getHostName
        },
        addr.getPort)
  }

  implicit def ofTuple[T: Flaggable, U: Flaggable] = new Flaggable[(T, U)] {
    private val tflag = implicitly[Flaggable[T]]
    private val uflag = implicitly[Flaggable[U]]

    assert(!tflag.default.isDefined)
    assert(!uflag.default.isDefined)

    def parse(v: String) = v.split(",") match {
      case Array(t, u) => (tflag.parse(t), uflag.parse(u))
      case _ => throw new IllegalArgumentException("not a 't,u'")
    }

    override def show(tup: (T, U)) = {
      val (t, u) = tup
      tflag.show(t)+","+uflag.show(u)
    }
  }

  implicit def ofSeq[T: Flaggable] = new Flaggable[Seq[T]] {
    private val flag = implicitly[Flaggable[T]]
    assert(!flag.default.isDefined)
    def parse(v: String): Seq[T] = v.split(",") map flag.parse
    override def show(seq: Seq[T]) = seq map flag.show mkString ","
  }

  implicit def ofMap[K: Flaggable, V: Flaggable] = new Flaggable[Map[K, V]] {
    private val kflag = implicitly[Flaggable[K]]
    private val vflag = implicitly[Flaggable[V]]

    assert(!kflag.default.isDefined)
    assert(!vflag.default.isDefined)

    def parse(in: String): Map[K, V] = {
      val tuples = in.split(',').foldLeft(Seq.empty[String]) {
        case (acc, s) if !s.contains('=') =>
          // In order to support comma-separated values, we concatenate
          // consecutive tokens that don't contain equals signs.
          acc.init :+ (acc.last + ',' + s)
        case (acc, s) => acc :+ s
      }

      tuples map { tup =>
        tup.split("=") match {
          case Array(k, v) => (kflag.parse(k), vflag.parse(v))
          case _ => throw new IllegalArgumentException("not a 'k=v'")
        }
      } toMap
    }

    override def show(out: Map[K, V]) = {
      out.toSeq map { case (k, v) => k.toString + "=" + v.toString } mkString(",")
    }
  }
}

/**
 * Exception thrown upon flag-parsing failure. Should typically lead to process
 * death, since continued execution would run the risk of unexpected behavior on
 * account of incorrectly-interpreted or malformed flag values.
 *
 * @param message A string name of the flag for which parsing failed.
 * @param cause The underlying [[java.lang.Throwable]] that caused this exception.
 */
case class FlagParseException(message: String, cause: Throwable = null)
  extends Exception(message, cause)
case class FlagUsageError(usage: String) extends Exception
class FlagValueRequiredException extends Exception(Flags.FlagValueRequiredMessage)
class FlagUndefinedException extends Exception(Flags.FlagUndefinedMessage)

/**
 * A single command-line flag, instantiated by a [[com.twitter.app.Flags]]
 * instance. Its current value can be extracted via `apply()`.
 *
 * @see [[com.twitter.app.Flags]]
 */
class Flag[T: Flaggable] private[app](val name: String, val help: String, defaultOrUsage: Either[() => T, String]) {

  private[app] def this(name: String, help: String, default: => T) = this(name, help, Left(() => default))
  private[app] def this(name: String, help: String, usage: String) = this(name, help, Right(usage))

  protected val flaggable = implicitly[Flaggable[T]]
  @volatile private[this] var value: Option[T] = None
  protected def getValue: Option[T] = value

  private def default: Option[T] = defaultOrUsage.left.toOption map { d => d() }
  private def valueOrDefault: Option[T] = getValue orElse default

  private[this] def flagNotFound: IllegalArgumentException =
    new IllegalArgumentException("flag '%s' not found".format(name))

  /**
   * Return this flag's current value. The default value is returned
   * when the flag has not otherwise been set.
   */
  def apply(): T = valueOrDefault getOrElse { throw flagNotFound }

  /** Reset this flag's value */
  def reset() { value = None }
  /** True if the flag has been set */
  def isDefined = getValue.isDefined
  /** Get the value if it has been set */
  def get: Option[T] = getValue
  /** String representation of this flag's default value */
  def defaultString = flaggable.show(default getOrElse { throw flagNotFound })

  def usageString =
    defaultOrUsage match {
      case Left(_) => "  -%s='%s': %s".format(name, defaultString, help)
      case Right(usage) => "  -%s=<%s>: %s".format(name, usage, help)
    }

  /**
   * String representation of this flag in -foo='bar' format,
   * suitable for being used on the command line.
   */
  override def toString = {
    valueOrDefault match {
      case None => "-" + name + "=<unset>"
      case Some(v) => "-" + name + "='" + flaggable.show(v).replaceAll("'", "'\"'\"'") + "'"
    }
  }

  /** Parse value `raw` into this flag. */
  def parse(raw: String) {
    value = Some(flaggable.parse(raw))
  }

  /** Parse this flag with no argument. */
  def parse() {
    value = flaggable.default
  }

  /** Indicates whether or not the flag is valid without an argument. */
  def noArgumentOk = flaggable.default.isDefined
}

object Flags {
  private[app] val FlagValueRequiredMessage = "flag value is required"
  private[app] val FlagUndefinedMessage = "flag undefined"

  sealed trait FlagParseResult

  /**
   * Indicates successful flag parsing.
   * @param remainder A remainder list of unparsed arguments.
   */
  case class Ok(remainder: Seq[String]) extends FlagParseResult

  /**
   * Indicates that a help flag (i.e. -help) was encountered
   * @param usage A string containing the application usage instructions.
   */
  case class Help(usage: String) extends FlagParseResult

  /**
   * Indicates that an error occurred during flag-parsing.
   * @param reason A string explaining the error that occurred.
   */
  case class Error(reason: String) extends FlagParseResult
}

/**
 * A simple flags implementation. We support only two formats:
 *
 *    for flags with optional values (e.g. booleans):
 *      -flag, -flag=value
 *    for flags with required values:
 *      -flag[= ]value
 *
 * That's it. These can be parsed without ambiguity.
 *
 * There is no support for mandatory arguments: That is not what
 * flags are for.
 *
 * `Flags.apply` adds a new flag to the flag set, so it is idiomatic
 * to assign instances of `Flags` to a singular `flag` val:
 *
 * {{{
 * val flag = new Flags("myapp")
 * val i = flag("i", 123, "iteration count")
 * }}}
 *
 * Global flags, detached from a particular `Flags` instance but
 * accessible to all, are defined by [[com.twitter.app.GlobalFlag]].
 *
 * @param argv0 The name of the application that is to be configured via flags
 * @param includeGlobal If true, [[com.twitter.app.GlobalFlag GlobalFlags]] will
 * be included during flag parsing. If false, only flags defined in the
 * application itself will be consulted.
 */
class Flags(argv0: String, includeGlobal: Boolean) {
  import Flags._

  def this(argv0: String) = this(argv0, false)

  private[this] val flags = new HashMap[String, Flag[_]]

  @volatile private[this] var cmdUsage = ""

  // Add a help flag by default
  private[this] val helpFlag = this("help", false, "Show this help")


  def reset() = synchronized {
    flags foreach { case (_, f) => f.reset() }
  }

  private[this] def resolveGlobalFlag(f: String) =
    if (includeGlobal) GlobalFlag.get(f) else None

  private[this] def resolveFlag(f: String): Option[Flag[_]] =
    synchronized { flags.get(f) orElse resolveGlobalFlag(f) }

  private[this] def hasFlag(f: String) = resolveFlag(f).isDefined
  private[this] def flag(f: String) = resolveFlag(f).get

  /**
   * Parse an array of flag strings.
   *
   * @param args The array of strings to parse.
   * @param allowUndefinedFlags If true, undefined flags (i.e. those that are
   * not defined in the application via a `flag.apply` invocation) are allowed.
   * If false, undefined flags will result in a FlagParseException being thrown.
   * @return A [[com.twitter.app.Flags.FlagParseResult]] representing the
   * result of parsing `args`.
   */
  def parseArgs(
    args: Array[String],
    allowUndefinedFlags: Boolean = false
  ): FlagParseResult = synchronized {
    reset()
    val remaining = new ArrayBuffer[String]
    var i = 0
    while (i < args.size) {
      val a = args(i)
      i += 1
      if (a == "--") {
        remaining ++= args.slice(i, args.size)
        i = args.size
      } else if (a startsWith "-") {
        a drop 1 split("=", 2) match {
          // There seems to be a bug Scala's pattern matching
          // optimizer that leaves `v' dangling in the last case if
          // we make this a wildcard (Array(k, _@_*))
          case Array(k) if !hasFlag(k) =>
            if (allowUndefinedFlags)
              remaining += a
            else
              return Error(
                "Error parsing flag \"%s\": %s\n%s".format(k, FlagUndefinedMessage, usage)
              )

          // Flag isn't defined
          case Array(k, _) if !hasFlag(k) =>
            if (allowUndefinedFlags)
              remaining += a
            else return Error(
              "Error parsing flag \"%s\": %s\n%s".format(k, FlagUndefinedMessage, usage)
            )

          // Optional argument without a value
          case Array(k) if flag(k).noArgumentOk =>
            flag(k).parse()

          // Mandatory argument without a value and with no more arguments.
          case Array(k) if i == args.size =>
            return Error(
              "Error parsing flag \"%s\": %s\n%s".format(k, FlagValueRequiredMessage, usage)
            )

          // Mandatory argument with another argument
          case Array(k) =>
            i += 1
            try flag(k).parse(args(i-1)) catch {
              case NonFatal(e) => return Error(
                "Error parsing flag \"%s\": %s\n%s".format(k, e.getMessage, usage)
              )
            }

          // Mandatory k=v
          case Array(k, v) =>
            try flag(k).parse(v) catch {
              case e: Throwable => return Error(
                "Error parsing flag \"%s\": %s\n%s".format(k, e.getMessage, usage)
              )
            }
        }
      } else {
        remaining += a
      }
    }

    if (helpFlag())
      Help(usage)
    else
      Ok(remaining)
  }

  /**
   * Parse an array of flag strings.
   *
   * @note This method has been deprecated in favor of `Flags.parseArgs`,
   * which indicates success or failure by returning
   * [[com.twitter.app.Flags.FlagParseResult]] rather than relying on thrown
   * exceptions.
   *
   * @param args The array of strings to parse.
   * @param undefOk If true, undefined flags (i.e. those that are not defined
   * in the application via a `flag.apply` invocation) are allowed. If false,
   * undefined flags will result in a FlagParseException being thrown.
   * @throws FlagParseException if an error occurs during flag-parsing.
   */
  @deprecated("Prefer result-value based `Flags.parseArgs` method", "6.17.1")
  def parse(
    args: Array[String],
    undefOk: Boolean = false
  ): Seq[String] = parseArgs(args, undefOk) match {
    case Ok(remainder) => remainder
    case Help(usage) => throw FlagUsageError(usage)
    case Error(reason) => throw FlagParseException(reason)
  }

  /**
   * Parse an array of flag strings or exit the application (with exit code 1)
   * upon failure to do so.
   *
   * @param args The array of strings to parse.
   * @param undefOk If true, undefined flags (i.e. those that are not defined
   * in the application via a `flag.apply` invocation) are allowed. If false,
   * undefined flags will result in a FlagParseException being thrown.
   */
  def parseOrExit1(args: Array[String], undefOk: Boolean = true): Seq[String] =
    parseArgs(args, undefOk) match {
      case Ok(remainder) =>
        remainder
      case Help(usage) =>
        System.err.println(usage)
        System.exit(1)
        throw new IllegalStateException
      case Error(reason) =>
        System.err.println(reason)
        System.exit(1)
        throw new IllegalStateException
    }

  /**
   * Add a flag. The canonical way to do so is via `Flags#apply`, but this
   * method is left public for any rare edge cases where it is necessary.
   *
   * @param f A concrete Flag to add
   */
  def add(f: Flag[_]) = synchronized {
    if (flags contains f.name)
      System.err.printf("Flag %s already defined!\n", f.name)
    flags(f.name) = f
  }

  /**
   * Add a named flag with a default value and a help message.
   *
   * @param name The name of the flag.
   * @param default A default value, as a thunk.
   * @param help The help string of the flag.
   */
  def apply[T: Flaggable](name: String, default: => T, help: String) = {
    val f = new Flag[T](name, help, default)
    add(f)
    f
  }

  /**
   * Add a named flag with a help message.
   *
   * @param name The name of the flag.
   * @param help The help string of the flag.
   */
  def apply[T](name: String, help: String)(implicit _f: Flaggable[T], m: Manifest[T]) = {
    val f = new Flag[T](name, help, m.toString)
    add(f)
    f
  }

  /**
   * Set the flags' command usage; this is a message printed
   * before the flag definitions in the usage string.
   */
  def setCmdUsage(u: String) {
    cmdUsage = u
  }

  def usage: String = synchronized {
    val lines =
      for (k <- flags.keys.toArray.sorted)
      yield flags(k).usageString
    val globalLines = if (!includeGlobal) Seq.empty else {
      GlobalFlag.getAll(getClass.getClassLoader).map(_.usageString).sorted
    }

    val cmd = if (cmdUsage.nonEmpty) cmdUsage+"\n" else "usage: "

    cmd+argv0+" [<flag>...]\n"+
    "flags:\n"+
    (lines mkString "\n")+(
      if (globalLines.isEmpty) "" else {
        "\nglobal flags:\n"+
        (globalLines mkString "\n")
      }
    )
  }

  /**
   * Get all of the flags known to this Flags instance.
   *
   * @param includeGlobal If true, all registered
   * [[com.twitter.app.GlobalFlag GlobalFlags]] will be included
   * in output. Defaults to the `includeGlobal` settings of this instance.
   * @param classLoader The [[java.lang.ClassLoader]] used to fetch
   * [[com.twitter.app.GlobalFlag GlobalFlags]], if necessary. Defaults to this
   * instance's classloader.
   * @return All of the flags known to this this Flags instance.
   */
  def getAll(
    includeGlobal: Boolean = this.includeGlobal,
    classLoader: ClassLoader = this.getClass.getClassLoader
  ): Iterable[Flag[_]] = synchronized {
    var flags = TreeSet[Flag[_]]()(Ordering.by(_.name)) ++ this.flags.valuesIterator

    if (includeGlobal) {
      flags ++= GlobalFlag.getAll(classLoader).iterator
    }

    flags
  }

  /**
   * Formats all values of all flags known to this instance into a format
   * suitable for logging.
   *
   * @param includeGlobal See `getAll` above.
   * @param classLoader   See `getAll` above.
   * @return All of the flag values in alphabetical order, grouped into (set, unset).
   */
  def formattedFlagValues(
    includeGlobal: Boolean = this.includeGlobal,
    classLoader: ClassLoader = this.getClass.getClassLoader
  ): (Iterable[String], Iterable[String]) = {
    val (set, unset) = getAll(includeGlobal, classLoader).partition { _.get.isDefined }

    (set.map { _ + " \\" }, unset.map { _ + " \\" })
  }

  /**
   * Creates a string containing all values of all flags known to this instance
   * into a format suitable for logging.
   *
   * @param includeGlobal See `getAll` above.
   * @param classLoader   Set `getAll` above
   * @return A string suitable for logging.
   */
  def formattedFlagValuesString(
    includeGlobal: Boolean = this.includeGlobal,
    classLoader: ClassLoader = this.getClass.getClassLoader
  ): String = {
    val (set, unset) = formattedFlagValues(includeGlobal, classLoader)
    val lines = Seq("Set flags:") ++
      set ++
      Seq("Unset flags:") ++
      unset

    lines.mkString("\n")
  }
}

/**
 * Subclasses of GlobalFlag (that are defined in libraries) are "global" in the
 * sense that they are accessible by any application that depends on that library.
 * Regardless of where in a library a GlobalFlag is defined, a value for it can
 * be passed as a command-line flag by any binary that includes the library.
 * The set of defined GlobalFlags can be enumerated (via `GlobalFlag.getAll)` by
 * the application.
 *
 * {{{
 * object MyFlag extends GlobalFlag("my", "default value", "this is my global flag")
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
 * object port extends GlobalFlag(8080, "the TCP port to which we bind")
 * }}}
 *
 * is settable by the command-line flag `-com.twitter.server.port`.
 *
 * Global flags may also be set by Java system properties with keys
 * named in the same way. However, values supplied by flags override
 * those supplied by system properties.
 */
@GlobalFlagVisible
class GlobalFlag[T] private[app](
  defaultOrUsage: Either[() => T, String],
  help: String
)(implicit _f: Flaggable[T]) extends Flag[T](null, help, defaultOrUsage) {

  def this(default: T, help: String)(implicit _f: Flaggable[T]) = this(Left(() => default), help)
  def this(help: String)(implicit _f: Flaggable[T], m: Manifest[T]) = this(Right(m.toString), help)

  // Unfortunately, `getClass` in the the extends... above
  // doesn't give the right answer.
  override val name = getClass.getName.stripSuffix("$")

  protected override def getValue = super.getValue orElse {
    Option(System.getProperty(name)) flatMap { p =>
      try Some(flaggable.parse(p)) catch {
        case NonFatal(exc) =>
          java.util.logging.Logger.getLogger("").log(
            java.util.logging.Level.SEVERE,
            "Failed to parse system property "+name+" as flag", exc)
          None
      }
    }
  }

  def getGlobalFlag: Flag[_] = this
}

private object GlobalFlag {
  def get(f: String): Option[Flag[_]] = try {
    val cls = Class.forName(f)
    val m = cls.getMethod("getGlobalFlag")
    Some(m.invoke(null).asInstanceOf[Flag[_]])
  } catch {
    case _: ClassNotFoundException
      | _: NoSuchMethodException
      | _: IllegalArgumentException => None
  }

  def getAll(loader: ClassLoader) = {
    val markerClass = classOf[GlobalFlagVisible]
    val flags = new ArrayBuffer[Flag[_]]

    for (info <- ClassPath.browse(loader)) try {
      val cls = info.load()
      if (cls.isAnnotationPresent(markerClass) && (info.name endsWith "$")) {
        get(info.name.dropRight(1)) match {
          case Some(f) => flags += f
          case None => println("failed for "+info.name)
        }
      }
    } catch {
      case _: IllegalStateException
        | _: NoClassDefFoundError
        | _: ClassNotFoundException =>
    }

    flags
  }
}
