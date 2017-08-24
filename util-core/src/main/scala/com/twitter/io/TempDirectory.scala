package com.twitter.io

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import scala.util.control.NonFatal

object TempDirectory {

  /**
   * A thread-safe queue of temp directories to be cleaned up on JVM Shutdown
   */
  private[this] val dirs = new ConcurrentLinkedQueue[File]()

  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run(): Unit = {
      while (!dirs.isEmpty) {
        try {
          Files.delete(dirs.poll())
        } catch {
          case NonFatal(t) => t.printStackTrace()
        }
      }
    }
  })

  /**
   * Create a new temporary directory which is optionally registered to be deleted upon the exit
   * of the VM.
   *
   * @param deleteAtExit Whether to register a JVM shutdown hook to delete the directory.
   * @return File representing the directory.
   */
  def create(deleteAtExit: Boolean = true): File = {
    val path = java.nio.file.Files.createTempDirectory("TempDirectory")

    if (deleteAtExit) dirs.add(path.toFile)

    path.toFile
  }
}
