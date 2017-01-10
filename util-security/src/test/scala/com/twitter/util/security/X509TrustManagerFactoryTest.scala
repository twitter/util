package com.twitter.util.security

import com.twitter.io.TempFile
import java.io.File
import java.security.cert.X509Certificate
import javax.net.ssl.{TrustManager, X509TrustManager}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class X509TrustManagerFactoryTest extends FunSuite {

  private[this] def loadCertFromResource(resourcePath: String): X509Certificate = {
    val tempFile = TempFile.fromResourcePath(resourcePath)
    val certFile = new X509CertificateFile(tempFile)
    val tryCert = certFile.readX509Certificate()
    tryCert.get()
  }

  private[this] def assertTrustsCertificates(trustManager: TrustManager): Unit = {
    assert(trustManager.isInstanceOf[X509TrustManager])
    val x509tm = trustManager.asInstanceOf[X509TrustManager]

    val serverCert = loadCertFromResource("/certs/test-rsa-chain-server.crt")
    x509tm.checkServerTrusted(Array(serverCert), "RSA")
  }

  test("Certificate file is bogus") {
    val tempChainFile = File.createTempFile("test", "crt")
    tempChainFile.deleteOnExit()

    val factory = new X509TrustManagerFactory(tempChainFile)
    val tryTms = factory.getTrustManagers()

    assert(tryTms.isThrow)
  }

  test("Single certificate in collection file") {
    val tempChainFile = TempFile.fromResourcePath("/certs/test-rsa-inter.crt")
    // deleteOnExit is handled by TempFile

    val factory = new X509TrustManagerFactory(tempChainFile)
    val tryTms = factory.getTrustManagers()

    assert(tryTms.isReturn)
    val tms = tryTms.get()
    assert(tms.length == 1)
    val tm = tms.head

    assertTrustsCertificates(tm)
  }

  test("Multiple certificates in collection file") {
    val tempChainFile = TempFile.fromResourcePath("/certs/test-rsa-chain.crt")
    // deleteOnExit is handled by TempFile

    val factory = new X509TrustManagerFactory(tempChainFile)
    val tryTms = factory.getTrustManagers()

    assert(tryTms.isReturn)
    val tms = tryTms.get()
    assert(tms.length == 1)
    val tm = tms.head

    assertTrustsCertificates(tm)
  }

}
