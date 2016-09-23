package com.twitter.app

import com.twitter.util._
import com.twitter.util.registry.GlobalRegistry
import java.io.File
import java.lang.reflect.{Method, Modifier}
import java.lang.{Boolean => JBoolean, Double => JDouble, Float => JFloat, Integer => JInteger, Long => JLong}
import java.net.InetSocketAddress
import java.util.{List => JList, Map => JMap, Set => JSet}
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.JavaConverters._
import scala.collection.immutable.TreeSet
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

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
abstract class Flaggable[T] {
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
  /**
   * Create a `Flaggable[T]` according to a `String => T` conversion function.
   *
   * @param f Function that parses a string into an object of type `T`
   */
  def mandatory[T](f: String => T): Flaggable[T] = new Flaggable[T] {
    def parse(s: String): T = f(s)
  }

  implicit val ofString: Flaggable[String] = mandatory(identity)

  // Pairs of Flaggable conversions for types with corresponding Java boxed types.
  implicit val ofBoolean: Flaggable[Boolean] = new Flaggable[Boolean] {
    override def default: Option[Boolean] = Some(true)
    def parse(s: String): Boolean = s.toBoolean
  }

  implicit val ofJavaBoolean: Flaggable[JBoolean] = new Flaggable[JBoolean] {
    override def default: Option[JBoolean] = Some(JBoolean.TRUE)
    def parse(s: String): JBoolean = JBoolean.valueOf(s.toBoolean)
  }

  implicit val ofInt: Flaggable[Int] = mandatory(_.toInt)
  implicit val ofJavaInteger: Flaggable[JInteger] =
    mandatory { s: String => JInteger.valueOf(s.toInt) }

  implicit val ofLong: Flaggable[Long] = mandatory(_.toLong)
  implicit val ofJavaLong: Flaggable[JLong] =
    mandatory { s: String => JLong.valueOf(s.toLong) }

  implicit val ofFloat: Flaggable[Float] = mandatory(_.toFloat)
  implicit val ofJavaFloat: Flaggable[JFloat] =
    mandatory { s: String => JFloat.valueOf(s.toFloat) }

  implicit val ofDouble: Flaggable[Double] = mandatory(_.toDouble)
  implicit val ofJavaDouble: Flaggable[JDouble] =
    mandatory { s: String => JDouble.valueOf(s.toDouble) }

  // Conversions for common non-primitive types and collections.
  implicit val ofDuration: Flaggable[Duration] = mandatory(Duration.parse(_))
  implicit val ofStorageUnit: Flaggable[StorageUnit] = mandatory(StorageUnit.parse(_))

  private val defaultTimeFormat = new TimeFormat("yyyy-MM-dd HH:mm:ss Z")
  implicit val ofTime: Flaggable[Time] = mandatory(defaultTimeFormat.parse(_))

  implicit val ofInetSocketAddress: Flaggable[InetSocketAddress] =
    new Flaggable[InetSocketAddress] {
      def parse(v: String): InetSocketAddress = v.split(":") match {
        case Array("", p) =>
          new InetSocketAddress(p.toInt)
        case Array(h, p) =>
          new InetSocketAddress(h, p.toInt)
        case _ =>
          throw new IllegalArgumentException
      }

      override def show(addr: InetSocketAddress): String =
        "%s:%d".format(
          Option(addr.getAddress) match {
            case Some(a) if a.isAnyLocalAddress => ""
            case _ => addr.getHostName
          },
          addr.getPort)
    }

  implicit val ofFile: Flaggable[File] = new Flaggable[File] {
    override def parse(v: String): File = new File(v)
    override def show(file: File): String = file.toString
  }

  implicit def ofTuple[T: Flaggable, U: Flaggable]: Flaggable[(T, U)] = new Flaggable[(T, U)] {
    private val tflag = implicitly[Flaggable[T]]
    private val uflag = implicitly[Flaggable[U]]

    assert(tflag.default.isEmpty)
    assert(uflag.default.isEmpty)

    def parse(v: String): (T, U) = v.split(",") match {
      case Array(t, u) => (tflag.parse(t), uflag.parse(u))
      case _ => throw new IllegalArgumentException("not a 't,u'")
    }

    override def show(tup: (T, U)): String = {
      val (t, u) = tup
      tflag.show(t)+","+uflag.show(u)
    }
  }

