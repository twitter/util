package com.twitter.util.security

import com.twitter.logging.{BareFormatter, Logger, StringHandler}
import com.twitter.util.Try
import java.io.{File, IOException}
import org.scalatest.Assertions._
import scala.reflect.ClassTag

object PemFileTestUtils {

  def newLogger(): Logger = {
    val logger = Logger.get("com.twitter.util.security")
    logger.clearHandlers()
    logger.setLevel(Logger.WARNING)
    logger.setUseParentHandlers(false)
    logger
  }

  def newHandler(): StringHandler = {
    val logger = newLogger()
    val handler = new StringHandler(BareFormatter, None)
    logger.addHandler(handler)
    handler
  }

  def assertException[A <: AnyRef, B](tryValue: Try[B])(implicit classTag: ClassTag[A]): Unit = {
    assert(tryValue.isThrow)
    intercept[A] {
      tryValue.get()
    }
  }

  def assertExceptionMessageContains[A](expected: String)(tryValue: Try[A]): Unit = {
    assert(tryValue.isThrow)
    val caught = intercept[Exception] {
      tryValue.get()
    }
    val message = caught.getMessage()
    assert(message.contains(expected))
  }

  def assertIOException[A](tryValue: Try[A]): Unit =
    assertException[IOException, A](tryValue)

  def assertLogMessage(name: String)(
    logMessage: String,
    filename: String,
    expectedExMessage: String
  ): Unit = {
    assert(logMessage.startsWith(s"$name ($filename) failed to load: "))
    assert(logMessage.contains(expectedExMessage))
  }

  def testFileDoesntExist[A](
    name: String,
    read: File => Try[A]
  ): Unit = {
    val handler = newHandler()
    val tempFile = File.createTempFile("test", "ext")
    tempFile.deleteOnExit()

    assert(tempFile.exists())
    tempFile.delete()
    assert(!tempFile.exists())

    val tryReadPem: Try[A] = read(tempFile)
    assertLogMessage(name)(handler.get, tempFile.getName(), "No such file or directory")
    assertIOException(tryReadPem)
  }

  def testFilePathIsntFile[A](
    name: String,
    read: File => Try[A]
  ): Unit = {
    val handler = newHandler()
    val tempFile = File.createTempFile("test", "ext")
    tempFile.deleteOnExit()

    val parentFile = tempFile.getParentFile()
    val tryReadPem: Try[A] = read(parentFile)

    assertLogMessage(name)(handler.get, parentFile.getName(), "Is a directory")
    assertIOException(tryReadPem)
  }

  def testFilePathIsntReadable[A](
    name: String,
    read: File => Try[A]
  ): Unit = {
    val handler = newHandler()
    val tempFile = File.createTempFile("test", "ext")
    tempFile.deleteOnExit()

    assert(tempFile.canRead())
    tempFile.setReadable(false)
    assert(!tempFile.canRead())

    val tryReadPem: Try[A] = read(tempFile)

    assertLogMessage(name)(handler.get, tempFile.getName(), "Permission denied")
    assertIOException(tryReadPem)
  }

  def testEmptyFile[A <: AnyRef, B](
    name: String,
    read: File => Try[B]
  )(implicit classTag: ClassTag[A]): Unit = {
    val handler = newHandler()
    val tempFile = File.createTempFile("test", "ext")
    tempFile.deleteOnExit()

    val tryReadPem: Try[B] = read(tempFile)

    assertLogMessage(name)(handler.get, tempFile.getName(), "Empty input")
    assertException[A, B](tryReadPem)
  }

}
