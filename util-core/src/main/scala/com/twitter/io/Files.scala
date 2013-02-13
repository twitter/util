package com.twitter.io

import java.io.{ByteArrayOutputStream, File, FileInputStream}

/**
 * Utilities for working with `java.io.File`s
 */
object Files {
  /**
   * Read a file fully into a byte array.
   *
   * @param file file to read
   * @param limit number of bytes to read, default 4MB
   * @return array of bytes
   */
  def readBytes(file: File, limit: Int = 1024 * 1024 * 4): Array[Byte]= {
    require(file.length() < limit, "File '%s' is too big".format(file.getAbsolutePath()))
    val buf = new ByteArrayOutputStream(math.min(limit, file.length().intValue()))
    val in = new FileInputStream(file)
    try {
      StreamIO.copy(in, buf)
    } finally {
      in.close()
    }
    buf.toByteArray()
  }

  /**
   * Deletes a given file or folder.
   *
   * Note since symlink detection in java is not reliable across platforms,
   * symlinks are in fact traversed and in the case of directories, what they
   * target will be deleted as well. Perhaps when we upgrade to jdk7 we can
   * use File.isSymbolicLink() here and make following symlinks optional.
   *
   * Returns whether or not the entire delete was successful
   */
  def delete(file: File): Boolean = {
    if (!file.exists) {
      true
    } else if (file.isFile) {
      file.delete()
    } else {
      file.listFiles.foreach { f =>
        delete(f)
      }
      file.delete()
    }
  }

}