  private[app] class SetFlaggable[T: Flaggable] extends Flaggable[Set[T]] {
    private val flag = implicitly[Flaggable[T]]
    assert(flag.default.isEmpty)
    override def parse(v: String): Set[T] = v.split(",").map(flag.parse(_)).toSet
    override def show(set: Set[T]): String = set.map(flag.show).mkString(",")
  }

  private[app] class SeqFlaggable[T: Flaggable] extends Flaggable[Seq[T]] {
    private val flag = implicitly[Flaggable[T]]
    assert(flag.default.isEmpty)
    def parse(v: String): Seq[T] = v.split(",").map(flag.parse)
    override def show(seq: Seq[T]): String = seq.map(flag.show).mkString(",")
  }

  private[app] class MapFlaggable[K: Flaggable, V: Flaggable] extends Flaggable[Map[K, V]] {
    private val kflag = implicitly[Flaggable[K]]
    private val vflag = implicitly[Flaggable[V]]

    assert(kflag.default.isEmpty)
    assert(vflag.default.isEmpty)

    def parse(in: String): Map[K, V] = {
      val tuples = in.split(',').foldLeft(Seq.empty[String]) {
        case (acc, s) if !s.contains('=') =>
          // In order to support comma-separated values, we concatenate
          // consecutive tokens that don't contain equals signs.
          acc.init :+ (acc.last + ',' + s)
        case (acc, s) => acc :+ s
      }

      tuples.map { tup =>
        tup.split("=") match {
          case Array(k, v) => (kflag.parse(k), vflag.parse(v))
          case _ => throw new IllegalArgumentException("not a 'k=v'")
        }
      }.toMap
    }

    override def show(out: Map[K, V]): String = {
      out.toSeq.map { case (k, v) => k.toString + "=" + v.toString }.mkString(",")
    }
  }

  implicit def ofSet[T: Flaggable]: Flaggable[Set[T]] = new SetFlaggable[T]
  implicit def ofSeq[T: Flaggable]: Flaggable[Seq[T]] = new SeqFlaggable[T]
  implicit def ofMap[K: Flaggable, V: Flaggable]: Flaggable[Map[K, V]] = new MapFlaggable[K, V]

  implicit def ofJavaSet[T: Flaggable]: Flaggable[JSet[T]] = new Flaggable[JSet[T]] {
    val setFlaggable = new SetFlaggable[T]
    override def parse(v: String): JSet[T] = setFlaggable.parse(v).asJava
    override def show(set: JSet[T]): String = setFlaggable.show(set.asScala.toSet)
  }

  implicit def ofJavaList[T: Flaggable]: Flaggable[JList[T]] = new Flaggable[JList[T]] {
    val seqFlaggable = new SeqFlaggable[T]
    override def parse(v: String): JList[T] = seqFlaggable.parse(v).asJava
    override def show(list: JList[T]): String = seqFlaggable.show(list.asScala)
  }

