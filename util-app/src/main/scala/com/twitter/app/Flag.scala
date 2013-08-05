package com.twitter.app

import com.twitter.util._
import scala.collection.mutable.{ArrayBuffer, HashMap}
import scala.collection.immutable.TreeSet
import java.net.InetSocketAddress

/**
 * A typeclass providing evidence for parsing type `T`
 * as a flag.
 */
trait Flaggable[T] {
  def parse(s: String): T
  def show(t: T): String = t.toString
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
      if (addr.getAddress.isAnyLocalAddress)
        ":%d".format(addr.getPort)
      else
        "%s:%s".format(addr.getHostName, addr.getPort)
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

    def parse(in: String): Map[K, V] = in.split(",") map { pair =>
      pair.split("=") match {
        case Array(k, v) => (kflag.parse(k), vflag.parse(v))
        case _ => throw new IllegalArgumentException("not a 'k=v'")
      }
    } toMap

    override def show(out: Map[K, V]) = {
      out.toSeq map { case (k, v) => k.toString + "=" + v.toString } mkString(",")
    }
  }
}

case class FlagParseException(which: String, cause: Throwable)
  extends Exception(cause)
case class FlagUsageError(usage: String) extends Exception
class FlagValueRequiredException extends Exception("flag value is required")
class FlagUndefinedException extends Exception("flag undefined")

/**
 * A single flag, instantiated by a [[com.twitter.app.Flags]] instance.
 * Its current value is extracted with `apply()`.
 *
 * @see [[com.twitter.app.Flags]]
 */
class Flag[T: Flaggable] private[app](val name: String, val help: String, default: => T) {
  protected val flaggable = implicitly[Flaggable[T]]
  @volatile private[this] var value: Option[T] = None
  protected def getValue: Option[T] = value

  /**
   * Return this flag's current value. The default value is returned
   * when the flag has not otherwise been set.
   */
  def apply(): T = get getOrElse default

  /** Reset this flag's value */
  def reset() { value = None }
  /** True if the flag has been set */
  def isDefined = getValue.isDefined
  /** Get the value if it has been set */
  def get: Option[T] = getValue
  /** String representation of this flag's default value */
  def defaultString = flaggable.show(default)

  /**
   * String representation of this flag in -foo='bar' format,
   * suitable for being used on the command line.
   */
  override def toString = {
    "-" + name + "='" + flaggable.show(apply()).replaceAll("'", "'\"'\"'") + "'"
  }

  /** Parse value `raw` into this flag. */
  def parse(raw: String) {
    value = Some(flaggable.parse(raw))
  }

  /** Parse this flag with no argument. */
  def parse() {
    value = flaggable.default
  }

  def noArgumentOk = flaggable.default.isDefined
}

/**
 * A simple flags implementation. We support only two formats:
 *
 *    for flags with optional values (booleans):
 *      -flag, -flag=value
 *    for flags with required values:
 *      -flag[= ]value
 *
 * That's it. These can be parsed without ambiguity.
 *
 * There is no support for mandatory arguments: That is not what
 * flags are for.
 *
 * Flags' `apply` adds a new flag to to the flag set, so it is idiomatic
 * to assign instances of `Flags` to a singular `flag`:
 *
 * {{{
 *   val flag = new Flags("myapp")
 *   val i = flag("i", 123, "iteration count")
 * }}}
 *
 * Global flags, detached from a particular `Flags` instance, but
 * accessible to all, are defined by [[com.twitter.app.GlobalFlag]].
 */
class Flags(argv0: String, includeGlobal: Boolean) {
  def this(argv0: String) = this(argv0, false)

  private[this] val flags = new HashMap[String, Flag[_]]

  // Add a help flag by default
  private[this] val helpFlag = this("help", false, "Show this help")

  def add(f: Flag[_]) = synchronized {
    if (flags contains f.name)
      System.err.printf("Flag %s already defined!\n", f.name)
    flags(f.name) = f
  }

  def reset() = synchronized {
    flags foreach { case (_, f) => f.reset() }
  }

  private[this] def resolveGlobalFlag(f: String) =
    if (includeGlobal) GlobalFlag.get(f) else None

  private[this] def resolveFlag(f: String): Option[Flag[_]] =
    synchronized { flags.get(f) orElse resolveGlobalFlag(f) }

  private[this] def hasFlag(f: String) = resolveFlag(f).isDefined
  private[this] def flag(f: String) = resolveFlag(f).get

