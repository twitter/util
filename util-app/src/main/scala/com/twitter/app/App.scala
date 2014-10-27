package com.twitter.app

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger

import scala.collection.JavaConverters._
import scala.collection.mutable

import com.twitter.conversions.time._
import com.twitter.util._

/**
 * A composable application trait that includes flag parsing as well
 * as basic application lifecycle (pre- and post- main). Flag parsing
 * is done via [[com.twitter.app.Flags]], an instance of which is
 * defined in the member `flag`. Applications should be constructed
 * with modularity in mind, and common functionality should be
 * extracted into mixins.
 *
 * Flags should only be constructed in the constructor, and should only be read
 * in the premain or later, after they have been parsed.
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
trait App extends Closable with CloseAwaitably {
  /** The name of the application, based on the classname */
  val name = getClass.getName stripSuffix "$"
  /** The [[com.twitter.app.Flags]] instance associated with this application */
  //failfastOnFlagsNotParsed is called in the ctor of App.scala here which is a bad idea
  //as things like this can happen http://stackoverflow.com/questions/18138397/calling-method-from-constructor
  val flag = new Flags(name, includeGlobal = true, failfastOnFlagsNotParsed)
  private var _args = Array[String]()
  /** The remaining, unparsed arguments */
  def args = _args

  /** Whether or not to accept undefined flags */
  protected def allowUndefinedFlags = false

  protected def failfastOnFlagsNotParsed = false

  private val inits     = mutable.Buffer[() => Unit]()
  private val premains  = mutable.Buffer[() => Unit]()
  private val exits     = new ConcurrentLinkedQueue[Closable]
  private val postmains = new ConcurrentLinkedQueue[() => Unit]

  /**
   * Invoke `f` before anything else (including flag parsing).
   */
  protected final def init(f: => Unit) {
    inits += (() => f)
  }

  /**
   * Invoke `f` right before the user's main is invoked.
   */
  protected final def premain(f: => Unit) {
    premains += (() => f)
  }

  // onExit may use this pool to run hooks concurrently
  private lazy val exitPool = FuturePool.unboundedPool

  // finagle isn't available here, so no DefaultTimer
  private val exitTimer = new JavaTimer(isDaemon = true)

  /** Minimum duration to allow for exits to be processed. */
  final val MinGrace: Duration = 1.second

  /**
   * Default amount of time to wait for shutdown.
   * This value is not used as a default if `close()` is called without parameters. It simply
   * provides a default value to be passed as `close(grace)`.
   */
  def defaultCloseGracePeriod: Duration = Duration.Zero

  /**
   * The actual close grace period.
   */
  @volatile private[this] var closeDeadline = Time.Top

  /**
   * Close `closable` when shutdown is requested. Closables are closed in parallel.
   */
  protected final def closeOnExit(closable: Closable) {
    exits.add(closable)
  }

  /**
   * Invoke `f` when shutdown is requested. Exit hooks run in parallel and all must complete before
   * postmains are executed.
   */
  protected final def onExit(f: => Unit) {
    closeOnExit {
      Closable.make { deadline => // close() ensures that this deadline is sane
        exitPool(f).within(exitTimer, deadline - Time.now)
      }
    }
  }

  /**
   * Invoke `f` after the user's main has exited.
   */
  protected final def postmain(f: => Unit) {
    postmains.add(() => f)
  }

  /**
   * Notify the application that it may stop running.
   * Returns a Future that is satisfied when the App has been torn down or errors at the deadline.
   */
  final def close(deadline: Time): Future[Unit] = closeAwaitably {
    closeDeadline = deadline max (Time.now + MinGrace)
    Closable.all(exits.asScala.toSeq: _*).close(closeDeadline)
  }

  final def main(args: Array[String]) {
    App.register(this)

    for (f <- inits) f()

    flag.parseArgs(args, allowUndefinedFlags) match {
      case Flags.Ok(remainder) =>
        _args = remainder.toArray

      case Flags.Help(usage) =>
        System.err.println(usage)
        System.exit(1)

      case Flags.Error(reason) =>
        System.err.println(reason)
        System.exit(1)
    }

    for (f <- premains) f()

    // Get a main() if it's defined. It's possible to define traits that only use pre/post mains.
    val mainMethod =
      try Some(getClass.getMethod("main"))
      catch { case _: NoSuchMethodException => None }

    // Invoke main() if it exists.
    mainMethod foreach { method =>
      try method.invoke(this)
      catch { case e: InvocationTargetException => throw e.getCause }
    }

    for (f <- postmains.asScala) f()

    close(defaultCloseGracePeriod)

    // The deadline to 'close' is advisory; we enforce it here.
    Await.result(this, closeDeadline - Time.now)
  }
}

object App {
  private[this] val log = Logger.getLogger(getClass.getName)
  private[this] val ref = new AtomicReference[Option[App]](None)

  /**
   * The currently registered App, if any. While the expectation is that there
   * will be a single running App per process, the most-recently registered
   * App will be returned in the event that more than one exists.
   */
  def registered: Option[App] = ref.get

  private[App] def register(app: App): Unit =
    ref.getAndSet(Some(app)).foreach { existing =>
      log.warning(
        s"Multiple com.twitter.app.App main methods called. ${existing.name}, then ${app.name}")
    }
}
