package com.twitter.util.security

import com.twitter.io.TempFile
import com.twitter.util.Try
import java.io.File
import java.security.cert.{CRLException, X509CRL}
import org.scalatest.funsuite.AnyFunSuite

class X509CrlFileTest extends AnyFunSuite {

  private[this] def assertCrlException(tryCrl: Try[X509CRL]): Unit =
    PemBytesTestUtils.assertException[CRLException, X509CRL](tryCrl)

  private[this] val readCrlFromFile: File => Try[X509CRL] =
    (tempFile) => {
      val crlFile = new X509CrlFile(tempFile)
      crlFile.readX509Crl()
    }

  test("File path doesn't exist") {
    PemBytesTestUtils.testFileDoesntExist("X509Crl", readCrlFromFile)
  }

  test("File path isn't a file") {
    PemBytesTestUtils.testFilePathIsntFile("X509Crl", readCrlFromFile)
  }

  test("File isn't a crl") {
    PemBytesTestUtils.testEmptyFile[InvalidPemFormatException, X509CRL](
      "X509Crl",
      readCrlFromFile
    )
  }

  test("File is garbage") {
    // Lines were manually deleted from a real crl file
    val tempFile = TempFile.fromResourcePath("/crl/csl-intermediate-garbage.crl")
    // deleteOnExit is handled by TempFile

    val crlFile = new X509CrlFile(tempFile)
    val tryCrl = crlFile.readX509Crl()

    assertCrlException(tryCrl)
  }

  test("File is a Certificate Revocation List") {
    val tempFile = TempFile.fromResourcePath("/crl/csl-intermediate.crl")
    // deleteOnExit is handled by TempFile

    val crlFile = new X509CrlFile(tempFile)
    val tryCrl = crlFile.readX509Crl()

    assert(tryCrl.isReturn)
    val crl = tryCrl.get()

    val principalInfo = new X500PrincipalInfo(crl.getIssuerX500Principal)
    assert(principalInfo.organizationalUnitName.isDefined)
    assert(principalInfo.organizationalUnitName.get == "Core Systems Libraries")
  }

}
