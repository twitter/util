package com.twitter.app

import scala.collection.immutable.TreeSet
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.control.NonFatal

/**
 * Exception thrown upon flag-parsing failure. Should typically lead to process
 * death, since continued execution would run the risk of unexpected behavior on
 * account of incorrectly-interpreted or malformed flag values.
 *
 * @param message A string name of the flag for which parsing failed.
 * @param cause The underlying `java.lang.Throwable` that caused this exception.
 */
case class FlagParseException(message: String, cause: Throwable = null)
    extends Exception(message, cause)

case class FlagUsageError(usage: String) extends Exception(usage)

class FlagValueRequiredException extends Exception(Flags.FlagValueRequiredMessage)

class FlagUndefinedException extends Exception(Flags.FlagUndefinedMessage)

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
  // when a flag with the same name as a previously added flag is added, track it here
  private[this] val duplicates = new java.util.concurrent.ConcurrentSkipListSet[String]()

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
  def parseArgs(args: Array[String], allowUndefinedFlags: Boolean = false): FlagParseResult =
    synchronized {
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
          a drop 1 split ("=", 2) match {
            // There seems to be a bug Scala's pattern matching
            // optimizer that leaves `v' dangling in the last case if
            // we make this a wildcard (Array(k, _@_*))
            case Array(k) if !hasFlag(k) =>
              if (allowUndefinedFlags)
                remaining += a
              else
                return Error(
                  "Error parsing flag \"%s\": %s".format(k, FlagUndefinedMessage)
                )

            // Flag isn't defined
            case Array(k, _) if !hasFlag(k) =>
              if (allowUndefinedFlags)
                remaining += a
              else
                return Error(
                  "Error parsing flag \"%s\": %s".format(k, FlagUndefinedMessage)
                )

            // Optional argument without a value
            case Array(k) if flag(k).noArgumentOk =>
              flag(k).parse()

            // Mandatory argument without a value and with no more arguments.
            case Array(k) if i == args.length =>
              return Error(
                "Error parsing flag \"%s\": %s".format(k, FlagValueRequiredMessage)
              )

            // Mandatory argument with another argument
            case Array(k) =>
              i += 1
              try flag(k).parse(args(i - 1))
              catch {
                case NonFatal(e) =>
                  return Error(
                    "Error parsing flag \"%s\": %s".format(k, e.getMessage)
                  )
              }

            // Mandatory k=v
            case Array(k, v) =>
              try flag(k).parse(v)
              catch {
                case e: Throwable =>
                  return Error(
                    "Error parsing flag \"%s\": %s".format(k, e.getMessage)
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
        Ok(remaining.toSeq)
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
  def parse(args: Array[String], undefOk: Boolean = false): Seq[String] =
    parseArgs(args, undefOk) match {
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
        System.err.println(usage)
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
    if (flags.contains(f.name)) {
      System.err.printf("Flag: \"%s\" already defined!\n", f.name)
      duplicates.add(f.name)
    }
    flags(f.name) = f.withFailFast(failFastUntilParsed)
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
  def apply[T](name: String, help: String)(implicit _f: Flaggable[T], m: ClassTag[T]): Flag[T] = {
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
    implicit val impl: Flaggable[T] = flaggable
    apply(name, default, help)
  }

  /**
   * A Java-friendly way to create mandatory flags
   *
   * @param name the name passed on the command-line
   * @param help the help text explaining the purpose of the flag
   * @param usage a string describing the type of the flag, i.e.: Integer
   */
  def createMandatory[T](
    name: String,
    help: String,
    usage: String,
    flaggable: Flaggable[T]
  ): Flag[T] = {
    implicit val impl: Flaggable[T] = flaggable
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
    val globalLines =
      if (!includeGlobal) Seq.empty
      else {
        GlobalFlag.getAllOrEmptyArray(getClass.getClassLoader).map(_.usageString).sorted
      }

    val cmd = if (cmdUsage.nonEmpty) cmdUsage + "\n" else "usage: "

    cmd + argv0 + " [<flag>...]\n" +
      "flags:\n" +
      lines.mkString("\n") +
      (if (globalLines.isEmpty) ""
       else "\nglobal flags:\n" + globalLines.mkString("\n"))
  }

  /**
   * Get all of the flags known to this Flags instance.
   *
   * @param includeGlobal If true, all registered
   * [[com.twitter.app.GlobalFlag GlobalFlags]] will be included
   * in output. Defaults to the `includeGlobal` settings of this instance.
   * @param classLoader The `java.lang.ClassLoader` used to fetch
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

    (set.map { _.toString + " \\" }, unset.map { _.toString + " \\" })
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

  /** Return the set of any registered duplicated flag names. */
  protected[twitter] def registeredDuplicates: Set[String] =
    duplicates.asScala.toSet
}
