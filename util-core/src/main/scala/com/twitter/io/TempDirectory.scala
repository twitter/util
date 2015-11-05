package com.twitter.io

import java.io.File

object TempDirectory {
  /**
   * Create a new temporary directory which is optionally registered to be deleted upon the exit
   * of the VM.
   *
   * @param deleteAtExit Whether to register a JVM shutdown hook to delete the directory.
   * @return File representing the directory.
   */
  def create(deleteAtExit: Boolean = true): File = {
    val path = java.nio.file.Files.createTempDirectory("TempDirectory")

    if (deleteAtExit)
      Runtime.getRuntime().addShutdownHook(new Thread {
        override def run() {
          Files.delete(path.toFile)
        }
      })

    path.toFile
  }
}
