package com.twitter.app

import com.twitter.conversions.time._
import com.twitter.util._
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger
import scala.collection.JavaConverters._
import scala.collection.mutable

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
  val name: String = getClass.getName.stripSuffix("$")
  /** The [[com.twitter.app.Flags]] instance associated with this application */
  //failfastOnFlagsNotParsed is called in the ctor of App.scala here which is a bad idea
  //as things like this can happen http://stackoverflow.com/questions/18138397/calling-method-from-constructor
  val flag: Flags = new Flags(name, includeGlobal = true, failfastOnFlagsNotParsed)

  private var _args = Array[String]()
  /** The remaining, unparsed arguments */
  def args: Array[String] = _args

  /** Whether or not to accept undefined flags */
  protected def allowUndefinedFlags: Boolean = false

  /**
   * Users of this code should override this to `true` so that
   * you fail-fast instead of being surprised at runtime by code that
   * is reading from flags before they have been parsed.
   *
   * Ideally this would default to `true`, however, in order to avoid
   * breaking existing users, it was introduced using `false`.
   */
  protected def failfastOnFlagsNotParsed: Boolean = false

  protected def exitOnError(reason: String): Unit = {
    System.err.println(reason)
    close()
    System.exit(1)
  }

  private val inits: mutable.Buffer[() => Unit] = mutable.Buffer.empty
  private val premains: mutable.Buffer[() => Unit] = mutable.Buffer.empty
  private val exits: ConcurrentLinkedQueue[Closable] = new ConcurrentLinkedQueue
  private val lastExits: ConcurrentLinkedQueue[Closable] = new ConcurrentLinkedQueue
  private val postmains: ConcurrentLinkedQueue[() => Unit] = new ConcurrentLinkedQueue

  // finagle isn't available here, so no DefaultTimer
  protected lazy val shutdownTimer: Timer = new JavaTimer(isDaemon = true)

  /**
   * Invoke `f` before anything else (including flag parsing).
   */
  protected final def init(f: => Unit): Unit = {
    inits += (() => f)
  }

  /**
   * Invoke `f` right before the user's main is invoked.
   */
  protected final def premain(f: => Unit): Unit = {
    premains += (() => f)
  }

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
  final def closeOnExit(closable: Closable): Unit = exits.add(closable)

  /**
   * Register a `closable` to be closed on application shutdown after those registered
   * via `clostOnExit`.
   *
   * @note Application shutdown occurs in two sequential phases to allow explicit
   *       encoding of resource lifecycle relationships. Concretely this is useful
   *       for encoding that a monitoring resource should outlive a monitored
   *       resource.
   *
   *       In all cases, the close deadline is enforced.
   */
  final def closeOnExitLast(closable: Closable): Unit = lastExits.add(closable)

  /**
   * Invoke `f` when shutdown is requested. Exit hooks run in parallel and are
   * executed after all postmains complete. The thread resumes when all exit
   * hooks complete or `closeDeadline` expires.
   */
  protected final def onExit(f: => Unit): Unit = {
    closeOnExit {
      Closable.make { deadline => // close() ensures that this deadline is sane
        FuturePool.unboundedPool(f).by(shutdownTimer, deadline)
      }
    }
  }

  /**
   * Invoke `f` after the user's main has exited.
   */
  protected final def postmain(f: => Unit): Unit = {
    postmains.add(() => f)
  }

  /**
   * Notify the application that it may stop running.
   * Returns a Future that is satisfied when the App has been torn down or errors at the deadline.
   */
  final def close(deadline: Time): Future[Unit] = closeAwaitably {
    closeDeadline = deadline.max(Time.now + MinGrace)
    val firstPhase = Closable.all(exits.asScala.toSeq: _*)
      .close(closeDeadline)
      .by(shutdownTimer, closeDeadline)

    firstPhase.transform { _ => Closable.all(lastExits.asScala.toSeq: _*).close(closeDeadline) }
  }

  final def main(args: Array[String]): Unit = {
    try {
      nonExitingMain(args)
    } catch {
      case FlagUsageError(reason) =>
        exitOnError(reason)
      case FlagParseException(reason, _) =>
        exitOnError(reason)
      case e: Throwable =>
        e.printStackTrace()
        exitOnError("Exception thrown in main on startup")
    }
  }

  final def nonExitingMain(args: Array[String]): Unit = {
    App.register(this)

    for (f <- inits) f()

    flag.parseArgs(args, allowUndefinedFlags) match {
      case Flags.Ok(remainder) =>
        _args = remainder.toArray
      case Flags.Help(usage) =>
        throw FlagUsageError(usage)
      case Flags.Error(reason) =>
        throw FlagParseException(reason)
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

    // We discard this future but we `Await.result` on `this` which is a
    // `CloseAwaitable`, and this means the thread waits for the future to
    // complete.
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

  private[app] def register(app: App): Unit =
    ref.getAndSet(Some(app)).foreach { existing =>
      log.warning(
        s"Multiple com.twitter.app.App main methods called. ${existing.name}, then ${app.name}")
    }
}
