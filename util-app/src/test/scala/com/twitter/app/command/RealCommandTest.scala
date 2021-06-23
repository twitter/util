package com.twitter.app.command

import com.twitter.conversions.DurationOps._
import com.twitter.io.{Buf, Reader}
import com.twitter.util.{Await, Awaitable, Future}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class RealCommandTest extends AnyFunSuite with Matchers {
  private def await[A](awaitable: Awaitable[A]): A = Await.result(awaitable, 5.seconds)

  private def parseUtf8Buf(buf: Buf): String = {
    val Buf.Utf8(str) = buf
    str
  }

  test("Executes a script and gets output") {
    val output =
      Command.run(Seq("util/util-app/src/test/resources/command-test.sh", "10", "0.1", "0"))
    val firstLine = await(output.stdout.read())
    val firstError = await(output.stderr.read())
    firstLine.map(parseUtf8Buf) shouldBe Some("Stdout # 1")
    firstError.map(parseUtf8Buf) shouldBe Some("Stderr # 1")

    // rest of lines/errors
    val restOut = await(Reader.readAllItems(output.stdout))
    val restErr = await(Reader.readAllItems(output.stderr))
    restOut.map(parseUtf8Buf) shouldBe (2 to 10).map(rep => s"Stdout # $rep")
    restErr.map(parseUtf8Buf) shouldBe (2 to 10).map(rep => s"Stderr # $rep")
  }

  test("Executes a script and gets failure") {
    val output =
      Command.run(Seq("util/util-app/src/test/resources/command-test.sh", "10", "0.1", "1"))

    // read 10 lines
    val tenLines = await(Future.traverseSequentially(1 to 10) { _ =>
      output.stdout.read()
    })

    // Read a line from stderr
    val stdErrLine = await(output.stderr.read())
    stdErrLine.map(parseUtf8Buf) shouldBe Some("Stderr # 1")

    tenLines.flatten.map(parseUtf8Buf) shouldBe (1 to 10).map(rep => s"Stdout # $rep")
    // read last exception
    val ex = the[NonZeroReturnCode] thrownBy { await(output.stdout.read()) }
    ex.code shouldBe 1

    // Note that stdErr doesn't have the first line, because it was already read
    await(Reader.readAllItems(ex.stdErr)).map(parseUtf8Buf) shouldBe (2 to 10).map { rep =>
      s"Stderr # $rep"
    }
  }
}
