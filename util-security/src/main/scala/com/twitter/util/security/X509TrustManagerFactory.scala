package com.twitter.util.security

import com.twitter.logging.Logger
import com.twitter.util.Try
import com.twitter.util.security.X509TrustManagerFactory._
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.{TrustManager, TrustManagerFactory}

/**
 * A factory which can create a `javax.net.ssl.TrustManager` which contains
 * a collection of X.509 certificates.
 */
class X509TrustManagerFactory(certsFile: File) {

  private[this] def logException(ex: Throwable): Unit =
    log.warning(
      s"X509TrustManagerFactory (${certsFile.getName()}) " +
        s"failed to create trust manager: ${ex.getMessage()}."
    )

  /**
   * Attempts to read the contents of a file containing a collection of X.509 certificates,
   * and combines them into a `javax.net.ssl.TrustManager`.
   * The singular value is returned in an Array for ease of use with
   * `javax.net.ssl.SSLContext`'s init method.
   */
  def getTrustManagers(): Try[Array[TrustManager]] = {
    val tryCerts: Try[Seq[X509Certificate]] =
      new X509CertificateFile(certsFile).readX509Certificates()
    tryCerts.flatMap(buildTrustManager).onFailure(logException)
  }

}

object X509TrustManagerFactory {
  private val log = Logger.get("com.twitter.util.security")

  private[this] def setCertificateEntry(ks: KeyStore)(cert: X509Certificate): Unit = {
    val alias: String = UUID.randomUUID().toString()
    ks.setCertificateEntry(alias, cert)
  }

  private[this] def certsToKeyStore(certs: Seq[X509Certificate]): KeyStore = {
    val ks: KeyStore = KeyStore.getInstance("JKS")
    ks.load(null)
    certs.foreach(setCertificateEntry(ks))
    ks
  }

  private[this] def keyStoreToTmf(ks: KeyStore): TrustManagerFactory = {
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    tmf.init(ks)
    tmf
  }

  private[this] def certsToTrustManagers(certs: Seq[X509Certificate]): Array[TrustManager] = {
    val ks: KeyStore = certsToKeyStore(certs)
    val tmf: TrustManagerFactory = keyStoreToTmf(ks)
    tmf.getTrustManagers()
  }

  /**
   * Attempts to combine the passed in X.509 certificates in order to construct a
   * `javax.net.ssl.TrustManager`.
   * The singular value is returned in an Array for ease of use with
   * `javax.net.ssl.SSLContext`'s init method.
   */
  def buildTrustManager(x509Certificates: Seq[X509Certificate]): Try[Array[TrustManager]] = Try {
    certsToTrustManagers(x509Certificates)
  }
}
