package com.twitter.io

import java.io._

object TempFile {

  /**
   * Create a temporary file from the given (resource) path. The
   * tempfile is deleted on JVM exit.
   *
   * Note, due to the usage of `File.deleteOnExit()` callers should
   * be careful using this as it can leak memory.
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4513817 for
   * example.
   *
   * @param path the resource-relative path to make a temp file from
   * @return the temp File object
   */
  def fromResourcePath(path: String): File = fromResourcePath(getClass, path)

  /**
   * Create a temporary file from the given (resource) path. The
   * tempfile is deleted on JVM exit.
   *
   * Note, due to the usage of `File.deleteOnExit()` callers should
   * be careful using this as it can leak memory.
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4513817 for
   * example.
   *
   * @param klass the `Class` to use for getting the resource.
   * @param path the resource-relative path to make a temp file from
   * @return the temp File object
   */
  def fromResourcePath(klass: Class[_], path: String): File =
    fromStream(path, klass.getResourceAsStream(path))

  /**
   * Create a temporary file from the given system resource path
   * (i.e. via ClassLoader.getSystemResource). The tempfile is deleted
   * on JVM exit.
   *
   * Note, due to the usage of `File.deleteOnExit()` callers should
   * be careful using this as it can leak memory.
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4513817 for
   * example.
   *
   * @param path the path to make a temp file from
   * @return the temp File object
   */
  def fromSystemResourcePath(path: String): File =
    fromStream(path, ClassLoader.getSystemResourceAsStream(path))

  private[this] def fromStream(path: String, stream: InputStream): File =
    stream match {
      case null =>
        throw new FileNotFoundException(path)
      case stream =>
        val (basename, ext) = parsePath(path)
        val file = File.createTempFile(basename, "." + ext)
        file.deleteOnExit()
        val fos = new BufferedOutputStream(new FileOutputStream(file), 1 << 20)
        StreamIO.copy(stream, fos)
        fos.flush()
        fos.close()
        stream.close()
        file
    }

  private[this] def parsePath(path: String) =
    path.split(File.separatorChar).last.split('.').reverse match {
      case Array(basename) => (basename, "")
      case Array(ext, base @ _*) => (base.reverse.mkString("."), ext)
    }

}
