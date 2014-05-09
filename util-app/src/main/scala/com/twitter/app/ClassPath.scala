package com.twitter.app

import java.net.{URI, URLClassLoader, URISyntaxException}
import scala.collection.mutable
import java.util.jar.JarFile
import java.io.{IOException, File}
import scala.collection.JavaConverters._

/**
 * Inspect and load the classpath. Inspired by 
 * Guava's ClassPath utility.
 *
 * @note This is not a generic facility -- it supports
 * PremainLoader.
 */
private object ClassPath {

  private val ignoredPackages = Seq(
    "apple/", "ch/epfl/", "com/apple/", "com/oracle/", 
    "com/sun/", "java/", "javax/", "scala/", "sun/", "sunw/")

  // TODO: we can inspect the constant pool for "Premain"
  // if needed to speed up start.

  /**
   * Information about a classpath entry.
   */
  case class Info(path: String, loader: ClassLoader) {
    val name =
      if (path.endsWith(".class"))
        (path take (path.length - 6)).replace('/', '.')
      else
        path.replace('/', '.')

    /**
     * Load the classpath described by this entry.
     */
    def load(initialize: Boolean = false): Class[_] =
      Class.forName(name, initialize, loader)
  }

  /**
   * Browse the given classloader recursively from
   * its package root.
   */
  def browse(loader: ClassLoader): Seq[Info] = {
    val buf = mutable.Buffer[Info]()

    for ((uri, loader) <- getEntries(loader))
      browseUri(uri, loader, buf)

    buf
  }

  private def isClass(name: String) =
    (name endsWith ".class") && ((name endsWith "$.class") || !(name contains "$"))

  private def getEntries(loader: ClassLoader): Seq[(URI, ClassLoader)] = {
    val ents = mutable.Buffer[(URI, ClassLoader)]()
    val parent = loader.getParent()
    if (parent != null)
      ents ++= getEntries(parent)

    loader match {
      case urlLoader: URLClassLoader =>
        for (url <- urlLoader.getURLs()) {
          ents += (url.toURI() -> loader)
        }
      case _ =>
    }
    
    ents
  }

  private def browseUri(uri: URI, loader: ClassLoader, buf: mutable.Buffer[Info]) {
    if (uri.getScheme != "file")
      return
    val f = new File(uri)
    if (!f.exists())
      return

   if (f.isDirectory)
      browseDir(f, loader, "", buf)
    else
      browseJar(f, loader, buf)
  }

  private def browseDir(dir: File, loader: ClassLoader, prefix: String, buf: mutable.Buffer[Info]) {
    if (ignoredPackages exists (_ == prefix)) {
      println("ignored "+prefix)
      return
    }

    for (f <- dir.listFiles)
      if (f.isDirectory())
        browseDir(f, loader, prefix + f.getName + "/", buf)
      else if (isClass(f.getName))
        buf += Info(prefix + f.getName, loader)
  }

  private def browseJar(file: File, loader: ClassLoader, buf: mutable.Buffer[Info]) {
    val jarFile = try new JarFile(file) catch {
      case _: IOException => return  // not a Jar file
    }
    
    try {
      for (uri <- jarClasspath(file, jarFile.getManifest))
        browseUri(uri, loader, buf)

      for {
        e <- jarFile.entries.asScala
        if !e.isDirectory
        n = e.getName
        if !(ignoredPackages exists (n startsWith _))
        if isClass(n)
      } buf += Info(n, loader)
    } finally {
      try jarFile.close() catch {
        case _: IOException =>
      }
    }
  }

  private def jarClasspath(jarFile: File, manifest: java.util.jar.Manifest): Seq[URI] = for {
    m <- Option(manifest).toSeq
    attr <- Option(m.getMainAttributes().getValue("Class-Path")).toSeq
    el <- attr.split(" ")
    uri <- uriFromJarClasspath(jarFile, el)
  } yield uri

  def uriFromJarClasspath(jarFile: File, path: String) = try {
    val uri = new URI(path)
    if (uri.isAbsolute)
      Some(uri)
    else
      Some(new File(jarFile.getParentFile, path.replace('/', File.separatorChar)).toURI)
  } catch {
    case _: URISyntaxException => None
  }
}
