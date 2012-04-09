package com.twitter.io

import java.io.File

object TempDirectory {
  /**
   * Create a new temporary directory, which will be deleted upon the exit of the VM.
   *
   * @return File representing the directory
   */
  def create(deleteAtExit: Boolean = true): File = {
    var file = File.createTempFile("temp", "dir")
    file.delete()
    file.mkdir()

    if (deleteAtExit)
      Runtime.getRuntime().addShutdownHook(new Thread {
        override def run() {
          Files.delete(file)
        }
      })

    file
  }
}