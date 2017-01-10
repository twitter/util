package com.twitter.util.security

import com.twitter.logging.Logger
import com.twitter.util.Try
import com.twitter.util.security.Pkcs8EncodedKeySpecFile._
import java.io.File
import java.security.spec.PKCS8EncodedKeySpec

/**
 * A representation of a PKCS#8 private key which is PEM-encoded and stored
 * in a file.
 *
 * @example
 * -----BEGIN PRIVATE KEY-----
 * base64encodedbytes
 * -----END PRIVATE KEY-----
 */
class Pkcs8EncodedKeySpecFile(file: File) {

  private[this] def logException(ex: Throwable): Unit =
    log.warning(s"PKCS8EncodedKeySpec (${file.getName()}) failed to load: ${ex.getMessage()}.")

  /**
   * Attempts to read the contents of the private key from the file.
   */
  def readPkcs8EncodedKeySpec(): Try[PKCS8EncodedKeySpec] = {
    val pemFile = new PemFile(file)
    pemFile
      .readMessage(MessageType)
      .map(new PKCS8EncodedKeySpec(_))
      .onFailure(logException)
  }
}

private object Pkcs8EncodedKeySpecFile {
  private val MessageType: String = "PRIVATE KEY"

  private val log = Logger.get("com.twitter.util.security")
}
