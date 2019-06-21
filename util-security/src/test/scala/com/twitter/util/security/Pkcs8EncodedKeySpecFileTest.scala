package com.twitter.util.security

import com.twitter.io.TempFile
import com.twitter.util.Try
import java.io.File
import java.security.spec.PKCS8EncodedKeySpec
import org.scalatest.FunSuite

class Pkcs8EncodedKeySpecFileTest extends FunSuite {

  private[this] val assertLogMessage =
    PemFileTestUtils.assertLogMessage("PKCS8EncodedKeySpec") _

  private[this] val readKeySpecFromFile: File => Try[PKCS8EncodedKeySpec] =
    (tempFile) => {
      val keyFile = new Pkcs8EncodedKeySpecFile(tempFile)
      keyFile.readPkcs8EncodedKeySpec()
    }

  test("File path doesn't exist") {
    PemFileTestUtils.testFileDoesntExist("PKCS8EncodedKeySpec", readKeySpecFromFile)
  }

  test("File path isn't a file") {
    PemFileTestUtils.testFilePathIsntFile("PKCS8EncodedKeySpec", readKeySpecFromFile)
  }

  test("File path isn't readable") {
    PemFileTestUtils.testFilePathIsntReadable("PKCS8EncodedKeySpec", readKeySpecFromFile)
  }

  test("File isn't a key spec") {
    PemFileTestUtils.testEmptyFile[InvalidPemFormatException, PKCS8EncodedKeySpec](
      "PKCS8EncodedKeySpec",
      readKeySpecFromFile
    )
  }

  /**
   * Due to the nature of the format, as long as there is a header and footer, the
   * data is indistinguishable, so it succeeds. Contrast to the same test for an X509Certificate.
   */
  test("File is garbage") {
    // Lines were manually deleted from a real pkcs 8 pem file
    val tempFile = TempFile.fromResourcePath("/keys/test-pkcs8-garbage.key")
    // deleteOnExit is handled by TempFile

    val keySpecFile = new Pkcs8EncodedKeySpecFile(tempFile)
    val tryKeySpec = keySpecFile.readPkcs8EncodedKeySpec()

    assert(tryKeySpec.isReturn)
    val keySpec = tryKeySpec.get()

    assert(keySpec.getFormat() == "PKCS#8")
  }

  test("File is a PKCS8 Encoded Key Spec") {
    val tempFile = TempFile.fromResourcePath("/keys/test-pkcs8.key")
    // deleteOnExit is handled by TempFile

    val keySpecFile = new Pkcs8EncodedKeySpecFile(tempFile)
    val tryKeySpec = keySpecFile.readPkcs8EncodedKeySpec()

    assert(tryKeySpec.isReturn)
    val keySpec = tryKeySpec.get()

    assert(keySpec.getFormat() == "PKCS#8")
  }

}
