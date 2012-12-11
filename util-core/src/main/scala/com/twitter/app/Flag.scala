package com.twitter.app

import com.twitter.util.{Try, Return, Throw, Duration, StorageUnit}
import scala.collection.JavaConverters._
import scala.collection.mutable.{HashMap, ArrayBuffer}
import java.net.InetSocketAddress

trait Flaggable[T] {
  def parse(s: String): T
  def show(t: T): String = t.toString
  def default: Option[T] = None
}

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
  implicit val ofFloat = mandatory(_.toFloat)
  implicit val ofDouble = mandatory(_.toDouble)
  implicit val ofDuration = mandatory(Duration.parse(_))
  implicit val ofStorageUnit = mandatory(StorageUnit.parse(_))

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
}

case class FlagParseException(which: String, cause: Throwable)
  extends Exception(cause)
case class FlagUsageError(usage: String) extends Exception
class FlagValueRequiredException extends Exception("flag value is required")
class FlagUndefinedException extends Exception("flag undefined")

/**
 * A single flag, typically instantiated by a
 * [[com.twitter.app.Flags]] instance. Its current value is extracted
 * with `apply()`.
 */
case class Flag[T: Flaggable](name: String, help: String, default: T) {
  private[this] val flaggable = implicitly[Flaggable[T]]
  @volatile private[this] var value: Option[T] = None

  /**
   * Return this flag's current value. The default value is returned
   * when the flag has not otherwise been set.
   */
  def apply(): T = get getOrElse default

  /** Reset this flag's value */
  def reset() { value = None }
  /** True if the flag has been set */
  def isDefined = value.isDefined
  /** Get the value if it has been set */
  def get: Option[T] = value
  /** String representation of this flag's default value */
  def defaultString = flaggable.show(default)

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
 */
class Flags(argv0: String) {
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
      if (a startsWith "-") {
        a drop 1 split("=", 2) match {
          // There seems to be a bug Scala's pattern matching
          // optimizer that leaves `v' dangling in the last case if
          // we make this a wildcard (Array(k, _@_*))
          case Array(k) if !(flags contains k) =>
            if (undefOk)
              remaining += a
            else
              throw FlagParseException(k, new FlagUndefinedException)

          // Flag isn't defined
          case Array(k, _) if !(flags contains k) =>
            if (undefOk)
              remaining += a
            else
              throw FlagParseException(k, new FlagUndefinedException)

          // Optional argument without a value
          case Array(k) if flags(k).noArgumentOk =>
            flags(k).parse()

          // Mandatory argument without a value and with no more arguments.
          case Array(k) if i == args.size =>
            throw FlagParseException(k, new FlagValueRequiredException)

          // Mandatory argument with another argument
          case Array(k) =>
            i += 1
            try flags(k).parse(args(i-1)) catch {
              case e => throw FlagParseException(k, e)
            }

          // Mandatory k=v
          case Array(k, v) =>
            try flags(k).parse(v) catch {
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

  def apply[T: Flaggable](name: String, default: T, help: String) = {
    val f = new Flag[T](name, help, default)
    add(f)
    f
  }

  def usage: String = synchronized {
    val lines = for (k <- flags.keys.toArray.sorted) yield {
      val f = flags(k)
      "  -%s='%s': %s".format(k, f.defaultString, f.help)
    }

    argv0+"\n"+(lines mkString "\n")
  }
}
