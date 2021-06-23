package com.twitter.app.command

import com.twitter.conversions.DurationOps._
import com.twitter.io.{Buf, Reader}
import com.twitter.util.{Await, Awaitable, Promise}
import java.io.{File, InputStream, OutputStream, PipedInputStream, PipedOutputStream}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CommandTest extends AnyFunSuite with BeforeAndAfterEach with Matchers {
  private def await[A](awaitable: Awaitable[A]): A = Await.result(awaitable, 5.seconds)

  private class FakeCommandExecutorImpl extends CommandExecutor {
    private var retValue: Promise[Int] = _
    private var command: Seq[String] = _
    private var workingDirectory: Option[File] = _
    private var errorOutputStream: PipedOutputStream = _
    private var errorInputStream: PipedInputStream = _
    private var stdOutputStream: PipedOutputStream = _
    private var stdInputStream: PipedInputStream = _

    override def apply(
      cmd: Seq[String],
      directory: Option[File],
      extraEnv: Map[String, String]
    ): Process = {
      retValue = new Promise[Int]()
      command = cmd
      workingDirectory = directory
      errorOutputStream = new PipedOutputStream()
      errorInputStream = new PipedInputStream(errorOutputStream)
      stdOutputStream = new PipedOutputStream()
      stdInputStream = new PipedInputStream(stdOutputStream)

      new Process {
        override def getOutputStream: OutputStream = ???

        override def getInputStream: InputStream = stdInputStream

        override def getErrorStream: InputStream = errorInputStream

        override def waitFor(): Int = {
          Await.result(retValue, 1.second)
        }

        override def exitValue(): Int = ???

        override def destroy(): Unit = ???
      }
    }

    private def checkInitialized(): Unit = {
      if (retValue == null || errorInputStream == null || stdInputStream == null || command == null) {
        throw new IllegalStateException(
          "Can't use fake command executor until the command has been kicked off with apply()")
      }
    }

    private def writeToStream(buf: Buf, stream: OutputStream): Unit = {
      stream.write(Buf.ByteArray.Shared.extract(buf))
    }

    def writeToStdOut(line: Buf): Unit = {
      checkInitialized()
      writeToStream(line, stdOutputStream)
    }

    def writeToStdErr(line: Buf): Unit = {
      checkInitialized()
      writeToStream(line, errorOutputStream)
    }

    def setReturn(value: Int): Unit = {
      checkInitialized()
      retValue.setValue(value)
      errorOutputStream.flush()
      stdOutputStream.flush()
      errorOutputStream.close()
      stdOutputStream.close()
    }

    def reset(): Unit = {
      command = null
      retValue = null
      errorOutputStream = null
      errorInputStream = null
      stdOutputStream = null
      stdInputStream = null
    }
  }

  private val fakeCommandExecutor = new FakeCommandExecutorImpl
  private val exposedCommandExecutor: CommandExecutor = fakeCommandExecutor
  private val command = new Command(exposedCommandExecutor)

  private def fakeCommandReturnsLines(lines: Buf*): Unit = {
    lines.foreach(fakeCommandExecutor.writeToStdOut)
  }

  private def fakeCommandReturnsValue(retVal: Int): Unit = {
    fakeCommandExecutor.setReturn(retVal)
  }

  private def fakeCommandReturnsStdErr(stdErrLines: Buf*): Unit = {
    stdErrLines.foreach(fakeCommandExecutor.writeToStdErr)
  }

  private def parseUtf8Buf(buf: Buf): String = buf match {
    case Buf.Utf8(str) => str
  }

  override protected def beforeEach(): Unit = {
    fakeCommandExecutor.reset()
  }

  test("When command succeeds return reader of lines with stdout and stderr") {
    val result = command.run(Seq("command"))
    val expectedStdOut = Seq("line 1\n", "line 2\n").map(Buf.Utf8(_))
    val expectedStdErr = Seq("error 1\n", "error 2\n").map(Buf.Utf8(_))
    fakeCommandReturnsLines(expectedStdOut: _*)
    fakeCommandReturnsStdErr(expectedStdErr: _*)
    fakeCommandReturnsValue(0)
    val actualStdOut = await(Reader.readAllItems(result.stdout))
    actualStdOut.map(parseUtf8Buf) shouldBe Seq("line 1", "line 2")

    val actualStdErr = await(Reader.readAllItems(result.stderr))
    actualStdErr.map(parseUtf8Buf) shouldBe Seq("error 1", "error 2")
  }

  test("When command fails at end return reader of lines") {
    val result = command.run(Seq("command"))
    val expectedStdOut = Seq("line 1\n", "line 2\n").map(Buf.Utf8(_))
    val expectedStdErr = Seq("error 1\n", "error 2\n").map(Buf.Utf8(_))
    fakeCommandReturnsLines(expectedStdOut: _*)
    fakeCommandReturnsStdErr(expectedStdErr: _*)
    fakeCommandReturnsValue(-1)
    val stdOutLine1 = await(result.stdout.read())
    val stdOutLine2 = await(result.stdout.read())
    stdOutLine1.map(parseUtf8Buf) shouldBe Some("line 1")
    stdOutLine2.map(parseUtf8Buf) shouldBe Some("line 2")

    val stdErrLine1 = await(result.stderr.read())
    stdErrLine1.map(parseUtf8Buf) shouldBe Some("error 1")

    val ex = the[NonZeroReturnCode] thrownBy {
      await(result.stdout.read())
    }

    // We don't get the first line because it was already read
    await(Reader.readAllItems(ex.stdErr)).map(parseUtf8Buf) shouldBe Seq("error 2")
    ex.code shouldBe -1
  }

  test("splitOnNewlinees prpoerly a simple single line with no newline") {
    assertSplitOnNewlines(Seq("line 1"), Seq.empty, Some("line 1"))
  }

  test("splitOnNewlinees prpoerly splits up a simple single line with a newline") {
    assertSplitOnNewlines(Seq("line 1\n"), Seq("line 1"), None)
  }

  test("splitOnNewlinees prpoerly splits up a single line split into 3 without a newline") {
    assertSplitOnNewlines(Seq("li", "ne", " 1"), Seq.empty, Some("line 1"))
  }

  test("splitOnNewlinees prpoerly splits up a single line split into 3 with a newline") {
    assertSplitOnNewlines(Seq("li", "ne", " 1\n"), Seq("line 1"), None)
  }

  test("splitOnNewlinees splits up a simple single line with a newline in a different segment") {
    assertSplitOnNewlines(Seq("line 1", "\n"), Seq("line 1"), None)
  }

  test("splitOnNewlinees splits up an empty input") {
    assertSplitOnNewlines(Seq.empty, Seq.empty, None)
  }

  test("splitOnNewlinees splits up an empty string input") {
    assertSplitOnNewlines(Seq(""), Seq.empty, None)
  }

  test("splitOnNewlinees splits up just a newline") {
    assertSplitOnNewlines(Seq("\n"), Seq(""), None)
  }

  test("splitOnNewlinees splits up a multiple lines in one segment with no trailing newline") {
    assertSplitOnNewlines(Seq("line 1\nline 2"), Seq("line 1"), Some("line 2"))
  }

  test("splitOnNewlinees splits up a multiple lines in one segment with a trailing newline") {
    assertSplitOnNewlines(Seq("line 1\nline 2\n"), Seq("line 1", "line 2"), None)
  }

  test(
    "splitOnNewlinees splits up a multiple lines in multiple segments with no trailing newline") {
    assertSplitOnNewlines(
      Seq("li", "ne 1\nli", "ne 2\nfinal text"),
      Seq("line 1", "line 2"),
      Some("final text"))
  }

  test("splitOnNewlinees splits up a multiple lines in multiple segments with a trailing newline") {
    assertSplitOnNewlines(
      Seq("li", "ne 1\nli", "ne 2\nfinal text\n"),
      Seq("line 1", "line 2", "final text"),
      None)
  }

  /**
   * A helper function to test many cases around splitOnNewlines.
   *
   * @param input The strings used to generate the input [[Reader]]
   * @param expectedMainOutput What we expect to read from the [[Reader]] before the input [[Reader]] is
   *                            finished
   * @param expectedLastLine What we expect to read from the [[Reader]] once the input [[Reader]] is
   *                         finished
   */
  private def assertSplitOnNewlines(
    input: Seq[String],
    expectedMainOutput: Seq[String],
    expectedLastLine: Option[String]
  ): Unit = {
    val finishingPromise = new Promise[Buf]()
    val inputReader = Reader.concat(
      Seq(Reader.fromSeq(input.map(Buf.Utf8(_))), Reader.fromFuture(finishingPromise)))

    val splitReader = CommandOutput.splitOnNewLines(inputReader)

    // Check main output
    expectedMainOutput.foreach { expectedLine =>
      await(splitReader.read()).map(parseUtf8Buf) shouldBe Some(expectedLine)
    }

    // The last line isn't available yet because the reader is not yet done
    val finalTextFut = splitReader.read()
    assert(!finalTextFut.isDefined)

    finishingPromise.setValue(Buf.Empty)

    // Now we should have a line read, if it's available
    await(finalTextFut).map(parseUtf8Buf) shouldBe expectedLastLine

    // If there was content then read again to ensure we are done
    if (expectedLastLine.isDefined) {
      await(splitReader.read()) shouldBe None
    }

  }

}
