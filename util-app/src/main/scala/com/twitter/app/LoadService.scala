package com.twitter.app

import com.twitter.finagle.util.loadServiceDenied
import com.twitter.util.registry.GlobalRegistry
import java.util.ServiceConfigurationError
import java.util.concurrent.ConcurrentHashMap
import java.util.function.{Function => JFunction}
import java.util.logging.{Level, Logger}
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Source
import scala.reflect.ClassTag
import scala.util.control.NonFatal

/**
 * Load classes in the manner of [[java.util.ServiceLoader]]. It is
 * more resilient to varying Java packaging configurations than ServiceLoader.
 *
 * For further control, see [[App.loadServiceBindings]] and the
 * [[loadServiceDenied]] flag.
 */
object LoadService {

  /**
   * Used to specify which implementation(s) should be used for the
   * interface given by `iface`.
   *
   * @param implementations cannot be empty.
   * @see [[App.loadServiceBindings]]
   */
  class Binding[T](
    val iface: Class[T],
    val implementations: Seq[T]
  ) {
    /**
     * Convenience constructor for binding a single implementation.
     */
    def this(iface: Class[T], implementation: T) =
      this(iface, Seq(implementation))

    /**
     * Convenience constructor for Java users.
     * @param implementations cannot be empty.
     */
    def this(iface: Class[T], implementations: java.util.List[T]) =
      this(iface, implementations.asScala)

    if (implementations.isEmpty) {
      throw new IllegalArgumentException("implementations cannot be empty")
    }
  }

  private[this] val log = Logger.getLogger("com.twitter.app.LoadService")

  private[this] val cache =
    new ConcurrentHashMap[ClassLoader, Seq[ClassPath.LoadServiceInfo]]()

  // Thread Safety discussion:
  // =========================
  // Thread safety on `binds` and `loaded` is handled by synchronization
  // on `binds` but note that they are racy at the application level.
  //
  // Specifically, calls to `bind` and `apply` can happen in an arbitrary
  // order. As such the outcome of `bind` checking to see if `apply`
  // has already been called is non-deterministic.
  //
  // "Instantaneous" races are dealt with by serializing access to
  // `binds` and `loaded` but this doesn't mitigate the higher level races.

  /** Map from interface to the implementations to use. */
  private[this] val binds = mutable.Map[Class[_], Seq[_]]()

  private[this] val loaded = mutable.Map[Class[_], Unit]()

  private[this] val bindDupes = new ConcurrentHashMap[Class[_], Unit]()

  /**
   * Programmatically specify which implementations to use in [[apply]] for
   * the given interface, `iface`. This allows users to circumvent
   * the standard service loading mechanism when needed. It may be useful
   * if you have a broad and/or rapidly changing set of dependencies.
   *
   * The public API entry point for this is [[App.loadServiceBindings]].
   *
   * If [[bind]] is called for a `Class[T]` after [[LoadService.apply]]
   * has been called for that same interface, an `IllegalStateException`
   * will be thrown.
   *
   * If [[bind]] is called multiple times for the same interface, the
   * last one registered is used, but warnings and lint violations will
   * be emitted.
   *
   * @note this should not generally be used by "libraries" as it forces
   *       their user's implementation choice.
   *
   * @throws IllegalStateException if [[LoadService.apply]] has already
   *                               been called for the given `Class`.
   */
  private[app] def bind(binding: Binding[_]): Unit = {
    val prevs = binds.synchronized {
      if (loaded.contains(binding.iface)) {
        throw new IllegalStateException(
          s"LoadService.apply has already been called for ${binding.iface.getName}.")
      }
      binds.put(binding.iface, binding.implementations)
    }
    prevs.foreach { ps =>
      bindDupes.put(binding.iface, ())
      log.warning(s"Replaced existing `LoadService.bind` for `${binding.iface.getName}`, " +
        s"old='$ps', new='${binding.implementations}'")
    }
  }

  /**
   * Return any interfaces that have been registered multiple times via [[bind]].
   */
  def duplicateBindings: Set[Class[_]] = bindDupes.keySet.asScala.toSet

  private[this] def addToRegistry(ifaceName: String, implClassNames: Seq[String]): Unit =
    GlobalRegistry.get.put(Seq("loadservice", ifaceName), implClassNames.mkString(","))

  /**
   * Returns classes for the given `Class` as specified by
   * resource files in `META-INF/services/FullyQualifiedClassName`.
   */
  def apply[T](iface: Class[T]): Seq[T] = {
    val ifaceName = iface.getName
    val allowed = binds.synchronized {
      loaded.put(iface, ())
      binds.get(iface)
    }
    val impls = allowed match {
      case Some(as) => as.asInstanceOf[Seq[T]]
      case None => loadImpls(iface, ifaceName)
    }
    addToRegistry(ifaceName, impls.map(_.getClass.getName))
    impls
  }

  /**
   * Returns classes for the given `ClassTag` as specified by
   * resource files in `META-INF/services/FullyQualifiedClassTagsClassName`.
   *
   * @see [[apply(Class)]] for a Java friendly API.
   */
  def apply[T: ClassTag](): Seq[T] = {
    val iface = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
    apply(iface)
  }

  private[this] def loadImpls[T](iface: Class[T], ifaceName: String): Seq[T] = {
    val loader = iface.getClassLoader

    val denied: Set[String] = loadServiceDenied()

    val cp = new LoadServiceClassPath()
    val whenAbsent = new JFunction[ClassLoader, Seq[ClassPath.LoadServiceInfo]] {
      def apply(loader: ClassLoader): Seq[ClassPath.LoadServiceInfo] = {
        cp.browse(loader)
      }
    }

    val classNames = for {
      info <- cache.computeIfAbsent(loader, whenAbsent) if info.iface == ifaceName
      className <- info.lines
    } yield className

    val classNamesFromResources = for {
      rsc <- loader.getResources("META-INF/services/" + ifaceName).asScala
      line <- cp.readLines(Source.fromURL(rsc, "UTF-8"))
    } yield line

    (classNames ++ classNamesFromResources).distinct
      .filterNot { className =>
        val isDenied = denied.contains(className)
        if (isDenied)
          log.info(s"LoadService: skipped $className due to deny list flag")
        isDenied
      }
      .flatMap { className =>
        val cls = Class.forName(className)
        if (!iface.isAssignableFrom(cls))
          throw new ServiceConfigurationError(s"$className not a subclass of $ifaceName")

        log.log(
          Level.FINE,
          s"LoadService: loaded instance of class $className for requested service $ifaceName"
        )

        try {
          val instance = cls.newInstance().asInstanceOf[T]
          Some(instance)
        } catch {
          case NonFatal(ex) =>
            log.log(
              Level.SEVERE,
              s"LoadService: failed to instantiate '$className' for the requested "
                + s"service '$ifaceName'",
              ex
            )
            None
        }
      }
  }

}
