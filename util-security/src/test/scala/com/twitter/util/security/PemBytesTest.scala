package com.twitter.util.security

import com.twitter.io.TempFile
import com.twitter.util.Try
import java.io.File
import java.nio.file.Files
import org.scalatest.funsuite.AnyFunSuite

class PemBytesTest extends AnyFunSuite {

  private[this] def readPemBytesMessageFromBytes(
    messageType: String
  )(
    tempBytes: Array[Byte]
  ): Try[String] =
    for {
      pemBytes <- PemBytes.fromBytes(tempBytes, "pem")
      item <- pemBytes.readMessage(messageType)
    } yield new String(item)

  private[this] def readPemBytesMessagesFromBytes(
    messageType: String
  )(
    tempBytes: Array[Byte]
  ): Try[Seq[String]] =
    for {
      pemBytes <- PemBytes.fromBytes(tempBytes, "pem")
      items <- pemBytes.readMessages(messageType)
    } yield items.map(new String(_))

  private[this] def readPemBytesMessage(messageType: String)(tempFile: File): Try[String] =
    for {
      pemBytes <- PemBytes.fromFile(tempFile)
      item <- pemBytes.readMessage(messageType)
    } yield new String(item)

  private[this] def readPemBytesMessages(messageType: String)(tempFile: File): Try[Seq[String]] =
    for {
      pemBytes <- PemBytes.fromFile(tempFile)
      items <- pemBytes.readMessages(messageType)
    } yield items.map(new String(_))

  private[this] val readHello = readPemBytesMessage("hello") _
  private[this] val readHellos = readPemBytesMessages("hello") _
  private[this] val readHellofromBytes = readPemBytesMessageFromBytes("hello") _
  private[this] val readHellosfromBytes = readPemBytesMessagesFromBytes("hello") _

  test("no header file") {
    val tempFile = TempFile.fromResourcePath("/pem/test-no-head.txt")
    // deleteOnExit is handled by TempFile

    val tryHello = readHello(tempFile)
    PemBytesTestUtils.assertException[InvalidPemFormatException, String](tryHello)
    PemBytesTestUtils.assertExceptionMessageContains("Missing -----BEGIN HELLO-----")(tryHello)
  }

  test("no footer file") {
    val tempFile = TempFile.fromResourcePath("/pem/test-no-foot.txt")
    // deleteOnExit is handled by TempFile

    val tryHello = readHello(tempFile)
    PemBytesTestUtils.assertException[InvalidPemFormatException, String](tryHello)
    PemBytesTestUtils.assertExceptionMessageContains("Missing -----END HELLO-----")(tryHello)
  }

  test("wrong message type") {
    val tempFile = TempFile.fromResourcePath("/pem/test.txt")
    // deleteOnExit is handled by TempFile

    val tryHello = readPemBytesMessage("GOODBYE")(tempFile)
    PemBytesTestUtils.assertException[InvalidPemFormatException, String](tryHello)
    PemBytesTestUtils.assertExceptionMessageContains("Missing -----BEGIN GOODBYE-----")(tryHello)
  }

  test("good file") {
    val tempFile = TempFile.fromResourcePath("/pem/test.txt")
    // deleteOnExit is handled by TempFile

    val tryHello = readHello(tempFile)
    assert(tryHello.isReturn)
    val hello = tryHello.get()
    assert(hello == "hello")
  }

  test("good bytes data") {
    val tempBytes = Files.readAllBytes(TempFile.fromResourcePath("/pem/test.txt").toPath)
    // deleteOnExit is handled by TempFile

    val tryHello = readHellofromBytes(tempBytes)
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

  test("read single message from good file with multiple messages") {
    val tempFile = TempFile.fromResourcePath("/pem/test-multiple.txt")
    // deleteOnExit is handled by TempFile

    val tryHello = readHello(tempFile)
    assert(tryHello.isReturn)
    val hello = tryHello.get()
    assert(hello == "hello")
  }

  test("read multiple message from good file with multiple messages") {
    val tempFile = TempFile.fromResourcePath("/pem/test-multiple.txt")
    // deleteOnExit is handled by TempFile

    val tryHello = readHellos(tempFile)
    assert(tryHello.isReturn)
    val messages = tryHello.get()
    assert(messages == Seq("hello", "goodbye"))
  }

  test("read single message from good encoded data with multiple messages as bytes") {
    val tempBytes = Files.readAllBytes(TempFile.fromResourcePath("/pem/test-multiple.txt").toPath)
    // deleteOnExit is handled by TempFile

    val tryHello = readHellofromBytes(tempBytes)
    assert(tryHello.isReturn)
    val hello = tryHello.get()
    assert(hello == "hello")
  }

  test("read multiple message from good encoded data with multiple messages") {
    val tempBytes = Files.readAllBytes(TempFile.fromResourcePath("/pem/test-multiple.txt").toPath)
    // deleteOnExit is handled by TempFile

    val tryHello = readHellosfromBytes(tempBytes)
    assert(tryHello.isReturn)
    val messages = tryHello.get()
    assert(messages == Seq("hello", "goodbye"))
  }
}
