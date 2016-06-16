package com.twitter.util.security.exp

import com.twitter.logging.Logger
import com.twitter.util.Try
import java.io.{File, FileInputStream, InputStream}
import java.security.cert.{CertificateFactory, X509Certificate}

/**
 * Object X509CertificateLoader provides the ability to load
 * an X509Certificate from a file on the filesystem.
 */
object X509CertificateLoader {

  private[this] val log = Logger.get("com.twitter.util.security")

  private[this] def isReadableFile(file: File): Boolean =
    file.isFile() && file.canRead()

  private[this] def readX509Certificate(inputStream: InputStream): X509Certificate = {
    val certFactory = CertificateFactory.getInstance("X.509")
    certFactory
      .generateCertificate(inputStream)
      .asInstanceOf[X509Certificate]
  }

  private[this] def openStream(certFile: File): InputStream =
    new FileInputStream(certFile)

  private[this] def closeStream(inputStream: InputStream): Unit =
    inputStream.close()

  private[this] def tryOpen(certFile: File): Try[InputStream] =
    Try(openStream(certFile))

  private[this] def tryRead(inputStream: InputStream): Try[X509Certificate] =
    Try(readX509Certificate(inputStream)).ensure(Try(closeStream(inputStream)))

  private[this] def logException(certFile: File)(ex: Throwable): Unit =
    log.warning(s"X509Certificate (${certFile.getName()}) failed to load: ${ex.getMessage()}.")

  private[this] def logNotReadableFile(certFile: File): Unit =
    log.warning(s"X509Certificate (${certFile.getName()}) is not a file or is not readable.")

  /**
   * Loads an X509Certificate from the filesystem.
   *
   * @param certFile A File instance pointing to the certificate file.
   * @return An optional value containing the certificate if success, or
   * None otherwise.
   */
  def load(certFile: File): Option[X509Certificate] = {
    if (isReadableFile(certFile)) {
      tryOpen(certFile).flatMap(tryRead).onFailure(logException(certFile)).toOption
    } else {
      logNotReadableFile(certFile)
      None
    }
  }

}
