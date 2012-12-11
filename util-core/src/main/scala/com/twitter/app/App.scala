package com.twitter.app

import scala.collection.mutable
import com.twitter.util.{Try, Throw, Return}

/**
 * A composable application trait that includes flag parsing as well
 * as basic application lifecycle (pre- and post- main). Flag parsing
 * is done via [[com.twitter.app.Flags]], an instance of which is
 * defined in the member `flag`. Applications should be constructed
 * with modularity in mind, and common functionality should be
 * extracted into mixins.
 *
 * {{{
 * object MyApp extends App {
 *   val n = flag("n", 100, "Number of items to process")
 *
 *   def main() {
 *     for (i <- 0 until n()) process(i)
 *   }
 * }
 * }}}
 *
 * Note that a missing `main` is OK: mixins may provide behavior that
 * does not require defining a custom `main` method.
 */
trait App {
  /** The name of the application, based on the classname */
  val name = getClass.getName.reverse.dropWhile(_ == '$').reverse
  /** The [[com.twitter.app.Flags]] instance associated with this application */
  val flag = new Flags(name)
  private var _args = Array[String]()
  /** The remaining, unparsed arguments */
  def args = _args

  private val premains = mutable.Buffer[() => Unit]()
  private val postmains = mutable.Buffer[() => Unit]()
  private val inits = mutable.Buffer[() => Unit]()

  /**
   * Invoke `f` right before the user's main is invoked.
   */
  protected def premain(f: => Unit) {
    premains += (() => f)
  }

  /**
   * Invoke `f` after the user's main has exited.
   */
  protected def postmain(f: => Unit) {
    postmains += (() => f)
  }

  /**
   * Invoke `f` before anything else (including flag parsing).
   */
  protected def init(f: => Unit) {
    inits += (() => f)
  }

  // TODO: onExit?

  final def main(args: Array[String]) {
    for (f <- inits) f()

    try {
      val rem = flag.parse(args)
      _args = rem.toArray
    } catch {
      case FlagUsageError(usage) =>
        System.err.println(usage)
        System.exit(1)
      case e@FlagParseException(k, cause) =>
        System.err.println("Error parsing flag %s: %s".format(k, cause.getMessage))
        System.err.println(flag.usage)
        System.exit(1)
      case e =>
        System.err.println("Error parsing flags: %s".format(e.getMessage))
        System.err.println(flag.usage)
        System.exit(1)
    }

    for (f <- premains) f()

    // Invoke main() if it exists.
    try {
      getClass.getMethod("main").invoke(this)
    } catch {
      case _: NoSuchMethodException =>
        // This is OK. It's possible to define traits that only use pre/post mains.
    }
    for (f <- postmains) f()
  }
}
