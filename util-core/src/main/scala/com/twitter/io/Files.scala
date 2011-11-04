package com.twitter.io

import java.io.File

/**
 * Utilities for working with `java.io.File`s
 */
object Files {

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
