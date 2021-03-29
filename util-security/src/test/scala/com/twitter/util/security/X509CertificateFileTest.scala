package com.twitter.util.security

import com.twitter.io.TempFile
import com.twitter.util.Try
import java.io.File
import java.security.cert.{CertificateException, X509Certificate}
import org.scalatest.funsuite.AnyFunSuite

class X509CertificateFileTest extends AnyFunSuite {

  private[this] def assertIsCslCert(cert: X509Certificate): Unit = {
    val subjectData: String = cert.getSubjectX500Principal.getName()
    assert(subjectData.contains("Core Systems Libraries"))
  }

  private[this] def assertCertException(tryCert: Try[X509Certificate]): Unit =
    PemBytesTestUtils.assertException[CertificateException, X509Certificate](tryCert)

  private[this] val readCertFromFile: File => Try[X509Certificate] =
    (tempFile) => {
      val certFile = new X509CertificateFile(tempFile)
      certFile.readX509Certificate()
    }

  test("File path doesn't exist") {
    PemBytesTestUtils.testFileDoesntExist("X509Certificate", readCertFromFile)
  }

  test("File path isn't a file") {
    PemBytesTestUtils.testFilePathIsntFile("X509Certificate", readCertFromFile)
  }

  test("File path isn't readable") {
    PemBytesTestUtils.testFilePathIsntReadable("X509Certificate", readCertFromFile)
  }

  test("File isn't a certificate") {
    PemBytesTestUtils.testEmptyFile[InvalidPemFormatException, X509Certificate](
      "X509Certificate",
      readCertFromFile
    )
  }

  test("File is garbage") {
    // Lines were manually deleted from a real certificate file
    val tempFile = TempFile.fromResourcePath("/certs/test-rsa-garbage.crt")
    // deleteOnExit is handled by TempFile

    val certFile = new X509CertificateFile(tempFile)
    val tryCert = certFile.readX509Certificate()

    assertCertException(tryCert)
  }

  test("File is an X509 Certificate") {
    val tempFile = TempFile.fromResourcePath("/certs/test-rsa.crt")
    // deleteOnExit is handled by TempFile

    val certFile = new X509CertificateFile(tempFile)
    val tryCert = certFile.readX509Certificate()

    assert(tryCert.isReturn)
    val cert = tryCert.get()

    assert(cert.getSigAlgName == "SHA256withRSA")
  }

  test("File with multiple X509 Certificates") {
    val tempFile = TempFile.fromResourcePath("/certs/test-rsa-chain.crt")
    // deleteOnExit is handled by TempFile

    val certsFile = new X509CertificateFile(tempFile)
    val tryCerts = certsFile.readX509Certificates()

    assert(tryCerts.isReturn)
    val certs = tryCerts.get()

    assert(certs.length == 2)

    val intermediate = certs.head
    assertIsCslCert(intermediate)

    val root = certs(1)
    assertIsCslCert(root)
  }
}
