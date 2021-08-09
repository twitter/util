package com.twitter.app.command

import com.twitter.app.command.RealCommandTest.ScriptPath
import com.twitter.conversions.DurationOps._
import com.twitter.io.{Buf, Reader, TempFile}
import com.twitter.util.{Await, Awaitable, Future}
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.Files
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.jdk.CollectionConverters._

object RealCommandTest {
  // Bazel provides the internal dependency as a JAR, and consequently,
  // we need to copy the script into a temp directory to use it.
  // http://go/bazel-compatibility/no-such-file
  private val ScriptPath: String = {
    val temp = TempFile.fromResourcePath(this.getClass, "/command-test.sh")
    Files.setPosixFilePermissions(
      temp.toPath,
      Set(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_EXECUTE,
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.GROUP_EXECUTE,
        PosixFilePermission.OTHERS_READ,
        PosixFilePermission.OTHERS_EXECUTE
      ).asJava
    )
    temp.toString
  }
}

class RealCommandTest extends AnyFunSuite with Matchers {
  private def await[A](awaitable: Awaitable[A]): A = Await.result(awaitable, 5.seconds)

  private def parseUtf8Buf(buf: Buf): String = {
    val Buf.Utf8(str) = buf
    str
  }

  test("Executes a script and gets output") {
    val output = Command.run(
      Seq(ScriptPath, "10", "0.1", "0"),
      None,
      Map(
        "EXTRA_ENV" -> "test value"
      ))

    val firstLine = await(output.stdout.read())
    val firstError = await(output.stderr.read())
    firstLine.map(parseUtf8Buf) shouldBe Some("Stdout # 1 test value")
    firstError.map(parseUtf8Buf) shouldBe Some("Stderr # 1 test value")

    // rest of lines/errors
    val restOut = await(Reader.readAllItems(output.stdout))
    val restErr = await(Reader.readAllItems(output.stderr))
    restOut.map(parseUtf8Buf) shouldBe (2 to 10).map(rep => s"Stdout # $rep test value")
    restErr.map(parseUtf8Buf) shouldBe (2 to 10).map(rep => s"Stderr # $rep test value")
  }

  test("Executes a script and gets failure") {
    val output =
      Command.run(
        Seq(ScriptPath, "10", "0.1", "1"),
        None,
        Map(
          "EXTRA_ENV" -> "test value"
        ))
    // read 10 lines
    val tenLines = await(Future.traverseSequentially(1 to 10) { _ =>
      output.stdout.read()
    })

    // Read a line from stderr
    val stdErrLine = await(output.stderr.read())
    stdErrLine.map(parseUtf8Buf) shouldBe Some("Stderr # 1 test value")

    tenLines.flatten.map(parseUtf8Buf) shouldBe (1 to 10).map(rep => s"Stdout # $rep test value")
    // read last exception
    val ex = the[NonZeroReturnCode] thrownBy { await(output.stdout.read()) }
    ex.code shouldBe 1

    // Note that stdErr doesn't have the first line, because it was already read
    await(Reader.readAllItems(ex.stdErr)).map(parseUtf8Buf) shouldBe (2 to 10).map { rep =>
      s"Stderr # $rep test value"
    }
  }
}
