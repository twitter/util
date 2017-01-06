package com.twitter.util.security

import com.twitter.io.TempFile
import com.twitter.util.Try
import java.io.File
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class PemFileTest extends FunSuite {

  private[this] val assertLogMessage =
    PemFileTestUtils.assertLogMessage("PemFile") _

  private[this] def readPemFile(messageType: String)(tempFile: File): Try[String] = {
    val pemFile = new PemFile(tempFile)
    pemFile.readMessage(messageType).map(new String(_))
  }

  private[this] val readHello = readPemFile("hello") _

  test("no header file") {
    val tempFile = TempFile.fromResourcePath("/pem/test-no-head.txt")
    // deleteOnExit is handled by TempFile

    val tryHello = readHello(tempFile)
    PemFileTestUtils.assertException[InvalidPemFormatException, String](tryHello)
    PemFileTestUtils.assertExceptionMessageContains("Missing -----BEGIN HELLO-----")(tryHello)
  }

  test("no footer file") {
    val tempFile = TempFile.fromResourcePath("/pem/test-no-foot.txt")
    // deleteOnExit is handled by TempFile

    val tryHello = readHello(tempFile)
    PemFileTestUtils.assertException[InvalidPemFormatException, String](tryHello)
    PemFileTestUtils.assertExceptionMessageContains("Missing -----END HELLO-----")(tryHello)
  }

  test("wrong message type") {
    val tempFile = TempFile.fromResourcePath("/pem/test.txt")
    // deleteOnExit is handled by TempFile

    val tryHello = readPemFile("GOODBYE")(tempFile)
    PemFileTestUtils.assertException[InvalidPemFormatException, String](tryHello)
    PemFileTestUtils.assertExceptionMessageContains("Missing -----BEGIN GOODBYE-----")(tryHello)
  }

  test("good file") {
    val tempFile = TempFile.fromResourcePath("/pem/test.txt")
    // deleteOnExit is handled by TempFile

    val tryHello = readHello(tempFile)
    assert(tryHello.isReturn)
    val hello = tryHello.get()
    assert(hello == "hello")
  }

  test("good file with text before and after") {
    val tempFile = TempFile.fromResourcePath("/pem/test-before-after.txt")
    // deleteOnExit is handled by TempFile

    val tryHello = readHello(tempFile)
    assert(tryHello.isReturn)
    val hello = tryHello.get()
    assert(hello == "hello")
  }

  test("good file with multiple messages") {
    val tempFile = TempFile.fromResourcePath("/pem/test-multiple.txt")
    // deleteOnExit is handled by TempFile

    val tryHello = readHello(tempFile)
    assert(tryHello.isReturn)
    val hello = tryHello.get()
    assert(hello == "hello")
  }

}
