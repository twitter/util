package com.twitter.util.security

import com.twitter.io.TempFile
import com.twitter.util.Try
import java.io.File
import java.security.cert.{CertificateException, X509Certificate}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class X509CertificateFileTest extends FunSuite {

  private[this] val assertLogMessage =
    PemFileTestUtils.assertLogMessage("X509Certificate") _

  private[this] def assertCertException(tryCert: Try[X509Certificate]): Unit =
    PemFileTestUtils.assertException[CertificateException, X509Certificate](tryCert)

  private[this] val readCertFromFile: File => Try[X509Certificate] =
    (tempFile) => {
      val certFile = new X509CertificateFile(tempFile)
      certFile.readX509Certificate()
    }

  test("File path doesn't exist") {
    PemFileTestUtils.testFileDoesntExist("X509Certificate", readCertFromFile)
  }

  test("File path isn't a file") {
    PemFileTestUtils.testFilePathIsntFile("X509Certificate", readCertFromFile)
  }

  test("File path isn't readable") {
    PemFileTestUtils.testFilePathIsntReadable("X509Certificate", readCertFromFile)
  }

  test("File isn't a certificate") {
    PemFileTestUtils.testEmptyFile[InvalidPemFormatException, X509Certificate](
      "X509Certificate", readCertFromFile)
  }

  test("File is garbage") {
    val handler = PemFileTestUtils.newHandler()
    // Lines were manually deleted from a real certificate file
    val tempFile = TempFile.fromResourcePath("/certs/test-rsa-garbage.crt")
    // deleteOnExit is handled by TempFile

    val certFile = new X509CertificateFile(tempFile)
    val tryCert = certFile.readX509Certificate()

    assertLogMessage(handler.get, tempFile.getName(), "Incomplete BER/DER data.")
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

}
