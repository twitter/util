package com.twitter.util.security

import com.twitter.logging.Logger
import com.twitter.util.Try
import com.twitter.util.security.X509CertificateFile._
import java.io.{ByteArrayInputStream, File}
import java.security.cert.{CertificateFactory, X509Certificate}

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
    log.warning(s"X509Certificate (${file.getName()}) failed to load: ${ex.getMessage()}.")

  private[this] def generateX509Certificate(decodedMessage: Array[Byte]): X509Certificate = {
    val certFactory = CertificateFactory.getInstance("X.509")
    certFactory
      .generateCertificate(new ByteArrayInputStream(decodedMessage))
      .asInstanceOf[X509Certificate]
  }

  /**
   * Attempts to read the contents of the X.509 Certificate from the file.
   */
  def readX509Certificate(): Try[X509Certificate] = {
    val pemFile = new PemFile(file)
    pemFile
      .readMessage(MessageType)
      .map(generateX509Certificate)
      .onFailure(logException)
  }

  /**
   * Attempts to read the contents of multiple X.509 Certificates from the file.
   */
  def readX509Certificates(): Try[Seq[X509Certificate]] = {
    val pemFile = new PemFile(file)
    pemFile
      .readMessages(MessageType)
      .map(certBytes => certBytes.map(generateX509Certificate))
      .onFailure(logException)
  }

}

object X509CertificateFile {
  val MessageType: String = "CERTIFICATE"

  private val log = Logger.get("com.twitter.util.security")
}

