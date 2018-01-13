package com.twitter.util.security

import com.twitter.logging.Logger
import com.twitter.util.Try
import com.twitter.util.security.X509CrlFile._
import java.io.{ByteArrayInputStream, File}
import java.security.cert.{CertificateFactory, X509CRL}

/**
 * A representation of an X.509 Certificate Revocation List (CRL)
 * PEM-encoded and stored in a file.
 * 
 * @example
 * -----BEGIN X509 CRL-----
 * base64encodedbytes
 * -----END X509 CRL-----
 */
class X509CrlFile(file: File) {

  private[this] def logException(ex: Throwable): Unit =
    log.warning(s"X509Crl (${file.getName}) failed to load: ${ex.getMessage}.")

  def readX509Crl(): Try[X509CRL] = {
    val pemFile = new PemFile(file)
    pemFile
      .readMessage(MessageType)
      .map(generateX509Crl)
      .onFailure(logException)
  }

}

private object X509CrlFile {
  private val MessageType: String = "X509 CRL"

  private val log = Logger.get("com.twitter.util.security")

  private def generateX509Crl(decodedMessage: Array[Byte]): X509CRL = {
    val certFactory = CertificateFactory.getInstance("X.509")
    certFactory
      .generateCRL(new ByteArrayInputStream(decodedMessage))
      .asInstanceOf[X509CRL]
  }
}
