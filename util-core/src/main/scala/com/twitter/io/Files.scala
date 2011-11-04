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
   * @returns array of bytes
   */
  def readBytes(file: File, limit: Int = 1024 * 1024 * 4): Array[Byte]= {
    require(file.length() < limit, "File '%s' is too big".format(file.getAbsolutePath()))
    val buf = new ByteArrayOutputStream(file.length().intValue())
    val in = new FileInputStream(file)
    StreamIO.copy(in, buf)
    in.close()

    buf.toByteArray()
  }

  /**
   * Deletes a given file or folder.
   *
   * Symlinks themselves will be deleted, but what they point to will
   * not be followed nor deleted.
   *
   * Returns whether or not the entire delete was successful
   */
  // Note: If we were willing to have a dependency on Guava in util-core
  // we could basically replace this with `com.google.common.io.Files.deleteRecursively()`
  def delete(file: File): Boolean = {
    if (!file.exists) {
      true
    } else if (file.isFile) {
      file.delete()
    } else {
      // canonical paths follow symlinks while absolute paths do not,
      // and by checking this, we can avoid deleting what a symlink points to
      if (file.getCanonicalPath == file.getAbsolutePath) {
        file.listFiles.foreach { f =>
          delete(f)
        }
      }
      file.delete()
    }
  }

}
