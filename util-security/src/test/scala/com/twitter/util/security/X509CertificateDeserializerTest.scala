package com.twitter.util.security

import java.security.cert.{CertificateException, X509Certificate}
import org.scalatest.funsuite.AnyFunSuite
import scala.io.Source

class X509CertificateDeserializerTest extends AnyFunSuite {
  private[this] def getResourceFileAsString(path: String): String = {
    val resourceStream = getClass.getResourceAsStream(path)
    val resourceSource = Source.fromInputStream(resourceStream)
    try {
      val resource = resourceSource.mkString
      resource
    } finally {
      resourceSource.close()
    }
  }

  private[this] def assertIsCslCert(cert: X509Certificate): Unit = {
    val subjectData: String = cert.getSubjectX500Principal.getName()
    assert(subjectData.contains("Core Systems Libraries"))
  }

  test("Input is garbage") {
    // Lines were manually deleted from a real certificate file
    val input = getResourceFileAsString("/certs/test-rsa-garbage.crt")

    val tryCert = X509CertificateDeserializer.deserializeCertificate(input, "test")

    PemBytesTestUtils.assertException[CertificateException, X509Certificate](tryCert)
  }

  test("Input is an X509 Certificate") {
    val input = getResourceFileAsString("/certs/test-rsa.crt")

    val tryCert = X509CertificateDeserializer.deserializeCertificate(input, "test")

    assert(tryCert.isReturn)
    val cert = tryCert.get()

    assert(cert.getSigAlgName == "SHA256withRSA")
  }

  test("Input with multiple X509 Certificates") {
    val input = getResourceFileAsString("/certs/test-rsa-chain.crt")

    val tryCerts = X509CertificateDeserializer.deserializeCertificates(input, "test")

    assert(tryCerts.isReturn)
    val certs = tryCerts.get()

    assert(certs.length == 2)

    val intermediate = certs.head
    assertIsCslCert(intermediate)

    val root = certs(1)
    assertIsCslCert(root)
  }

  test("X509 Certificate File is not valid: expired") {
    val input = getResourceFileAsString("/certs/test-rsa-expired.crt")

    intercept[java.security.cert.CertificateExpiredException] {
      X509CertificateDeserializer.deserializeCertificate(input, "test").get()
    }
  }

  test("X509 Certificate File is not valid: not yet ready") {
    val input = getResourceFileAsString("/certs/test-rsa-future.crt")

    intercept[java.security.cert.CertificateNotYetValidException] {
      X509CertificateDeserializer.deserializeCertificate(input, "test").get()
    }
  }
}
