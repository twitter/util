package com.twitter.io

import com.twitter.concurrent.AsyncStream
import com.twitter.conversions.storage._
import com.twitter.conversions.time._
import com.twitter.util.{Future, Return, Await, Throw}
import java.io.ByteArrayInputStream
import org.scalatest.FunSuite
import scala.util.Random

abstract class ReaderSemanticsTest[A] extends FunSuite {
  def createReader(): Reader[A]

  def loopAndCheck(reader: Reader[A], onClose: Future[StreamTermination]): Future[Unit] = {
    reader.read().flatMap {
      case t @ Some(_) =>
        assert(!onClose.isDefined)
        assert(!reader.onClose.isDefined)
        loopAndCheck(reader, onClose)
      case t @ None =>
        assert(onClose.poll == Some(StreamTermination.FullyRead.Return))
        assert(onClose.poll == Some(StreamTermination.FullyRead.Return))
        Future.Done
    }
  }

  def await(f: Future[_]): Unit = {
    Await.result(f, 5.seconds)
    ()
  }

  test("When you read a reader until the end, onClose will be satisfied") {
    val reader = createReader()
    val f = loopAndCheck(reader, reader.onClose)
    await(f)
    assert(f.poll == Some(Return.Unit))
  }

  test("When you discard a reader, onClose will be satisfied") {
    val reader = createReader()
    assert(!reader.onClose.isDefined)
    reader.discard()
    assert(reader.onClose.poll == Some(StreamTermination.Discarded.Return))
  }
}

class BufReaderSemanticsTest extends ReaderSemanticsTest[Buf] {
  def createReader(): Reader[Buf] = {
    val buf = Buf.Utf8(Random.nextString(2.kilobytes.inBytes.toInt))
    BufReader(buf, 1.kilobyte.inBytes.toInt)
  }
}

class ConcatReaderSemanticsTest extends ReaderSemanticsTest[Buf] {
  def createReader(): Reader[Buf] = {
    val buf = Buf.Utf8(Random.nextString(2.kilobytes.inBytes.toInt))
    Reader.concat(
      AsyncStream(
        BufReader(buf, 1.kilobyte.inBytes.toInt),
        BufReader(buf, 1.kilobyte.inBytes.toInt)
      )
    )
  }

  test("When a piece is failed, the entire thing is failed") {
    val left = new Pipe[Buf]()
    val exn = new Exception("boom!")
    left.fail(exn)
    val buf = Buf.Utf8(Random.nextString(2.kilobytes.inBytes.toInt))
    val right = BufReader(buf, 1.kilobyte.inBytes.toInt)
    val reader = Reader.concat(AsyncStream(left, right))
    val actual = intercept[Exception] {
      await(reader.read())
    }
    assert(actual == exn)
    assert(reader.onClose.poll == Some(Throw(exn)))
  }
}

class InputStreamReaderSemanticsTest extends ReaderSemanticsTest[Buf] {
  def createReader(): Reader[Buf] = {
    val bytes = Random.nextString(2.kilobytes.inBytes.toInt).getBytes
    val inputStream = new ByteArrayInputStream(bytes)
    InputStreamReader(inputStream, 1.kilobyte.inBytes.toInt)
  }
}
