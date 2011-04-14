package com.twitter.io

import scala.annotation.tailrec

object OutputStream {
  /**
   * Copy an InputStream to an OutputStream in chunks of the given
   * buffer size (default = 1KB).
   */
  @tailrec
  final def copy(
    inputStream:  java.io.InputStream,
    outputStream: java.io.OutputStream,
    bufferSize:   Int = 1024
  ) {
    val buf = new Array[Byte](bufferSize)
    inputStream.read(buf, 0, buf.size) match {
      case -1 => ()
      case n =>
        outputStream.write(buf, 0, n)
        copy(inputStream, outputStream)
    }
  }

  /**
   * Buffer (fully) the given input stream by creating & copying it to
   * a ByteArrayOutputStream.
   */
  def bufferInputStream(
    inputStream: java.io.InputStream
  ): java.io.ByteArrayOutputStream = {
    val bos = new java.io.ByteArrayOutputStream
    copy(inputStream, bos)
    bos
  }
}
