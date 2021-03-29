package com.twitter.util.security

import com.twitter.util.Try
import java.io.{File, IOException}
import org.scalatest.Assertions._
import scala.reflect.ClassTag

object PemBytesTestUtils {

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

  def testFileDoesntExist[A](name: String, read: File => Try[A]): Unit = {
    val tempFile = File.createTempFile("test", "ext")
    tempFile.deleteOnExit()

    assert(tempFile.exists())
    tempFile.delete()
    assert(!tempFile.exists())

    val tryReadPem: Try[A] = read(tempFile)
    assertIOException(tryReadPem)
  }

  def testFilePathIsntFile[A](name: String, read: File => Try[A]): Unit = {
    val tempFile = File.createTempFile("test", "ext")
    tempFile.deleteOnExit()

    val parentFile = tempFile.getParentFile()
    val tryReadPem: Try[A] = read(parentFile)

    assertIOException(tryReadPem)
  }

  def testFilePathIsntReadable[A](name: String, read: File => Try[A]): Unit = {
    val tempFile = File.createTempFile("test", "ext")
    tempFile.deleteOnExit()

    assert(tempFile.canRead())
    tempFile.setReadable(false)
    assert(!tempFile.canRead())

    val tryReadPem: Try[A] = read(tempFile)

    assertIOException(tryReadPem)
  }

  def testEmptyFile[A <: AnyRef, B](
    name: String,
    read: File => Try[B]
  )(
    implicit classTag: ClassTag[A]
  ): Unit = {
    val tempFile = File.createTempFile("test", "ext")
    tempFile.deleteOnExit()

    val tryReadPem: Try[B] = read(tempFile)

    assertException[A, B](tryReadPem)
  }

}
