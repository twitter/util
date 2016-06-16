package com.twitter.util.security.exp

import com.twitter.io.TempFile
import com.twitter.logging.{BareFormatter, Logger, StringHandler}
import java.io.File
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class X509CertificateLoaderTest extends FunSuite {

  def newLogger(): Logger = {
    val logger = Logger.get("com.twitter.util.security")
    logger.clearHandlers()
    logger.setLevel(Logger.WARNING)
    logger.setUseParentHandlers(false)
    logger
  }

  test("File path doesn't exist") {
    val tempFile = File.createTempFile("test", "crt")
    tempFile.deleteOnExit()

    assert(tempFile.exists())
    tempFile.delete()
    assert(!tempFile.exists())
    assert(!X509CertificateLoader.load(tempFile).isDefined)
  }

  test("File path isn't a file") {
    val tempFile = File.createTempFile("test", "crt")
    tempFile.deleteOnExit()

    val parentFile = tempFile.getParentFile()

    assert(!X509CertificateLoader.load(parentFile).isDefined)
  }

  test("File path isn't readable") {
    val logger = newLogger()
    val handler = new StringHandler(BareFormatter, None)
    logger.addHandler(handler)

    val tempFile = File.createTempFile("test", "crt")
    tempFile.deleteOnExit()

    assert(tempFile.canRead())
    tempFile.setReadable(false)
    assert(!tempFile.canRead())

    assert(!X509CertificateLoader.load(tempFile).isDefined)
    assert(handler.get.trim() ==
      s"X509Certificate (${tempFile.getName()}) is not a file or is not readable.")
  }

  test("File isn't a certificate") {
    val tempFile = File.createTempFile("test", "crt")
    tempFile.deleteOnExit()

    assert(!X509CertificateLoader.load(tempFile).isDefined)
  }

  test("File is an RSA X509 Certificate") {
    val tempFile = TempFile.fromResourcePath("/certs/test-rsa.crt")
    // deleteOnExit is handled by TempFile

    val cert = X509CertificateLoader.load(tempFile)

    assert(cert.isDefined)
    assert(cert.exists(_.getSigAlgName == "SHA256withRSA"))
  }

  test("File is garbage") {
    val logger = newLogger()
    val handler = new StringHandler(BareFormatter, None)
    logger.addHandler(handler)

    // Lines were manually deleted from a real certificate file
    val tempFile = TempFile.fromResourcePath("/certs/test-rsa-garbage.crt")
    // deleteOnExit is handled by TempFile

    val cert = X509CertificateLoader.load(tempFile)

    assert(!cert.isDefined)
    assert(handler.get.trim() startsWith
      s"X509Certificate (${tempFile.getName()}) failed to load: ")
  }

}
