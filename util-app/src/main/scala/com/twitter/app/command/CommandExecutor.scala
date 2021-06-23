package com.twitter.app.command

import java.io.File

/**
 * CommandExecutor is a private trait used for testing so that the actual forking of the
 * command can be mocked out.
 */
private[command] trait CommandExecutor {
  def apply(cmd: Seq[String], workingDirectory: Option[File]): Process
}

private[command] class CommandExecutorImpl extends CommandExecutor {

  /**
   *
   * @param cmd A [[Seq]] of [[String]] for the actual command
   * @param workingDirectory The directory in which the command will be run
   * @return a [[java.lang.Process]] representing the subprocess kicked off
   */
  def apply(cmd: Seq[String], workingDirectory: Option[File]): Process = {
    val processBuilder = new java.lang.ProcessBuilder(cmd: _*)
    workingDirectory.foreach(dir => processBuilder.directory(dir))
    processBuilder.start()
  }
}
