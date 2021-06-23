package com.twitter.app.command

import java.io.File

/**
 * A utility to allow for processing the outout from an external process in a streaming manner.
 * When [[Command.run]] is called, a [[CommandOutput]] is returned, which provides a
 * [[com.twitter.io.Reader]] interface to both the stdout and stderr of the external process.
 *
 * Example usage:
 * {{{
 *   // Run a shell command and log the output as it shows up, and then discards the output
 *
 *   val commandOutput: CommandOutput = Command.run(Seq("./slow-shell-command.sh")
 *   val logged: Reader[Unit] = commandOutput.stdout.map {
 *    case Buf.Utf8(str) => info(s"stdout: $str")
 *   }
 *   Await.result(Reader.readAllLines(logged).onFailure {
 *    case NonZeroReturnCode(_, code, stderr) =>
 *      val errors = stderr.map {
 *        case Buf.Utf8(str) => str
 *      }
 *      error(s"failure: $code")
 *      errors.foreach(errLine => error(s"stderr: $errLine")
 *   })
 * }}}
 */
object Command extends Command(new CommandExecutorImpl)

class Command private[command] (
  commandExecutor: CommandExecutor) {

  /**
   * Runs a shell command and returns a [[CommandOutput]]. See the CommandOutput scaladoc
   * for more details on the behavior of fields.
   *
   * @param cmd The shell command to run
   * @param workingDirectory An optional directory from which the command should be run
   * @return A [[CommandOutput]] with stdout & stderr from the command
   */
  def run(cmd: Seq[String], workingDirectory: Option[File] = None): CommandOutput = {
    val process = commandExecutor(cmd, workingDirectory)
    CommandOutput.fromProcess(cmd, process)
  }

}
