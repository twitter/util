package com.twitter.app

import com.twitter.util.{Try, Throw, Return}
import java.lang.reflect.InvocationTargetException
import scala.collection.mutable

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
  val flag = new Flags(name, includeGlobal = true)
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

  /**
   * Create a new shutdown hook. As such, these will be started in
   * no particular order and run concurrently.
   */
  protected def onExit(f: => Unit) {
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run() = f
    })
  }

  final def main(args: Array[String]) {
    for (f <- inits) f()

    _args = flag.parseOrExit1(args).toArray

    for (f <- premains) f()

    // Invoke main() if it exists.
    try {
      getClass.getMethod("main").invoke(this)
    } catch {
      case _: NoSuchMethodException =>
        // This is OK. It's possible to define traits that only use pre/post mains.

      case e: InvocationTargetException => throw e.getCause
    }
    for (f <- postmains) f()
  }
}
