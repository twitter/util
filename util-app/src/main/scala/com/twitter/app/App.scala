package com.twitter.app

import com.twitter.app.lifecycle.Event._
import com.twitter.conversions.DurationOps._
import com.twitter.util._
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

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
 * import com.twitter.app.App
 *
 * object MyApp extends App {
 *   val n = flag("n", 100, "Number of items to process")
 *
 *   def main(): Unit = {
 *     for (i <- 0 until n()) process(i)
 *   }
 * }
 * }}}
 *
 * Note that a missing `main` is OK: mixins may provide behavior that
 * does not require defining a custom `main` method.
 *
 * @define LoadServiceApplyScaladocLink
 * [[com.twitter.app.LoadService.apply[T]()(implicitevidence\$1:scala\.reflect\.ClassTag[T]):Seq[T]* LoadService.apply]]
 */
trait App extends ClosableOnce with CloseOnceAwaitably with Lifecycle {

  /** The name of the application, based on the classname */
  def name: String = getClass.getName.stripSuffix("$")

  //failfastOnFlagsNotParsed is called in the ctor of App.scala here which is a bad idea
  //as things like this can happen https://stackoverflow.com/questions/18138397/calling-method-from-constructor
  private[this] val _flag: Flags =
    new Flags(name, includeGlobal = includeGlobalFlags, failfastOnFlagsNotParsed)

  /** The [[com.twitter.app.Flags]] instance associated with this application */
  final def flag: Flags = _flag

  private var _args = Array[String]()

  /** The remaining, unparsed arguments */
  def args: Array[String] = _args

  /** Whether or not to accept undefined flags */
  protected def allowUndefinedFlags: Boolean = false

  /** Whether or not to include [[GlobalFlag GlobalFlag's]] when [[Flags]] are parsed */
  protected def includeGlobalFlags: Boolean = true

  /**
   * Users of this code should override this to `true` so that
   * you fail-fast instead of being surprised at runtime by code that
   * is reading from flags before they have been parsed.
   *
   * Ideally this would default to `true`, however, in order to avoid
   * breaking existing users, it was introduced using `false`.
   */
  protected def failfastOnFlagsNotParsed: Boolean = false

  /** Exit on error with the given Throwable */
  protected def exitOnError(throwable: Throwable): Unit = {
    throwable.printStackTrace()
    throwable match {
      case _: CloseException =>
        // exception occurred while closing, do not attempt to close again
        System.err.println(throwable.getMessage)
        System.exit(1)
      case _ =>
        exitOnError("Exception thrown in main on startup")
    }
  }

  /** Exit on error with the given `reason` String */
  protected def exitOnError(reason: String): Unit = {
    exitOnError(reason, "")
  }

  /**
   * The details Fn may be an expensive operation (which could fail). We want to
   * ensure that we've at least written the `reason` field to System.err before
   * attempting to write the `detail` field so that users will at a minimum see the
   * `reason` for the exit regardless of any extra details.
   */
  protected def exitOnError(reason: String, details: => String): Unit = {
    System.err.println(reason)
    // want to ensure "reason" is written before attempting to write any details
    System.err.flush()
    System.err.println(details)
    // if we have gotten here we may not yet have attempted to close, ensure we do.
    try {
      Await.ready(close(), closeDeadline - Time.now)
    } catch {
      case NonFatal(exc) =>
        exitOnError(newCloseException(Seq(exc)))
    }
    System.exit(1)
  }

  /**
   * By default any failure during the graceful shutdown portion of an [[App]]'s lifecycle
   * bubbles up and causes non-zero return codes in the process. Setting this to `false`
   * allows an application to suppress these errors and express that graceful shutdown logic
   * should not be a determinant of the process exit code.
   */
  protected def suppressGracefulShutdownErrors: Boolean = false

  private val inits: mutable.Buffer[() => Unit] = mutable.Buffer.empty
  private val premains: mutable.Buffer[() => Unit] = mutable.Buffer.empty
  private val exits: ConcurrentLinkedQueue[Closable] = new ConcurrentLinkedQueue
  private val lastExits: ConcurrentLinkedQueue[Closable] = new ConcurrentLinkedQueue
  private val postmains: ConcurrentLinkedQueue[() => Unit] = new ConcurrentLinkedQueue

  // finagle isn't available here, so no DefaultTimer
  protected lazy val shutdownTimer: Timer = new JavaTimer(isDaemon = true)

