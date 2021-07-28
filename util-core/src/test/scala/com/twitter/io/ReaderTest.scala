package com.twitter.io

import com.twitter.concurrent.AsyncStream
import com.twitter.conversions.DurationOps._
import com.twitter.conversions.StorageUnitOps._
import com.twitter.util.{Await, Awaitable, Future, Promise}
import java.io.ByteArrayInputStream
import java.nio.charset.{StandardCharsets => JChar}
import java.util.concurrent.atomic.AtomicBoolean
import org.mockito.Mockito._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.collection.compat.immutable.LazyList
import scala.collection.mutable.ArrayBuffer

class ReaderTest
    extends AnyFunSuite
    with ScalaCheckDrivenPropertyChecks
    with Matchers
    with Eventually
    with IntegrationPatience {

  private def arr(i: Int, j: Int) = Array.range(i, j).map(_.toByte)

  private def buf(i: Int, j: Int) = Buf.ByteArray.Owned(arr(i, j))

  private def await[T](t: Awaitable[T]): T = Await.result(t, 5.seconds)

  def undefined: AsyncStream[Reader[Buf]] = throw new Exception

  def undefinedReaders: List[Reader[Buf]] = List.empty

  private def assertReadWhileReading(r: Reader[Buf]): Unit = {
    val f = r.read()
    intercept[IllegalStateException] {
      await(r.read())
    }
    assert(!f.isDefined)
  }

  private def assertFailed(r: Reader[Buf], p: Promise[Option[Buf]]): Unit = {
    val f = r.read()
    assert(!f.isDefined)
    p.setException(new Exception)
    intercept[Exception] {
      await(f)
    }
    intercept[Exception] {
      await(r.read())
    }
    intercept[Exception] {
      await(r.read())
    }
  }

  private def writeLoop[T](from: List[T], to: Writer[T]): Future[Unit] =
    from match {
      case h :: t => to.write(h).flatMap(_ => writeLoop(t, to))
      case _ => to.close()
    }

  test("Reader.concat collection of readers") {
    forAll { ss: List[String] =>
      val readers: List[Reader[String]] = ss.map(s => Reader.value(s))
      val concatedReaders = Reader.concat(readers)
      val values = Reader.readAllItems(concatedReaders)
      assert(await(values) == ss)
    }
  }

  test("Reader.concat from a collection won't force the stream") {
    forAll { ss: List[String] =>
      val readers: List[Reader[String]] = ss.map(s => Reader.value(s))
      val head = Reader.value("hmm")
      Reader.concat(head +: readers)
      assert(await(head.read()) == Some("hmm"))
    }
  }

  test("Reader.concat from a stream of readers") {
    forAll { ss: List[String] =>
      val readers: List[Reader[String]] = ss.map(s => Reader.value(s))
      val concatedReaders = Reader.concat(AsyncStream.fromSeq(readers))
      val values = Reader.readAllItems(concatedReaders)
      assert(await(values) == ss)
    }
  }

  test("Reader.concat from a stream of readers forces the stream") {
    forAll { ss: List[String] =>
      val readers: List[Reader[String]] = ss.map(s => Reader.value(s))
      val head = Reader.value("hmm")
      Reader.concat(AsyncStream.fromSeq(head +: readers))
      assert(await(head.read()) == None)
    }
  }

  test("Reader.concat from a stream of readers - discard") {
    val p = new Promise[Option[Buf]]
    val head = new Reader[Buf] {
      def read(): Future[Option[Buf]] = p
      def discard(): Unit = p.setException(new ReaderDiscardedException)
      def onClose: Future[StreamTermination] = Future.never
    }
    val reader = Reader.concat(head +:: undefined)
    reader.discard()
    assert(p.isDefined)
  }

  test("Reader.concat from a stream of readers - read while reading") {
    val p = new Promise[Option[Buf]]
    val head = new Reader[Buf] {
      def read(): Future[Option[Buf]] = p
      def discard(): Unit = p.setException(new ReaderDiscardedException)
      def onClose: Future[StreamTermination] = Future.never
    }
    val reader = Reader.concat(head +:: undefined)
    assertReadWhileReading(reader)
  }

  test("Reader.concat from a stream of readers - failed") {
    val p = new Promise[Option[Buf]]
    val head = new Reader[Buf] {
      def read(): Future[Option[Buf]] = p
      def discard(): Unit = p.setException(new ReaderDiscardedException)
      def onClose: Future[StreamTermination] = Future.never
    }
    val reader = Reader.concat(head +:: undefined)
    assertFailed(reader, p)
  }

  test("Reader.concat from a stream of readers - lazy tail") {
    val head = new Reader[Buf] {
      def read(): Future[Option[Buf]] = Future.exception(new Exception)

      def discard(): Unit = ()
      def onClose: Future[StreamTermination] = Future.never
    }
    val p = new Promise[Unit]

    def tail: AsyncStream[Reader[Buf]] = {
      p.setDone()
      AsyncStream.empty
    }

    val combined = Reader.concat(head +:: tail)
    val buf = Reader.readAllItems(combined)
    intercept[Exception] {
      await(buf)
    }
    assert(!p.isDefined)
  }

  test("Reader.flatten") {
    forAll { ss: List[String] =>
      val readers: List[Reader[String]] = ss.map(s => Reader.value(s))
      val value = Reader.readAllItems(Reader.flatten(Reader.fromSeq(readers)))
      assert(await(value) == ss)
    }
  }

  test("Reader#flatten") {
    forAll { ss: List[String] =>
      val readers: List[Reader[String]] = ss.map(s => Reader.value(s))
      val buf = Reader.readAllItems((Reader.fromSeq(readers).flatten))
      assert(await(buf) == ss)
    }
  }

  test("Reader.flatten empty") {
    val readers = Reader.flatten(Reader.fromSeq(undefinedReaders))
    assert(await(readers.read()) == None)
  }

  test("Reader.flatten - discard") {
    val p = new Promise[Option[Buf]]

    val head = new Reader[Buf] {
      def read(): Future[Option[Buf]] = p
      def discard(): Unit = p.setException(new ReaderDiscardedException)
      def onClose: Future[StreamTermination] = Future.never
    }

    val sourceReader = Reader.fromSeq(head +: undefinedReaders)
    val reader = Reader.flatten(sourceReader)
    reader.read()
    reader.discard()
    assert(p.isDefined)
    assert(await(sourceReader.onClose) == StreamTermination.Discarded)
  }

  test("Reader.flatten - failed") {
    val p = new Promise[Option[Buf]]
    val head = new Reader[Buf] {
      def read(): Future[Option[Buf]] = p
      def discard(): Unit = p.setException(new ReaderDiscardedException)
      def onClose: Future[StreamTermination] = Future.never
    }
    val pReader = Reader.fromSeq(head +: undefinedReaders)
    val reader = Reader.flatten(pReader)
    assertFailed(reader, p)
    assert(await(pReader.onClose) == StreamTermination.Discarded)
  }

  test("Reader.flatten - onClose is satisfied when fully read") {
    val reader = Reader.flatten(Reader.fromSeq(Seq(Reader.value(10), Reader.value(20))))
    assert(await(reader.read()) == Some(10))
    assert(await(reader.read()) == Some(20))
    assert(await(reader.read()) == None)
    assert(await(reader.onClose) == StreamTermination.FullyRead)
  }

  test("Reader.flatten - stop when encounter exceptions") {
    val reader = Reader.flatten(
      Reader.fromSeq(
        Seq(Reader.value(10), Reader.exception(new Exception("stop")), Reader.value(20))))
    assert(await(reader.read()) == Some(10))
    val ex1 = intercept[Exception] {
      await(reader.read())
    }
    assert(ex1.getMessage == "stop")
    val ex2 = intercept[Exception] {
      await(reader.read())
    }
    assert(ex2.getMessage == "stop")
    val ex3 = intercept[Exception] {
      await(reader.onClose)
    }
    assert(ex3.getMessage == "stop")
  }

  test("Reader.flatten - listen for onClose update from parent") {
    val exceptionMsg = "boom"
    val p = new Pipe[Int]
    val reader = Reader.flatten(Reader.value(p))
    reader.read()
    p.fail(new Exception(exceptionMsg))
    val exception = intercept[Exception] {
      await(reader.onClose)
    }
    assert(exception.getMessage == exceptionMsg)
  }

  test(
    "Reader.flatten - re-register `curReaderClosep` to listen to the next reader in the stream") {
    val exceptionMsg = "boom"

    val r1 = Reader.value(1)
    val p2 = new Promise
    val i2 = p2.interruptible
    val r2 = Reader.fromFuture(i2)

    def rStreams: LazyList[Reader[Any]] = r1 #:: r2 #:: rStreams

    val reader = Reader.fromSeq(rStreams)
    val rFlat = reader.flatten
    val e = new Exception(exceptionMsg)

    assert(await(rFlat.read()) == Some(1))
    // re-register `curReaderClosep` to listen to r2
    rFlat.read()
    i2.raise(e)
    val exception = intercept[Exception] {
      await(rFlat.onClose)
    }

    assert(exception.getMessage == exceptionMsg)
  }

  test("Reader.fromSeq works on infinite streams") {
    def ones: LazyList[Int] = 1 #:: ones
    val reader = Reader.fromSeq(ones)
    assert(await(reader.read()) == Some(1))
    assert(await(reader.read()) == Some(1))
    assert(await(reader.read()) == Some(1))
  }

  test("Reader.fromStream closes resources on EOF read") {
    val in = spy(new ByteArrayInputStream(arr(0, 10)))
    val r: Reader[Buf] = Reader.fromStream(in, 4)
    val f = BufReader.readAll(r)
    assert(await(f) == buf(0, 10))
    eventually {
      verify(in).close()
    }
  }

  test("Reader.fromStream closes resources on discard") {
    val in = spy(new ByteArrayInputStream(arr(0, 10)))
    val r = Reader.fromStream(in, 4)
    r.discard()
    eventually { verify(in).close() }
  }

  test("Reader.fromAsyncStream completes when stream is empty") {
    val as = AsyncStream(buf(1, 10)) ++ AsyncStream.exception[Buf](new Exception()) ++ AsyncStream(
      buf(1, 10))
    val r = Reader.fromAsyncStream(as)
    assert(await(r.read()) == Some(buf(1, 10)))
  }

  test("Reader.fromAsyncStream fails on exceptional stream") {
    val as = AsyncStream.exception(new Exception())
    val r = Reader.fromAsyncStream(as)
    val f = Reader.readAllItems(r)
    intercept[Exception] {
      await(f)
    }
  }

  test("Reader.fromAsyncStream only evaluates tail when buffer is exhausted") {
    val tailEvaluated = new AtomicBoolean(false)

    def tail: AsyncStream[Buf] = {
      tailEvaluated.set(true)
      AsyncStream.empty
    }

    val as = AsyncStream.mk(buf(0, 10), AsyncStream.mk(buf(10, 20), tail))
    val r = Reader.fromAsyncStream(as)

    // partially read the buffer
    await(r.read())
    assert(!tailEvaluated.get())

    // read the rest of the buffer
    await(r.read())
    assert(tailEvaluated.get())
  }

  test("Reader.toAsyncStream") {
    forAll { l: List[Byte] =>
      val buf = Buf.ByteArray.Owned(l.toArray)
      val as = Reader.toAsyncStream(Reader.fromBuf(buf, 1))

      assert(await(as.toSeq()).map(b => Buf.ByteArray.Owned.extract(b).head) == l)
    }
  }

  test("Reader.flatMap") {
    forAll { s: String =>
      val reader1 = Reader.fromBuf(Buf.Utf8(s), 8)
      val reader2 = reader1.flatMap { buf =>
        val pipe = new Pipe[Buf]
        pipe.write(buf).flatMap(_ => pipe.write(Buf.Empty)).flatMap(_ => pipe.close())
        pipe
      }

      assert(Buf.decodeString(await(BufReader.readAll(reader2)), JChar.UTF_8) == s)
    }
  }

  test("Reader.flatMap: discard the intermediate will discard the output reader") {
    val reader1 = Reader.fromBuf(Buf.Utf8("hi"), 8)
    val reader2 = reader1.flatMap { buf =>
      val pipe = new Pipe[Buf]
      pipe.write(buf).map(_ => pipe.discard())
      pipe
    }

    await(reader2.read())
    intercept[ReaderDiscardedException] {
      await(reader2.read())
    }
  }

  test("Reader.flatMap: discard the origin reader will discard the output reader") {
    val reader1 = Reader.fromBuf(Buf.Utf8("hi"), 1)
    val samples = ArrayBuffer.empty[Reader[Buf]]
    val reader2 = reader1.flatMap { buf =>
      val pipe = new Pipe[Buf]
      pipe.write(buf).map(_ => samples += pipe).flatMap(_ => pipe.close())
      pipe
    }

    await(reader2.read())
    reader1.discard()

    intercept[ReaderDiscardedException] {
      await(reader2.read())
    }
    assert(samples.size == 1)
    assert(await(samples(0).onClose) == StreamTermination.FullyRead)
  }

  test(
    "Reader.flatMap: discard the output reader will discard " +
      "the origin and intermediate") {
    val reader1 = Reader.fromBuf(Buf.Utf8("hello"), 1)
    val samples = ArrayBuffer.empty[Reader[Buf]]
    val reader2 = reader1.flatMap { buf =>
      val pipe = new Pipe[Buf]
      pipe.write(buf).map(_ => samples += pipe).flatMap(_ => pipe.close())
      pipe
    }

    await(reader2.read())
    await(reader2.read())
    reader2.discard()

    assert(samples.size == 2)
    assert(await(samples(0).onClose) == StreamTermination.FullyRead)
    assert(await(samples(1).onClose) == StreamTermination.Discarded)

    intercept[ReaderDiscardedException] {
      await(reader1.read())
    }
  }

  test("Reader.flatMap: propagate parent's onClose when parent reader failed with an exception") {
    val ExceptionMessage = "boom"
    val parentReader = new Pipe[Double]
    val childReader = parentReader.flatMap(Reader.value)
    parentReader.fail(new Exception(ExceptionMessage))
    val parentException = intercept[Exception](await(parentReader.onClose))
    val readerException = intercept[Exception](await(childReader.onClose))
    assert(parentException.getMessage == ExceptionMessage)
    assert(readerException.getMessage == ExceptionMessage)
  }

  test("Reader.flatMap: propagate parent's onClose when parent reader is discarded") {
    val parentReader = Reader.empty[Int]
    val childReader = parentReader.flatMap(Reader.value)
    parentReader.discard()
    assert(await(parentReader.onClose) == StreamTermination.Discarded)
    assert(await(childReader.onClose) == StreamTermination.Discarded)
  }

  test("Reader.value") {
    forAll { a: AnyVal =>
      val r = Reader.value(a)
      assert(await(r.read()) == Some(a))
      assert(r.onClose.isDefined == false)
      assert(await(r.read()) == None)
      assert(await(r.onClose) == StreamTermination.FullyRead)
    }
  }

  test("Reader.exception") {
    forAll { ex: Exception =>
      val r = Reader.exception(ex)
      val exr = intercept[Exception] {
        await(r.read())
      }
      assert(exr == ex)
      val exc = intercept[Exception] {
        await(r.onClose)
      }
      assert(exc == ex)
    }
  }

  test("Reader.fromFuture") {
    // read regularly
    val r1 = Reader.fromFuture(Future.value(1))
    assert(await(r1.read()) == Some(1))
    assert(await(r1.read()) == None)

    // discard
    val r2 = Reader.fromFuture(Future.value(2))
    r2.discard()
    intercept[ReaderDiscardedException] {
      await(r2.read())
    }
  }

  test("Reader.map") {
    forAll { l: List[Int] =>
      val pipe = new Pipe[Int]
      writeLoop(l, pipe)
      val reader2 = pipe.map(_.toString)
      assert(await(Reader.readAllItems(reader2)) == l.map(_.toString))
    }
  }

  test(
    "Reader.empty " +
      "- return Future.None and update closep to be FullyRead after first read") {
    val reader = Reader.empty
    assert(await(reader.read()) == None)
    assert(await(reader.onClose) == StreamTermination.FullyRead)
  }

  test(
    "Reader.empty " +
      "- return ReaderDiscardedException when reading from a discarded reader") {
    val reader = Reader.empty
    reader.discard()
    intercept[ReaderDiscardedException] {
      await(reader.read())
    }
    assert(await(reader.onClose) == StreamTermination.Discarded)
  }

  test("Reader.readAllItems") {
    val genBuf: Gen[Buf] = {
      for {
        // limit arrays to a few kilobytes, otherwise we may generate a very large amount of data
        numBytes <- Gen.choose(0.bytes.inBytes, 2.kilobytes.inBytes)
        bytes <- Gen.containerOfN[Array, Byte](numBytes.toInt, Arbitrary.arbitrary[Byte])
      } yield {
        Buf.ByteArray.Owned(bytes)
      }
    }

    val genList =
      Gen.listOf(Gen.oneOf(Gen.alphaLowerStr, Gen.alphaNumStr, Gen.choose(1, 100), genBuf))
    forAll(genList) { l =>
      val r = Reader.fromSeq(l)
      assert(await(Reader.readAllItems(r)) == l)
    }
  }
}
