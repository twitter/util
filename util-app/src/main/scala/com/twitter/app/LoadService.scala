package com.twitter.app

import com.twitter.finagle.util.loadServiceDenied
import com.twitter.util.NonFatal
import com.twitter.util.registry.GlobalRegistry
import java.util.ServiceConfigurationError
import java.util.logging.{Level, Logger}
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Source
import scala.reflect.ClassTag

/**
 * Load classes in the manner of [[java.util.ServiceLoader]]. It is
 * more resilient to varying Java packaging configurations than ServiceLoader.
 */
object LoadService {

  private val log = Logger.getLogger("com.twitter.app.LoadService")

  private val cache = mutable.Map.empty[ClassLoader, Seq[ClassPath.LoadServiceInfo]]

  def apply[T: ClassTag](): Seq[T] = synchronized {
    val iface = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
    val ifaceName = iface.getName
    val loader = iface.getClassLoader

    val denied: Set[String] = loadServiceDenied()

    val cp = new LoadServiceClassPath()
    val classNames = for {
      info <- cache.getOrElseUpdate(loader, cp.browse(loader))
      if info.iface == ifaceName
      className <- info.lines
    } yield className

    val classNamesFromResources = for {
      rsc <- loader.getResources("META-INF/services/" + ifaceName).asScala
      line <- cp.readLines(Source.fromURL(rsc, "UTF-8"))
    } yield line

    val buffer = mutable.ListBuffer.empty[String]
    val result = (classNames ++ classNamesFromResources)
      .distinct
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
          buffer += className
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

    GlobalRegistry.get.put(Seq("loadservice", ifaceName), buffer.mkString(","))
    result
  }
}
