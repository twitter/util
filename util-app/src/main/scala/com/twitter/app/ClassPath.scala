package com.twitter.app

import com.twitter.finagle.util.loadServiceIgnoredPaths
import java.io.{File, IOException}
import java.net.{URI, URISyntaxException, URL, URLClassLoader}
import java.nio.charset.MalformedInputException
import java.nio.file.Paths
import java.util.jar.{JarEntry, JarFile}
import scala.collection.mutable
import scala.collection.mutable.Builder
import scala.io.Source
import scala.jdk.CollectionConverters._

private[app] object ClassPath {

  val IgnoredPackages: Set[String] = Set(
    "apple/",
    "ch/epfl/",
    "com/apple/",
    "com/oracle/",
    "com/sun/",
    "java/",
    "javax/",
    "scala/",
    "sun/",
    "sunw/"
  )

  sealed abstract class Info(path: String)

  case class FlagInfo(path: String) extends Info(path) {
    val className: String = {
      val prefixed =
        if (path.endsWith(".class")) path.dropRight(6)
        else path
      prefixed.replace('/', '.')
    }
  }

  case class LoadServiceInfo(path: String, iface: String, lines: Seq[String]) extends Info(path)

}

/**
 * Inspect and load the classpath. Inspired by Guava's ClassPath
 * utility.
 *
 * @note This is not a generic facility, rather it is designed
 * specifically to support GlobalFlag and LoadService.
 */
private[app] sealed abstract class ClassPath[CpInfo <: ClassPath.Info] {

  protected def ignoredPackages: Set[String]

  def browse(loader: ClassLoader): Seq[CpInfo] = {
    val buf = Vector.newBuilder[CpInfo]
    val seenUris = mutable.HashSet[URI]()

    for ((uri, loader) <- getEntries(loader)) {
      browseUri0(uri, loader, buf, seenUris)
      seenUris += uri
    }
    buf.result()
  }

  // Note: In JDK 9+ URLClassLoader is no longer the default ClassLoader.
  // This method allows us to scan URL's on the class path that can be used
  // but does NOT include the ModulePath introduced in JDK9. This method is
  // used as a bridge between JDK 8 and JDK 9+.
  // The method used here is attributed to https://stackoverflow.com/a/49557901.
  // TODO - add suppport for the ModulePath after dropping JDK 8 support.
  private[this] def urlsFromClasspath(): Array[URL] = {
    val classpath: String = System.getProperty("java.class.path")
    classpath.split(File.pathSeparator).map { (pathEntry: String) =>
      Paths.get(pathEntry).toAbsolutePath().toUri().toURL
    }
  }

  // package protected for testing
  private[app] def getEntries(loader: ClassLoader): Seq[(URI, ClassLoader)] = {
    val parent = Option(loader.getParent)

    val ownURIs: Vector[(URI, ClassLoader)] = for {
      urlLoader <- Vector(loader).map {
        case urlClassLoader: URLClassLoader => urlClassLoader
        case cl => new URLClassLoader(urlsFromClasspath(), cl)
      }
      urls <- Option(urlLoader.getURLs()).toVector
      url <- urls if url != null
    } yield (url.toURI -> loader)

    val p = parent.toSeq.flatMap(getEntries)

    ownURIs ++ p

  }

  // package protected for testing
  private[app] def browseUri(
    uri: URI,
    loader: ClassLoader,
    buf: Builder[CpInfo, Seq[CpInfo]]
  ): Unit =
    browseUri0(uri, loader, buf, mutable.Set[URI]())

  private[this] def browseUri0(
    uri: URI,
    loader: ClassLoader,
    buf: Builder[CpInfo, Seq[CpInfo]],
    history: mutable.Set[URI]
  ): Unit = {
    if (!history.contains(uri)) {
      history.add(uri)
      if (uri.getScheme != "file")
        return

      val f = new File(uri)
      if (!(f.exists() && f.canRead))
        return

      if (f.isDirectory)
        browseDir(f, loader, "", buf)
      else
        browseJar(f, loader, buf, history)
    }
  }

  private[this] def browseDir(
    dir: File,
    loader: ClassLoader,
    prefix: String,
    buf: Builder[CpInfo, Seq[CpInfo]]
  ): Unit = {
    if (ignoredPackages.contains(prefix))
      return

    for (f <- dir.listFiles)
      if (f.isDirectory && f.canRead) {
        browseDir(f, loader, prefix + f.getName + "/", buf)
      } else
        processFile(prefix, f, buf)
  }

  protected def processFile(prefix: String, file: File, buf: Builder[CpInfo, Seq[CpInfo]]): Unit

  private def browseJar(
    file: File,
    loader: ClassLoader,
    buf: Builder[CpInfo, Seq[CpInfo]],
    seenUris: mutable.Set[URI]
  ): Unit = {
    val jarFile =
      try new JarFile(file)
      catch {
        case _: IOException => return // not a Jar file
      }

    try {
      for (uri <- jarClasspath(file, jarFile.getManifest)) {
        browseUri0(uri, loader, buf, seenUris)
      }

      for {
        e <- jarFile.entries.asScala if !e.isDirectory
        n = e.getName if !ignoredPackages.exists(p => n.startsWith(p))
      } {
        processJarEntry(jarFile, e, buf)
      }
    } finally {
      try jarFile.close()
      catch {
        case _: IOException =>
      }
    }
  }

  protected def processJarEntry(
    jarFile: JarFile,
    entry: JarEntry,
    buf: Builder[CpInfo, Seq[CpInfo]]
  ): Unit

  private def jarClasspath(jarFile: File, manifest: java.util.jar.Manifest): Seq[URI] =
    for {
      m <- Option(manifest).toSeq
      attr <- Option(m.getMainAttributes.getValue("Class-Path")).toSeq
      el <- attr.split(" ").toSeq
      uri <- uriFromJarClasspath(jarFile, el)
    } yield uri

  private def uriFromJarClasspath(jarFile: File, path: String): Option[URI] =
    try {
      val uri = new URI(path)
      if (uri.isAbsolute)
        Some(uri)
      else
        Some(new File(jarFile.getParentFile, path.replace('/', File.separatorChar)).toURI)
    } catch {
      case _: URISyntaxException => None
    }

}

