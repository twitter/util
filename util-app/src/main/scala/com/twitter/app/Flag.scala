package com.twitter.app

import com.twitter.util._
import com.twitter.util.registry.GlobalRegistry
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

object Flag {

  // stores Local overrides for a Flag's value
  private val localFlagValues = new Local[Map[Flag[_], Any]]
  private[app] val EmptyRequired = "__EMPTY_REQUIRED_FLAG"

  private val log = Logger.getLogger("")

}

/**
 * A single command-line flag, instantiated by a [[com.twitter.app.Flags]]
 * instance.
 *
 * The current value can be extracted via [[apply()]], [[get]], and
 * [[getWithDefault]]. [[com.twitter.util.Local Local]]-scoped modifications
 * of their values, which can be useful for tests, can be done by calls to
 * [[let]] and [[letClear]].
 *
 * Using a `String`-typed [[Flag]], `myFlag`, which is unset and has a default
 * value of "DEFAULT" as an example:
 *
 * {{{
 * myFlag.isDefined // => false
 * myFlag.get // => None
 * myFlag.getWithDefault // => Some("DEFAULT")
 * myFlag() // => "DEFAULT"
 *
 * myFlag.let("a value") {
 *   myFlag.isDefined // => true
 *   myFlag.get // => Some("a value")
 *   myFlag.getWithDefault // => Some("a value")
 *   myFlag() // => "a value"
 *
 *   myFlag.letClear {
 *     myFlag.isDefined // => false
 *     myFlag.get // => None
 *     myFlag.getWithDefault // => Some("DEFAULT")
 *     myFlag() // => "DEFAULT"
 *   }
 * }
 * }}}
 *
 * @see [[com.twitter.app.Flags]] for information on how flags can be set by
 *     the command line.
 * @see [[com.twitter.app.GlobalFlag]]
 */
class Flag[T: Flaggable] private[app] (
  val name: String,
  val help: String,
  defaultOrUsage: Either[() => T, String],
  failFastUntilParsed: Boolean) {
  import com.twitter.app.Flag._
  import java.util.logging._

  private[app] def this(name: String, help: String, default: => T, failFastUntilParsed: Boolean) =
    this(name, help, Left(() => default), failFastUntilParsed)

  private[app] def this(name: String, help: String, usage: String, failFastUntilParsed: Boolean) =
    this(name, help, Right(usage), failFastUntilParsed)

  private[twitter] def this(name: String, help: String, default: => T) =
    this(name, help, default, false)

  private[twitter] def this(name: String, help: String, usage: String) =
    this(name, help, usage, false)

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
      case (Some(map), None) => map - this
      case (None, Some(v)) => Map(this -> v)
      case (None, None) => Map.empty[Flag[_], Any]
    }
    if (updatedMap.isEmpty)
      localFlagValues.clear()
    else
      localFlagValues.update(updatedMap)
  }

  @volatile private[this] var failFast = failFastUntilParsed
  private[app] def withFailFast(fail: Boolean): this.type = {
    failFast = fail
    this
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
      case lv @ Some(_) => lv
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
          throw new RuntimeException(s"Could not run default function for flag $name", e)
      }
  }

  private def valueOrDefault: Option[T] = getValue match {
    case v @ Some(_) => v
    case None => default
  }

  private[this] def flagNotFound: IllegalArgumentException =
    new IllegalArgumentException("flag '%s' not found".format(name))

  /**
   * Override the value of this flag with `t`, only for the scope of the current
   * [[com.twitter.util.Local]] for the given function `f`.
   *
   * @see [[letParse]]
   * @see [[letClear]]
   */
  def let[R](t: T)(f: => R): R =
    let(Some(t), f)

  /**
   * Override the value of this flag with `arg` [[String] parsed to this [[Flag]]'s [[T]] type,
   * only for the scope of the current [[com.twitter.util.Local]] for the given function `f`.
   *
   * @see [[let]]
   * @see [[letClear]]
   */
  def letParse[R](arg: String)(f: => R): R =
    let(flaggable.parse(arg))(f)

  /**
   * Unset the value of this flag, such that [[isDefined]] will return `false`,
   * only for the scope of the current [[com.twitter.util.Local]] for the
   * given function `f`.
   *
   * @see [[let]]
   * @see [[letParse]]
   */
  def letClear[R](f: => R): R =
    let(None, f)

  private[this] def let[R](t: Option[T], f: => R): R = {
    val prev = localValue
    setLocalValue(t)
    try f
    finally {
      setLocalValue(prev)
    }
  }

  /**
   * Return this flag's current value. The default value is returned
   * when the flag has not otherwise been set.
   */
  def apply(): T = {
    if (!parsingDone) {
      if (failFast)
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
        log.log(Level.SEVERE, s"Flag $name default cannot be read", e)
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
