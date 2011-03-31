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
  def fromResourcePath(path: String): File = {
    val stream = getClass.getResourceAsStream(path)
    val file = File.createTempFile("thrift", "scala")
    file.deleteOnExit()
    val fos = new BufferedOutputStream(new FileOutputStream(file), 1<<20)

    var byte = stream.read()
    while (byte != -1) {
      fos.write(byte)
      byte = stream.read()
    }
    fos.flush()

    file
  }
}
