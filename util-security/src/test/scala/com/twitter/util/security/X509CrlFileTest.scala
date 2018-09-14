package com.twitter.util.security

import com.twitter.io.TempFile
import com.twitter.util.Try
import java.io.File
import java.security.cert.{CRLException, X509CRL}
import org.scalatest.FunSuite

class X509CrlFileTest extends FunSuite {

  private[this] val assertLogMessage =
    PemFileTestUtils.assertLogMessage("X509Crl") _

  private[this] def assertCrlException(tryCrl: Try[X509CRL]): Unit =
    PemFileTestUtils.assertException[CRLException, X509CRL](tryCrl)

  private[this] val readCrlFromFile: File => Try[X509CRL] =
    (tempFile) => {
      val crlFile = new X509CrlFile(tempFile)
      crlFile.readX509Crl()
    }

  test("File path doesn't exist") {
    PemFileTestUtils.testFileDoesntExist("X509Crl", readCrlFromFile)
  }

  test("File path isn't a file") {
    PemFileTestUtils.testFilePathIsntFile("X509Crl", readCrlFromFile)
  }

  test("File isn't a crl") {
    PemFileTestUtils.testEmptyFile[InvalidPemFormatException, X509CRL](
      "X509Crl",
      readCrlFromFile
    )
  }

  test("File is garbage") {
    val handler = PemFileTestUtils.newHandler()
    // Lines were manually deleted from a real crl file
    val tempFile = TempFile.fromResourcePath("/crl/csl-intermediate-garbage.crl")
    // deleteOnExit is handled by TempFile

    val crlFile = new X509CrlFile(tempFile)
    val tryCrl = crlFile.readX509Crl()

    assertLogMessage(handler.get, tempFile.getName, "Incomplete BER/DER data.")
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