  implicit def ofJavaMap[K: Flaggable, V: Flaggable]: Flaggable[JMap[K, V]] = {
    val mapFlaggable = new MapFlaggable[K, V]

    new Flaggable[JMap[K, V]] {
      def parse(in: String): JMap[K, V] = mapFlaggable.parse(in).asJava
      override def show(out: JMap[K, V]): String = mapFlaggable.show(out.asScala.toMap)
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
case class FlagUsageError(usage: String) extends Exception(usage)
class FlagValueRequiredException extends Exception(Flags.FlagValueRequiredMessage)
class FlagUndefinedException extends Exception(Flags.FlagUndefinedMessage)

object Flag {

  // stores Local overrides for a Flag's value
  private val localFlagValues = new Local[Map[Flag[_], Any]]
  private[app] val EmptyRequired = "__EMPTY_REQUIRED_FLAG"

}

/**
 * A single command-line flag, instantiated by a [[com.twitter.app.Flags]]
 * instance. Its current value can be extracted via `apply()`.
 *
 * @see [[com.twitter.app.Flags]]
 */
class Flag[T: Flaggable] private[app](
    val name: String,
    val help: String,
    defaultOrUsage: Either[() => T, String],
    failFastUntilParsed: Boolean)
{
  import com.twitter.app.Flag._
  import java.util.logging._

  private[app] def this(name: String, help: String, default: => T, failFastUntilParsed: Boolean) =
    this(name, help, Left(() => default), failFastUntilParsed)

  private[app] def this(name: String, help: String, usage: String, failFastUntilParsed: Boolean) =
    this(name, help, Right(usage), failFastUntilParsed)

  private[app] def this(name: String, help: String, default: => T) =
    this(name, help, default, false)

  private[app] def this(name: String, help: String, usage: String) =
    this(name, help, usage, false)

  private[this] val log = Logger.getLogger("")

  protected val flaggable: Flaggable[T] = implicitly[Flaggable[T]]

  private[this] def localValue: Option[T] = localFlagValues() match {
    case None => None
    case Some(flagValues) =>
      flagValues.get(this) match {
        case None => None
        case Some(flagValue) => Some(flagValue.asInstanceOf[T])
      }
  }

  private[this] def setLocalValue(value: Option[T]): Unit = {
    val updatedMap: Map[Flag[_], Any] = (localFlagValues(), value) match {
      case (Some(map), Some(v)) => map + (this -> v)
      case (Some(map), None)    => map - this
      case (None, Some(v))      => Map(this -> v)
      case (None, None)         => Map.empty[Flag[_], Any]
    }
    if (updatedMap.isEmpty)
      localFlagValues.clear()
    else
      localFlagValues.set(Some(updatedMap))
  }

  @volatile private[this] var value: Option[T] = None
  private[this] val registered = new AtomicBoolean(false)

  private[this] def register(): Unit = {
    val shown = valueOrDefault.map(flaggable.show).getOrElse(EmptyRequired)
    GlobalRegistry.get.put(Seq("flags", name), shown)
  }

  protected def getValue: Option[T] = {
    if (registered.compareAndSet(false, true)) {
      register()
    }
    localValue match {
      case lv@Some(_) => lv
      case None => value
    }
  }

  @volatile private[this] var _parsingDone = false
  protected[this] def parsingDone: Boolean = _parsingDone

  private lazy val default: Option[T] = defaultOrUsage match {
    case Right(_) => None
    case Left(d) =>
      try {
        Some(d())
      } catch {
        case e: Throwable =>
          throw new RuntimeException( s"Could not run default function for flag $name", e)
      }
  }

  private def valueOrDefault: Option[T] = getValue match {
    case v@Some(_) => v
    case None => default
  }

  private[this] def flagNotFound: IllegalArgumentException =
    new IllegalArgumentException("flag '%s' not found".format(name))

  /**
   * Override the value of this flag with `t`, only for the scope of the current
   * [[com.twitter.util.Local]] for the given function `f`.
   *
   * @see [[letClear]]
   */
  def let(t: T)(f: => Unit): Unit =
    let(Some(t), f)

  /**
   * Unset the value of this flag, such that [[isDefined]] will return `false`,
   * only for the scope of the current [[com.twitter.util.Local]] for the
   * given function `f`.
   *
   * @see [[let]]
   */
  def letClear(f: => Unit): Unit =
    let(None, f)

  private[this] def let(t: Option[T], f: => Unit): Unit = {
    val prev = localValue
    setLocalValue(t)
    try f finally {
      setLocalValue(prev)
    }
  }

  /**
   * Return this flag's current value. The default value is returned
   * when the flag has not otherwise been set.
   */
  def apply(): T = {
    if (!parsingDone) {
      if (failFastUntilParsed)
        throw new IllegalStateException(s"Flag $name read before parse.")
      else
        log.log(Level.SEVERE, s"Flag $name read before parse.")
    }
    valueOrDefault match {
      case Some(v) => v
      case None => throw flagNotFound
    }
  }

  /** Reset this flag's value */
  def reset(): Unit = {
    value = None
    _parsingDone = false
  }

  /**
   * True if the flag has been set.
   *
   * @note if no user-defined value has been set, `false` will be
   * returned even when a default value is supplied.
   */
  def isDefined: Boolean = getValue.isDefined

  /**
   * Get the value if it has been set.
   *
   * @note if no user-defined value has been set, `None` will be
   * returned even when a default value is supplied.
   * @see [[Flag.getWithDefault]]
   */
  def get: Option[T] = getValue

  /**
   * Get the value if it has been set or if there is a default value supplied.
   *
   * @see [[Flag.get]] and [[Flag.isDefined]] if you are interested in
   *      determining if there is a supplied value.
   */
  def getWithDefault: Option[T] = valueOrDefault

  /** String representation of this flag's default value */
  def defaultString(): String = {
    try {
      flaggable.show(default getOrElse { throw flagNotFound })
    } catch {
      case e: Throwable =>
        log.log(Level.SEVERE,
          s"Flag $name default cannot be read",
          e)
        throw e
    }
  }

  def usageString: String = {
    val defaultOrUsageStr = defaultOrUsage match {
      case Left(_) => runDefaultString
      case Right(usage) => usage
    }
    s"  -$name='$defaultOrUsageStr': $help"
  }

  private[this] def runDefaultString = {
    try {
      defaultString()
    } catch {
      case e: Throwable => s"Error in reading default value for flag=$name.  See logs for exception"
    }
  }

  /**
   * String representation of this flag in -foo='bar' format,
   * suitable for being used on the command line.
   */
  override def toString: String = {
    valueOrDefault match {
      case None => "-" + name + "=unset"
      case Some(v) => "-" + name + "='" + flaggable.show(v).replaceAll("'", "'\"'\"'") + "'"
    }
  }

  /** Parse value `raw` into this flag. */
  def parse(raw: String): Unit = {
    value = Some(flaggable.parse(raw))
    _parsingDone = true
  }

  /** Parse this flag with no argument. */
  def parse(): Unit = {
    value = flaggable.default
    _parsingDone = true
  }

  private[app] def finishParsing(): Unit = {
    _parsingDone = true
  }

  /** Indicates whether or not the flag is valid without an argument. */
  def noArgumentOk: Boolean = flaggable.default.isDefined
}

object Flags {
  private[app] val FlagValueRequiredMessage = "flag value is required"
  private[app] val FlagUndefinedMessage = "flag undefined"

  sealed trait FlagParseResult

  /**
   * Indicates successful flag parsing.
   *
   * @param remainder A remainder list of unparsed arguments.
   */
  case class Ok(remainder: Seq[String]) extends FlagParseResult

  /**
   * Indicates that a help flag (i.e. -help) was encountered
   *
   * @param usage A string containing the application usage instructions.
   */
  case class Help(usage: String) extends FlagParseResult

  /**
   * Indicates that an error occurred during flag-parsing.
   *
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
class Flags(argv0: String, includeGlobal: Boolean, failFastUntilParsed: Boolean) {
  import com.twitter.app.Flags._

  def this(argv0: String, includeGlobal: Boolean) = this(argv0, includeGlobal, false)
  def this(argv0: String) = this(argv0, false)

  // thread-safety is provided by synchronization on `this`
  private[this] val flags = new mutable.HashMap[String, Flag[_]]

  @volatile private[this] var cmdUsage = ""

  // Add a help flag by default
  private[this] val helpFlag = this("help", false, "Show this help")


  def reset(): Unit = synchronized {
    flags.foreach { case (_, f) => f.reset() }
  }

  private[app] def finishParsing(): Unit = {
    flags.values.foreach { _.finishParsing() }
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
    while (i < args.length) {
      val a = args(i)
      i += 1
      if (a == "--") {
        remaining ++= args.slice(i, args.length)
        i = args.length
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
          case Array(k) if i == args.length =>
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
    finishParsing()

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
  def add(f: Flag[_]): Unit = synchronized {
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
  def apply[T: Flaggable](name: String, default: => T, help: String): Flag[T] = {
    val f = new Flag[T](name, help, default, failFastUntilParsed)
    add(f)
    f
  }

  /**
   * Add a named flag with a help message.
   *
   * @param name The name of the flag.
   * @param help The help string of the flag.
   */
  def apply[T](name: String, help: String)(implicit _f: Flaggable[T], m: Manifest[T]): Flag[T] = {
    val f = new Flag[T](name, help, m.toString, failFastUntilParsed)
    add(f)
    f
  }

  /**
   * A Java-friendly method for adding named flags.
   *
   * @param name The name of the flag.
   * @param default A default value.
   * @param help The help string of the flag.
   */
  def create[T](name: String, default: T, help: String, flaggable: Flaggable[T]): Flag[T] = {
    implicit val impl = flaggable
    apply(name, default, help)
  }

  /**
   * A Java-friendly way to create mandatory flags
   *
   * @param name the name passed on the command-line
   * @param help the help text explaining the purpose of the flag
   * @param usage a string describing the type of the flag, i.e.: Integer
   */
  def createMandatory[T](name: String, help: String, usage: String, flaggable: Flaggable[T]): Flag[T] = {
    implicit val impl = flaggable
    val f = new Flag[T](name, help, usage, failFastUntilParsed)
    add(f)
    f
  }

  /**
   * Set the flags' command usage; this is a message printed
   * before the flag definitions in the usage string.
   */
  def setCmdUsage(u: String): Unit = {
    cmdUsage = u
  }

  def usage: String = synchronized {
    val lines =
      for (k <- flags.keys.toArray.sorted)
      yield flags(k).usageString
    val globalLines = if (!includeGlobal) Seq.empty else {
      GlobalFlag.getAllOrEmptyArray(getClass.getClassLoader).map(_.usageString).sorted
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
class GlobalFlag[T] private[app](
    defaultOrUsage: Either[() => T, String],
    help: String)
    (implicit _f: Flaggable[T])
  extends Flag[T](null, help, defaultOrUsage, false) {

  override protected[this] def parsingDone: Boolean = true

  private[this] lazy val propertyValue =
    Option(System.getProperty(name)).flatMap { p =>
      try Some(flaggable.parse(p)) catch {
        case NonFatal(exc) =>
          GlobalFlag.log.log(
            java.util.logging.Level.SEVERE,
            "Failed to parse system property "+name+" as flag",
            exc)
          None
      }
    }

  /**
   * $java_declaration
   *
   * @param default the default value used if the value is not specified by the user.
   * @param help documentation regarding usage of this [[Flag]].
   */
  def this(default: T, help: String)(implicit _f: Flaggable[T]) = this(Left(() => default), help)

  /**
   * $java_declaration
   *
   * @param help documentation regarding usage of this [[Flag]].
   */
  def this(help: String)(implicit _f: Flaggable[T], m: Manifest[T]) = this(Right(m.toString), help)

  // Unfortunately, `getClass` in the the extends... above
  // doesn't give the right answer.
  override val name: String = getClass.getName.stripSuffix("$")

  protected override def getValue: Option[T] = super.getValue match {
    case v@Some(_) => v
    case _ => propertyValue
  }

  /**
   * Used by [[Flags.parseArgs]] to initialize [[Flag]] values.
   *
   * @note Called via reflection assuming it will be a `static`
   *       method on a singleton `object`. This causes problems
   *       for Java developers who want to create a [[GlobalFlag]]
   *       as there is no good means for them to have it be a
   *       `static` method. Thus, Java devs must add a method
   *       `public static Flag<?> globalFlagInstance()` which
   *       returns the singleton instance of the flag.
   *       See [[JavaGlobalFlag]] for more details.
   */
  def getGlobalFlag: Flag[_] = this
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
        case _: ClassNotFoundException
          | _: NoSuchMethodException
          | _: IllegalArgumentException => None
      }

    tryMethod(f, "getGlobalFlag").orElse {
      // fallback for GlobalFlags declared in Java
      tryMethod(f + "$", "globalFlagInstance")
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
        log.log(java.util.logging.Level.SEVERE,
          "failure reading in flags",
          e)
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
          get(info.className.dropRight(1)) match {
            case Some(f) => flags += f
            case None => println("failed for " + info.className)
          }
        }
      } catch {
        case _: IllegalStateException
             | _: NoClassDefFoundError
             | _: ClassNotFoundException =>
      }
    }
    flags
  }
}
