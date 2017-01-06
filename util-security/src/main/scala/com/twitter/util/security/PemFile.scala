package com.twitter.util.security

import com.twitter.util.Try
import com.twitter.util.security.PemFile._
import java.io.{BufferedReader, File, FileReader, IOException}
import java.util.Base64

/**
 * Signals that the PEM file failed to parse properly due to a
 * missing boundary header or footer.
 */
class InvalidPemFormatException(file: File, message: String)
  extends IOException(s"PemFile (${file.getName}) failed to load: $message")

/**
 * An abstract representation of a PEM formatted file.
 *
 * The PEM file format, originally from Privacy Enhanced Mail, is distinctive
 * in that it contains a boundary header and footer and the data within the
 * boundary is Base 64 encoded.
 *
 * @example
 * -----BEGIN CERTIFICATE-----
 * base64encodedbytes
 * -----END CERTIFICATE-----
 */
class PemFile(file: File) {

  private[this] def openReader(file: File): BufferedReader =
    new BufferedReader(new FileReader(file))

  private[this] def closeReader(bufReader: BufferedReader): Unit =
    Try(bufReader.close())

  private[this] def readDecodedMessage(
    bufReader: BufferedReader,
    messageType: String
  ): Array[Byte] =
    decodeMessage(readEncodedMessage(bufReader, messageType))

  private[this] def readEncodedMessage(bufReader: BufferedReader, messageType: String): String = {
    val header = constructHeader(messageType)
    val footer = constructFooter(messageType)

    val content: StringBuilder = new StringBuilder()

    var state: PemState = PemState.Begin
    var line: String = bufReader.readLine()

    if (line == null)
      throw new InvalidPemFormatException(file, "Empty input")

    while (line != null) {
      if (state == PemState.Begin) {
        if (line == header) state = PemState.Content
      } else if (state == PemState.Content) {
        if (line == header) throw new InvalidPemFormatException(file, "Missing " + footer)
        if (line == footer) state = PemState.End
        else content.append(line)
      }
      line = bufReader.readLine()
    }
    if (state == PemState.Begin)
      throw new InvalidPemFormatException(file, "Missing " + header)
    else if (state == PemState.Content)
      throw new InvalidPemFormatException(file, "Missing " + footer)

    content.toString()
  }

  /**
   * This method attempts to read a single PEM encoded message.
   *
   * @note The mesage must contain a header. Items before the header
   * are ignored.
   * @note The message must contain a footer. Items after the footer
   * are ignored.
   * @note If there are multiple messages within the same file,
   * only the first one will be read.
   */
  def readMessage(messageType: String): Try[Array[Byte]] =
    Try(openReader(file)).flatMap(reader =>
      Try(readDecodedMessage(reader, messageType)).ensure(closeReader(reader)))
}

object PemFile {

  private trait PemState
  private object PemState {
    case object Begin extends PemState
    case object Content extends PemState
    case object End extends PemState
  }

  private def constructHeader(messageType: String): String =
    s"-----BEGIN ${messageType.toUpperCase}-----"

  private def constructFooter(messageType: String): String =
    s"-----END ${messageType.toUpperCase}-----"

  private def decodeMessage(encoded: String): Array[Byte] = {
    val decoder = Base64.getDecoder()
    decoder.decode(encoded)
  }

}
