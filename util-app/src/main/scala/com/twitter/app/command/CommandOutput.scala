package com.twitter.app.command

import com.twitter.io.{Buf, Pipe, Reader}
import com.twitter.util.{Future, FuturePool}
import scala.util.control.NonFatal

object CommandOutput {
  private val Delim = '\n'.toByte
  private val DelimLength = 1
  private val futurePool = FuturePool.unboundedPool

  /**
   * Turns a [[java.lang.Process]] into a [[CommandOutput]]. The stdout and stderr inputstreams are
   * converted into a newline-delimited [[Reader]]. If the [[Process]] exits with a non-zero
   * return code, the final [[Reader#read]] on stdout will return an exceptional [[Future]].
   *
   * @param cmd The command used to create the process (used for the exception message)
   * @param process The [[java.lang.Process]] that we are turning into a CommandOutput
   * @return
   */
  def fromProcess(cmd: Seq[String], process: Process): CommandOutput = {
    val outputStream = splitOnNewLines(Reader.fromStream(process.getInputStream))
    val errorStream = splitOnNewLines(Reader.fromStream(process.getErrorStream))

    val returnCode = futurePool {
      process.waitFor()
    }

    val endReader = Reader
      .fromFuture(returnCode.map {
        case 0 =>
          Reader.empty
        case failure =>
          Reader.exception(NonZeroReturnCode(cmd, failure, errorStream))
      }).flatten

    CommandOutput(Reader.concat(Seq(outputStream, endReader)), errorStream)
  }

  /**
   * Takes a [[com.twitter.io.Reader]] of [[com.twitter.io.Buf]] and transforms to a [[Reader]] of
   * [[Buf]] where each element in the resulting reader is the original data delimited by
   * a newline. Delimiters are not included in the output.
   *
   * @param in The incoming [[Reader]]
   * @return A [[Reader]] of the delimited records
   *
   */
  private[command] def splitOnNewLines(
    in: Reader[Buf]
  ): Reader[Buf] = {
    val pipe = new Pipe[Buf]()

    def copyLoop(accum: Buf): Future[Unit] = {
      val delimIndex: Int = accum.process((byte: Byte) => byte != Delim)
      if (delimIndex != -1) {
        pipe.write(accum.slice(0, delimIndex)).before {
          copyLoop(accum.slice(delimIndex + DelimLength, Int.MaxValue))
        }
      } else {
        in.read().flatMap {
            case Some(buf) => copyLoop(accum.concat(buf))
            case None =>
              (if (!accum.isEmpty) pipe.write(accum) else Future.Unit).before {
                pipe.close()
              }
          }.rescue {
            case NonFatal(e) =>
              pipe.fail(e)
              Future.exception(e)
          }
      }
    }
    copyLoop(Buf.Empty).onFailure {
      case NonFatal(_) =>
        in.discard()
    }
    pipe
  }
}

/**
 * The output of a shell command.
 *
 * @param stdout A [[com.twitter.io.Reader]] representing what is written to stdout
 *               by the shell command. Each [[Buf]] is a line of output from
 *               the command. If the command exits with a non zero return code,
 *               the final [[Reader#read()]] will return a failed [[com.twitter.util.Future]]
 *               with a [[NonZeroReturnCode]] exception. If the shell command
 *               completes successfully the final read will return a [[None]]
 * @param stderr A [[Reader]] with the stderr from the shell command. The final
 *               read on stderr will always return a [[None]], even if the command exited with a
 *               nonzero return code.
 */
case class CommandOutput(stdout: Reader[Buf], stderr: Reader[Buf])
