package com.twitter.io

import java.io.{File, BufferedOutputStream, FileOutputStream}

object TempFile {
  /**
   * Create a temporary file from the given (resource) path. The
   * tempfile is deleted on JVM exit.
   *
   * @param path the resource-relative path to make a temp file from
   * @return the temp File object
   */

  def fromResourcePath(path: String): File = fromResourcePath(getClass, path)
  def fromResourcePath(klass: Class[_], path: String): File = {
    val (basename, ext) = {
      val last = path.split(File.separatorChar).last
      last.split('.').reverse match {
        case Array(basename) =>
          (basename, "")
        case Array(ext, base@_*) =>
          (base.reverse.mkString("."), ext)
      }
    }

    val stream = klass.getResourceAsStream(path)
    val file = File.createTempFile(basename, ext)
    file.deleteOnExit()
    val fos = new BufferedOutputStream(new FileOutputStream(file), 1<<20)
    StreamIO.copy(stream, fos)
    fos.flush()

    file
  }
}
