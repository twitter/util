package com.twitter.util.security

import com.twitter.util.Try
import com.twitter.util.security.PemBytes._
import java.io.{BufferedReader, File, IOException, StringReader}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import scala.collection.mutable.ArrayBuffer

/**
 * Signals that the PEM file failed to parse properly due to a
 * missing boundary header or footer.
 */
class InvalidPemFormatException(name: String, message: String)
    extends IOException(s"PemBytes ($name) failed to load: $message")

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
class PemBytes(message: String, name: String) {
  private[this] def openReader(): BufferedReader =
    new BufferedReader(new StringReader(message))

  private[this] def closeReader(bufReader: BufferedReader): Unit =
    Try(bufReader.close())

  private[this] def readDecodedMessage(
    bufReader: BufferedReader,
    messageType: String
  ): Array[Byte] =
    decodeMessage(readEncodedMessage(bufReader, messageType))

  private[this] def readDecodedMessages(
    bufReader: BufferedReader,
    messageType: String
  ): Seq[Array[Byte]] =
    readEncodedMessages(bufReader, messageType).map(decodeMessage)

  private[this] def readEncodedMessage(bufReader: BufferedReader, messageType: String): String = {
    val contents = readEncodedMessages(bufReader, messageType)
    if (contents.isEmpty)
      throw new InvalidPemFormatException(name, "Empty input")
    else contents.head
  }

  private[this] def readEncodedMessages(
    bufReader: BufferedReader,
    messageType: String
  ): Seq[String] = {
    val header = constructHeader(messageType)
    val footer = constructFooter(messageType)

    val contents: ArrayBuffer[String] = ArrayBuffer()
    var content: StringBuilder = null

    var state: PemState = PemState.Begin
    var line: String = bufReader.readLine()

    if (line == null)
      throw new InvalidPemFormatException(name, "Empty input")

    while (line != null) {
      if (state == PemState.Begin || state == PemState.End) {
        // We are either before the first matching header or after a footer
        // and are only looking for the next matching header. Other lines are ignored.
        if (line == header) {
          content = new StringBuilder()
          state = PemState.Content
        }
      } else { // PemState.Content
        if (line == header) {
          // A new matching header, but the previous one had no footer
          throw new InvalidPemFormatException(name, "Missing " + footer)
        }
        if (line == footer) {
          // A good message, handle it and move to the end state
          // where we ignore lines until the next message
          contents.append(content.toString())
          state = PemState.End
        } else {
          // In the middle of a message, append the line and move forward
          content.append(line)
        }
      }
      line = bufReader.readLine()
    }
    if (state == PemState.Begin) {
      // We didn't see any matching header
      throw new InvalidPemFormatException(name, "Missing " + header)
    } else if (state == PemState.Content) {
      // We didn't see a corresponding footer for the current message
      throw new InvalidPemFormatException(name, "Missing " + footer)
    }

    contents.toSeq
  }

  /**
   * This method attempts to read a single PEM encoded message.
   *
   * @note The mesage must contain a header. Items before the header
   * are ignored.
   * @note The message must contain a footer. Items after the footer
   * are ignored.
   * @note If there are multiple messages within the same file,
   * only the first one will be returned.
   */
  def readMessage(messageType: String): Try[Array[Byte]] =
    Try(openReader())
      .flatMap(reader => Try(readDecodedMessage(reader, messageType)).ensure(closeReader(reader)))

  /**
   * This method attemps to read multiple PEM encoded messages
   * of the same type.
   *
   * @note Messages must contain a header. Items before the header
   * are ignored.
   * @note Messages must contain a footer. Items after the footer
   * are ignored.
   */
  def readMessages(messageType: String): Try[Seq[Array[Byte]]] =
    Try(openReader())
      .flatMap(reader => Try(readDecodedMessages(reader, messageType)).ensure(closeReader(reader)))

}

object PemBytes {

  private trait PemState
  private object PemState {

    /**
     * Begin indicates that no message headers have been seen yet.
     */
    case object Begin extends PemState

    /**
     * Content indicates that a Begin header has been seen, but
     * not yet a corresponding End footer.
     */
    case object Content extends PemState

    /**
     * End indicates that an End footer has been seen for a message,
     * but that another one has not started yet.
     */
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

  /**
   * Reads the given file and constructs a `PemBytes` with the contents.
   */
  def fromFile(file: File): Try[PemBytes] = Try {
    val buffered = new String(Files.readAllBytes(file.toPath), StandardCharsets.UTF_8)
    new PemBytes(buffered, file.getName)
  }
}