  /**
   * Programmatically specify which implementations to use in
   * $LoadServiceApplyScaladocLink
   * for the given interfaces. This allows applications to circumvent
   * the standard service loading mechanism when needed. It may be useful
   * if the application has a broad and/or rapidly changing set of dependencies.
   *
   * For example, to require `SuperCoolMetrics` be used as the
   * `com.twitter.finagle.stats.StatsReceiver` implementation:
   * {{{
   * import com.initech.SuperCoolMetrics
   * import com.twitter.app.App
   * import com.twitter.app.LoadService.Binding
   * import com.twitter.finagle.stats.StatsReceiver
   *
   * class MyApp extends App {
   *   val implementationToUse = new SuperCoolMetrics()
   *
   *   override protected[this] val loadServiceBindings: Seq[Binding[_]] = {
   *     Seq(new Binding(classOf[StatsReceiver], implementationToUse))
   *   }
   *
   *   def main(): Unit = {
   *     val loaded = LoadService[StatsReceiver]()
   *     assert(Seq(implementationToUse) == loaded)
   *   }
   * }
   * }}}
   *
   * If this is called for a `Class[T]` after $LoadServiceApplyScaladocLink
   * has been called for that same interface, an `IllegalStateException`
   * will be thrown. For this reason, bindings are done as early
   * as possible in the application lifecycle, before even `inits`
   * and flag parsing.
   *
   * @note this should not generally be used by "libraries" as it forces
   *       their user's implementation choice.
   *
   * @return a mapping from `Class` to the 1 or more implementations to
   *         be used by $LoadServiceApplyScaladocLink for that interface.
   *
   * @see $LoadServiceApplyScaladocLink
   */
  protected[this] def loadServiceBindings: Seq[LoadService.Binding[_]] = Nil

  /**
   * Parse the command line arguments as an Array of Strings. The default
   * implementation parses the given String[] as [[Flag]] input values.
   *
   * Users may override this method to specify different functionality.
   *
   * @param args String Array which represents the command line input given
   *             to the application.
   */
  protected[this] def parseArgs(args: Array[String]): Unit = {
    flag.parseArgs(args, allowUndefinedFlags) match {
      case Flags.Ok(remainder) =>
        _args = remainder.toArray
      case Flags.Help(usage) =>
        throw FlagUsageError(usage)
      case Flags.Error(reason) =>
        throw FlagParseException(reason)
    }
  }

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
  final def closeOnExit(closable: Closable): Unit = synchronized {
    if (isClosed) {
      // `close()` has already been called, we close eagerly
      closable.close(closeDeadline)
    } else {
      // `close()` not yet called, safe to add it
      exits.add(closable)
    }
  }

  /**
   * Register a `closable` to be closed on application shutdown after those registered
   * via `closeOnExit`.
   *
   * @note Application shutdown occurs in two sequential phases to allow explicit
   *       encoding of resource lifecycle relationships. Concretely this is useful
   *       for encoding that a monitoring resource should outlive a monitored
   *       resource.
   *
   *       In all cases, the close deadline is enforced.
   */
  final def closeOnExitLast(closable: Closable): Unit = synchronized {
    if (isClosed) {
      // `close()` already called, we need to close this here, but only
      // after `close()` completes and `closing` is satisfied
      close(closeDeadline)
        .transform { _ => closable.close(closeDeadline) }
        .by(shutdownTimer, closeDeadline)
    } else {
      // `close()` not yet called, safe to add it
      lastExits.add(closable)
    }
  }

  /**
   * Invoke `f` when shutdown is requested. Exit hooks run in parallel and are
   * executed after all postmains complete. The thread resumes when all exit
   * hooks complete or `closeDeadline` expires.
   *
   * @see [[runOnExit(Runnable)]] for a variant that is more suitable for Java.
   */
  protected final def onExit(f: => Unit): Unit = {
    closeOnExit {
      Closable.make { deadline => // close() ensures that this deadline is sane
        FuturePool.unboundedPool(f).by(shutdownTimer, deadline)
      }
    }
  }

  /**
   * Invoke `runnable.run()` when shutdown is requested. Exit hooks run in parallel
   * and are executed after all postmains complete. The thread resumes when all exit
   * hooks complete or `closeDeadline` expires.
   *
   * This is a Java friendly API to allow Java 8 users to invoke `onExit` with lambda
   * expressions.
   *
   * For example (in Java):
   *
   * {{{
   *   runOnExit(() -> {
   *    clientA.close();
   *    clientB.close();
   *   });
   * }}}
   *
   * @see [[onExit(f: => Unit)]] for a variant that is more suitable for Scala.
   */
  protected final def runOnExit(runnable: Runnable): Unit = {
    onExit(runnable.run())
  }

  /**
   * Invoke `f` when shutdown is requested. Exit hooks run in parallel and are
   * executed after all closeOnExit functions complete. The thread resumes when all exit
   * hooks complete or `closeDeadline` expires.
   *
   * @see [[runOnExitLast(Runnable)]] for a variant that is more suitable for Java.
   */
  protected final def onExitLast(f: => Unit): Unit = {
    closeOnExitLast {
      Closable.make { deadline => // close() ensures that this deadline is sane
        FuturePool.unboundedPool(f).by(shutdownTimer, deadline)
      }
    }
  }

