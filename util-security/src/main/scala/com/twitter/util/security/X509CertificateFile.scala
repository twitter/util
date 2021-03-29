package com.twitter.util.security

import com.twitter.logging.Logger
import com.twitter.util.Try
import com.twitter.util.security.X509CertificateFile._
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.cert.X509Certificate

/**
 * A representation of an X.509 Certificate PEM-encoded and stored
 * in a file.
 *
 * @example
 * -----BEGIN CERTIFICATE-----
 * base64encodedbytes
 * -----END CERTIFICATE-----
 */
class X509CertificateFile(file: File) {

  private[this] def logException(ex: Throwable): Unit =
    log.warning(s"X509Certificate (${file.getName}) failed to load: ${ex.getMessage}.")

  /**
   * Attempts to read the contents of the X.509 Certificate from the file.
   */
  def readX509Certificate(): Try[X509Certificate] = Try {
    new String(Files.readAllBytes(file.toPath), StandardCharsets.UTF_8)
  }.flatMap { buffered =>
      X509CertificateDeserializer.deserializeCertificate(buffered, file.getName)
    }.onFailure(logException)

  /**
   * Attempts to read the contents of multiple X.509 Certificates from the file.
   */
  def readX509Certificates(): Try[Seq[X509Certificate]] = Try {
    new String(Files.readAllBytes(file.toPath), StandardCharsets.UTF_8)
  }.flatMap { buffered =>
      X509CertificateDeserializer.deserializeCertificates(buffered, file.getName)
    }.onFailure(logException)

}

private object X509CertificateFile {
  private val log = Logger.get("com.twitter.util.security")
}