private[app] class FlagClassPath extends ClassPath[ClassPath.FlagInfo] {

  protected def ignoredPackages: Set[String] =
    ClassPath.IgnoredPackages

  private[this] def isClass(name: String): Boolean =
    name.endsWith(".class") && (name.endsWith("$.class") || !name.contains("$"))

  protected def processFile(
    prefix: String,
    file: File,
    buf: Builder[ClassPath.FlagInfo, Seq[ClassPath.FlagInfo]]
  ): Unit = {
    val name = file.getName
    if (isClass(name)) {
      buf += ClassPath.FlagInfo(prefix + name)
    }
  }

  protected def processJarEntry(
    jarFile: JarFile,
    entry: JarEntry,
    buf: Builder[ClassPath.FlagInfo, Seq[ClassPath.FlagInfo]]
  ): Unit = {
    val name = entry.getName
    if (isClass(name)) {
      buf += ClassPath.FlagInfo(name)
    }
  }

}

private[app] class LoadServiceClassPath extends ClassPath[ClassPath.LoadServiceInfo] {

  protected def ignoredPackages: Set[String] =
    ClassPath.IgnoredPackages ++ loadServiceIgnoredPaths()

  private[this] def ifaceOfName(name: String): Option[String] =
    if (!name.contains("META-INF")) None
    else
      name.split("/").takeRight(3) match {
        case Array("META-INF", "services", iface) => Some(iface)
        case _ => None
      }

  private[app] def readLines(source: Source): Seq[String] = {
    try {
      source.getLines().toVector.flatMap { line =>
        val commentIdx = line.indexOf('#')
        val end = if (commentIdx != -1) commentIdx else line.length
        val str = line.substring(0, end).trim
        if (str.isEmpty) Nil else Seq(str)
      }
    } catch {
      case ex: MalformedInputException => Nil /* skip malformed files (e.g. non UTF-8) */
    } finally {
      source.close()
    }
  }

  protected def processFile(
    prefix: String,
    file: File,
    buf: Builder[ClassPath.LoadServiceInfo, Seq[ClassPath.LoadServiceInfo]]
  ): Unit = {
    for (iface <- ifaceOfName(prefix + file.getName)) {
      val source = Source.fromFile(file, "UTF-8")
      val lines = readLines(source)
      buf += ClassPath.LoadServiceInfo(prefix + file.getName, iface, lines)
    }
  }

  protected def processJarEntry(
    jarFile: JarFile,
    entry: JarEntry,
    buf: Builder[ClassPath.LoadServiceInfo, Seq[ClassPath.LoadServiceInfo]]
  ): Unit = {
    for (iface <- ifaceOfName(entry.getName)) {
      val source = Source.fromInputStream(jarFile.getInputStream(entry), "UTF-8")
      val lines = readLines(source)
      buf += ClassPath.LoadServiceInfo(entry.getName, iface, lines)
    }
  }
}