  /**
   * Invoke `runnable.run()` when shutdown is requested. Exit hooks run in parallel
   * and are executed after all closeOnExit functions complete. The thread resumes
   * when all exit hooks complete or `closeDeadline` expires.
   *
   * This is a Java friendly API to allow Java 8 users to invoke `onExitLast` with lambda
   * expressions.
   *
   * For example (in Java):
   *
   * {{{
   *   runOnExitLast(() -> {
   *    clientA.close();
   *    clientB.close();
   *   });
   * }}}
   *
   * @see [[onExitLast(f: => Unit)]] for a variant that is more suitable for Scala.
   */
  protected final def runOnExitLast(runnable: Runnable): Unit = {
    onExitLast(runnable.run())
  }

  /**
   * Invoke `f` after the user's main has exited.
   */
  protected final def postmain(f: => Unit): Unit = {
    postmains.add(() => f)
  }

  override protected def closeOnce(deadline: Time): Future[Unit] = synchronized {
    closeDeadline = deadline.max(Time.now + MinGrace)
    Future
      .collectToTry(exits.asScala.toSeq.map(_.close(closeDeadline)))
      .by(shutdownTimer, closeDeadline)
      .transform {
        case Return(results) =>
          observeFuture(CloseExitLast) {
            closeLastExits(results, closeDeadline)
          }
        case Throw(t) =>
          // this would be triggered by a timeout on the collectToTry of exits,
          // still try to close last exits
          observeFuture(CloseExitLast) {
            closeLastExits(Seq(Throw(t)), closeDeadline)
          }
      }
  }

  private[this] def newCloseException(errors: Seq[Throwable]): CloseException = {
    val message = if (errors.size == 1) {
      "An error occurred on exit"
    } else {
      s"${errors.size} errors occurred on exit"
    }
    val exc = new CloseException(message)
    errors.foreach { error => exc.addSuppressed(error) }
    exc
  }

  private[this] final def closeLastExits(
    onExitResults: Seq[Try[Unit]],
    deadline: Time
  ): Future[Unit] = {
    Future
      .collectToTry(lastExits.asScala.toSeq.map(_.close(deadline)))
      .by(shutdownTimer, deadline)
      .transform {
        case Return(results) =>
          val errors = (onExitResults ++ results).collect { case Throw(e) => e }
          if (errors.isEmpty) {
            Future.Done
          } else {
            Future.exception(newCloseException(errors))
          }
        case Throw(t) =>
          Future.exception(newCloseException(Seq(t)))
      }
  }

  final def main(args: Array[String]): Unit = {
    try {
      nonExitingMain(args)
    } catch {
      case FlagUsageError(reason) =>
        exitOnError(reason)
      case FlagParseException(reason, _) =>
        exitOnError(reason, flag.usage)
      case t: Throwable =>
        exitOnError(t)
    }
  }

  final def nonExitingMain(args: Array[String]): Unit = {
    observe(Register) {
      App.register(this)
    }
    observe(LoadBindings) {
      loadServiceBindings.foreach { binding => LoadService.bind(binding) }
    }
    observe(Init) {
      for (f <- inits) f()
    }
    observe(ParseArgs) {
      parseArgs(args)
    }
    observe(PreMain) {
      for (f <- premains) f()
    }
    observe(Main) {
      // Get a main() if it's defined. It's possible to define traits that only use pre/post mains.
      val mainMethod =
        try Some(getClass.getMethod("main"))
        catch {
          case _: NoSuchMethodException => None
        }

      // Invoke main() if it exists.
      mainMethod.foreach { method =>
        try method.invoke(this)
        catch {
          case e: InvocationTargetException => throw e.getCause
        }
      }
    }
    observe(PostMain) {
      for (f <- postmains.asScala) f()
    }

    observe(Close) {
      // We get a reference to the `close()` Future in order to Await upon it, to ensure the thread
      // waits for `close()` to complete.
      // Note that the Future returned here is the same as `promise` exposed via ClosableOnce,
      // which is the same result that would be returned by an `Await.result` on `this`.
      val closeFuture = close(defaultCloseGracePeriod)

      // The deadline to 'close' is advisory; we enforce it here.
      if (!suppressGracefulShutdownErrors) Await.result(closeFuture, closeDeadline - Time.now)
      else {
        try { // even if we suppress shutdown errors, we give the resources time to close
          Await.ready(closeFuture, closeDeadline - Time.now)
        } catch {
          case e: TimeoutException =>
            throw e // we want TimeoutExceptions to propagate
          case NonFatal(_) => ()
        }
      }
    }
  }
}

object App {
  private[this] val ref = new AtomicReference[Option[App]](None)

  /**
   * The currently registered App, if any. While the expectation is that there
   * will be a single running App per process, the most-recently registered
   * App will be returned in the event that more than one exists.
   */
  def registered: Option[App] = ref.get

  private[app] def register(app: App): Unit = ref.set(Some(app))
}
