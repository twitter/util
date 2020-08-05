package com.twitter.util.security

import com.twitter.io.TempFile
import java.security.spec.InvalidKeySpecException
import org.scalatest.funsuite.AnyFunSuite

class PrivateKeyFileTest extends AnyFunSuite {

  test("File is garbage") {
    // Lines were manually deleted from a real pkcs 8 pem file
    val tempFile = TempFile.fromResourcePath("/keys/test-pkcs8-garbage.key")
    // deleteOnExit is handled by TempFile

    val privateKeyFile = new PrivateKeyFile(tempFile)
    val tryPrivateKey = privateKeyFile.readPrivateKey()

    intercept[InvalidKeySpecException] {
      tryPrivateKey.get()
    }
  }

  test("File is RSA Private Key") {
    val tempFile = TempFile.fromResourcePath("/keys/test-pkcs8.key")
    // deleteOnExit is handled by TempFile

    val privateKeyFile = new PrivateKeyFile(tempFile)
    val tryPrivateKey = privateKeyFile.readPrivateKey()

    assert(tryPrivateKey.isReturn)
    val privateKey = tryPrivateKey.get()
    assert(privateKey.getFormat == "PKCS#8")
    assert(privateKey.getAlgorithm == "RSA")
  }

  test("File is DSA Private Key") {
    val tempFile = TempFile.fromResourcePath("/keys/test-pkcs8-dsa.key")
    // deleteOnExit is handled by TempFile

    val privateKeyFile = new PrivateKeyFile(tempFile)
    val tryPrivateKey = privateKeyFile.readPrivateKey()

    assert(tryPrivateKey.isReturn)
    val privateKey = tryPrivateKey.get()
    assert(privateKey.getFormat == "PKCS#8")
    assert(privateKey.getAlgorithm == "DSA")
  }

  test("File is EC Private Key") {
    val tempFile = TempFile.fromResourcePath("/keys/test-pkcs8-ec.key")
    // deleteOnExit is handled by TempFile

    val privateKeyFile = new PrivateKeyFile(tempFile)
    val tryPrivateKey = privateKeyFile.readPrivateKey()

    assert(tryPrivateKey.isReturn)
    val privateKey = tryPrivateKey.get()
    assert(privateKey.getFormat == "PKCS#8")
    assert(privateKey.getAlgorithm == "EC")
  }

}
