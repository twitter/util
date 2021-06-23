package com.twitter.app.command

import com.twitter.io.{Buf, Reader}

/**
 * The exception thrown when the command returns with a nonzero return code.
 *
 * @param code The actual return code from the command
 * @param stdErr A [[Reader]] with the stderr from the command. Note that if the stderr has already
 *               been consumed, then this [[Reader]] will only contain the portion of the stderr,
 *               that has not yet been consumed.
 */
case class NonZeroReturnCode(command: Seq[String], code: Int, stdErr: Reader[Buf])
    extends Exception(s"Command $command exited with return code: $code")
