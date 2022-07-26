package com.twitter.util.security

import com.twitter.util.Try

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * A helper object to deserialize PEM-encoded X.509 Certificates.
 *
 * @example
 * -----BEGIN CERTIFICATE-----
 * base64encodedbytes
 * -----END CERTIFICATE-----
 */
object X509CertificateDeserializer {
  private[this] val MessageType: String = "CERTIFICATE"
  private[this] val deserializeX509: Array[Byte] => X509Certificate = { (certBytes: Array[Byte]) =>
    val certFactory = CertificateFactory.getInstance("X.509")
    val certificate = certFactory
      .generateCertificate(new ByteArrayInputStream(certBytes))
      .asInstanceOf[X509Certificate]
    certificate.checkValidity()
    certificate
  }

  /**
   * Deserializes an [[InputStream]] that contains a PEM-encoded X.509
   * Certificate.
   *
   * Closes the InputStream once it has finished reading.
   */
  def deserializeCertificate(rawPem: String, name: String): Try[X509Certificate] = {
    val pemBytes = new PemBytes(rawPem, name)
    val message: Try[Array[Byte]] = pemBytes
      .readMessage(MessageType)

    message.map(deserializeX509)
  }

  /**
   * Deserializes an [[InputStream]] that contains a PEM-encoded X.509
   * Certificate.
   *
   * Closes the InputStream once it has finished reading.
   */
  def deserializeCertificates(rawPem: String, name: String): Try[Seq[X509Certificate]] = {
    val pemBytes = new PemBytes(rawPem, name)
    val messages: Try[Seq[Array[Byte]]] = pemBytes
      .readMessages(MessageType)

    messages.map(_.map(deserializeX509))
  }
}