  def parse(
    args: Array[String],
    undefOk: Boolean = false
  ): Seq[String] = synchronized {
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
            if (undefOk)
              remaining += a
            else
              throw FlagParseException(k, new FlagUndefinedException)

          // Flag isn't defined
          case Array(k, _) if !hasFlag(k) =>
            if (undefOk)
              remaining += a
            else
              throw FlagParseException(k, new FlagUndefinedException)

          // Optional argument without a value
          case Array(k) if flag(k).noArgumentOk =>
            flags(k).parse()

          // Mandatory argument without a value and with no more arguments.
          case Array(k) if i == args.size =>
            throw FlagParseException(k, new FlagValueRequiredException)

          // Mandatory argument with another argument
          case Array(k) =>
            i += 1
            try flag(k).parse(args(i-1)) catch {
              case NonFatal(e) => throw FlagParseException(k, e)
            }

          // Mandatory k=v
          case Array(k, v) =>
            try flag(k).parse(v) catch {
              case e => throw FlagParseException(k, e)
            }
        }
      } else {
        remaining += a
      }
    }

    if (helpFlag())
      throw FlagUsageError(usage)

    remaining
  }

  def apply[T: Flaggable](name: String, default: => T, help: String) = {
    val f = new Flag[T](name, help, default)
    add(f)
    f
  }

  def usage: String = synchronized {
    val lines = for (k <- flags.keys.toArray.sorted) yield {
      val f = flags(k)
      "  -%s='%s': %s".format(k, f.defaultString, f.help)
    }
    val globalLines = if (!includeGlobal) Seq() else {
      for (f <- GlobalFlag.getAll(getClass.getClassLoader))
        yield "  -%s='%s': %s".format(f.name, f.defaultString, f.help)
    }

    argv0+"\n"+(lines mkString "\n")+(
      if (globalLines.isEmpty) "" else "\nglobal flags:\n"+(globalLines mkString "\n")
    )
  }

  /**
   * Get all the flags known to this this Flags instance
   *
   * @param includeGlobal defaults to the includeGlobal settings of this instance
   * @param classLoader   needed to find global flags, defaults to this instance's class loader
   * @return all the flags known to this this Flags instance
   */
  def getAll(includeGlobal: Boolean = this.includeGlobal,
             classLoader: ClassLoader = this.getClass.getClassLoader): Iterable[Flag[_]] = synchronized {

    var flags = TreeSet[Flag[_]]()(Ordering.by(_.name)) ++ this.flags.valuesIterator

    if (includeGlobal) {
      flags ++= GlobalFlag.getAll(classLoader).iterator
    }

    flags
  }

  /**
   * Formats all the values of all flags known to this instance into a format suitable for logging
   *
   * @param includeGlobal see getAll above
   * @param classLoader   see getAll above
   * @return all the flag values in alphabetical order, grouped into (set, unset)
   */
  def formattedFlagValues(includeGlobal: Boolean = this.includeGlobal,
                          classLoader: ClassLoader = this.getClass.getClassLoader):
                          (Iterable[String], Iterable[String]) = {

    val (set, unset) = getAll(includeGlobal, classLoader).partition { _.get.isDefined }

    (set.map { _ + " \\" }, unset.map { _ + " \\" })
  }

  /**
   * Creates a string containing all the values of all flags known to this instance into a format suitable for logging
   *
   * @param includeGlobal set getAll above
   * @param classLoader   set getAll above
   * @return A string suitable for logging
   */
  def formattedFlagValuesString(includeGlobal: Boolean = this.includeGlobal,
                                classLoader: ClassLoader = this.getClass.getClassLoader): String = {
    val (set, unset) = formattedFlagValues(includeGlobal, classLoader)
    val lines = Seq("Set flags:") ++
      set ++
      Seq("Unset flags:") ++
      unset

    lines.mkString("\n")
  }

}

/**
 * Declare a global flag by extending this class with an object.
 *
 * {{{
 * object MyFlag extends GlobalFlag("my", "default value", "my global flag")
 * }}}
 *
 * All such global flag declarations in a given classpath are
 * visible, and are used by, [[com.twitter.app.App]].
 *
 * The name of the flag is the fully-qualified classname, for
 * example, the flag
 *
 * {{{
 * package com.twitter.server
 *
 * object port extends GlobalFlag(8080, "the TCP port to which we bind")
 * }}}
 *
 * is accessed by the name `com.twitter.server.port`.
 *
 * Global flags may also be set by Java system properties with keys
 * named in the same way, however values supplied by flags override
 * those supplied by system properties.
 *
 */
@GlobalFlagVisible
class GlobalFlag[T: Flaggable](default: T, help: String)
    extends Flag[T](null, help, default) {

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
