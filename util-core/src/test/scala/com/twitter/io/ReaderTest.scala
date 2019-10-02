package com.twitter.io

import com.twitter.concurrent.AsyncStream
import com.twitter.conversions.DurationOps._
import com.twitter.conversions.StorageUnitOps._
import com.twitter.util.{Await, Awaitable, Future, Promise, Return, Try}
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.{StandardCharsets => JChar}
import java.util.concurrent.atomic.AtomicBoolean
import org.mockito.Mockito._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers}
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

class ReaderTest
    extends FunSuite
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

  private def readAllString(r: Reader[String]): Future[String] = {
    def loop(left: StringBuilder): Future[String] = {
      r.read.flatMap {
        case Some(right) => loop(left.append(right))
        case _ => Future.value(left.toString())
      }
    }
    loop(StringBuilder.newBuilder)
  }

  private def writeLoop[T](from: List[T], to: Writer[T]): Future[Unit] =
    from match {
      case h :: t => to.write(h).flatMap(_ => writeLoop(t, to))
      case _ => to.close()
    }

  test("Reader.copy - source and destination equality") {
    forAll { (p: Array[Byte], q: Array[Byte], r: Array[Byte]) =>
      val rw = new Pipe[Buf]
      val bos = new ByteArrayOutputStream

      val w = Writer.fromOutputStream(bos, 31)
      val f = Reader.copy(rw, w).ensure(w.close())
      val g =
        rw.write(Buf.ByteArray.Owned(p)).before {
          rw.write(Buf.ByteArray.Owned(q)).before {
            rw.write(Buf.ByteArray.Owned(r)).before {
              rw.close()
            }
          }
        }

      await(Future.join(f, g))

      val b = new ByteArrayOutputStream
      b.write(p)
      b.write(q)
      b.write(r)
      b.flush()

      bos.toByteArray should equal(b.toByteArray)
    }
  }

  test("Reader.copy - discard the source if cancelled") {
    var cancelled = false
    val src = new Reader[Int] {
      def read(): Future[Option[Int]] = Future.value(Some(1))
      def discard(): Unit = cancelled = true
      def onClose: Future[StreamTermination] = Future.never
    }
    val dest = new Pipe[Int]
    Reader.copy(src, dest)
    assert(await(dest.read()) == Some(1))
    dest.discard()
    assert(cancelled)
  }

  test("Reader.copy - discard the source if interrupted") {
    var cancelled = false
    val src = new Reader[Int] {
      def read(): Future[Option[Int]] = Future.value(Some(1))
      def discard(): Unit = cancelled = true
      def onClose: Future[StreamTermination] = Future.never
    }
    val dest = new Pipe[Int]
    val f = Reader.copy(src, dest)
    assert(await(dest.read()) == Some(1))
    f.raise(new Exception("Freeze!"))
    assert(cancelled)
  }

  test("Reader.chunked") {
    val stringAndChunk = for {
      s <- Gen.alphaStr
      i <- Gen.posNum[Int].suchThat(_ <= s.length)
    } yield (s, i)

    forAll(stringAndChunk) {
      case (s, i) =>
        val r = Reader.chunked(Reader.fromBuf(Buf.Utf8(s), 32), i)

        def readLoop(): Unit = await(r.read()) match {
          case Some(b) =>
            assert(b.length <= i)
            readLoop()
          case None => ()
        }

        readLoop()
    }
  }

  test("Reader.concat") {
    forAll { ss: List[String] =>
      val readers = ss.map(s => Reader.fromBuf(Buf.Utf8(s), 16))
      val buf = Reader.readAll(Reader.concat(AsyncStream.fromSeq(readers)))
      assert(await(buf) == Buf.Utf8(ss.mkString))
    }
  }

  test("Reader.concat - discard") {
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

  test("Reader.concat - read while reading") {
    val p = new Promise[Option[Buf]]
    val head = new Reader[Buf] {
      def read(): Future[Option[Buf]] = p
      def discard(): Unit = p.setException(new ReaderDiscardedException)
      def onClose: Future[StreamTermination] = Future.never
    }
    val reader = Reader.concat(head +:: undefined)
    assertReadWhileReading(reader)
  }

  test("Reader.concat - failed") {
    val p = new Promise[Option[Buf]]
    val head = new Reader[Buf] {
      def read(): Future[Option[Buf]] = p
      def discard(): Unit = p.setException(new ReaderDiscardedException)
      def onClose: Future[StreamTermination] = Future.never
    }
    val reader = Reader.concat(head +:: undefined)
    assertFailed(reader, p)
  }

  test("Reader.concat - lazy tail") {
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
    val buf = Reader.readAll(combined)
    intercept[Exception] {
      await(buf)
    }
    assert(!p.isDefined)
  }

  test("Reader.flatten") {
    forAll { ss: List[String] =>
      val readers = ss.map(s => Reader.fromBuf(Buf.Utf8(s), 16))
      val buf = Reader.readAll(Reader.flatten(Reader.fromSeq(readers)))
      assert(await(buf) == Buf.Utf8(ss.mkString))
    }
  }

  test("Reader#flatten") {
    forAll { ss: List[String] =>
      val readers = ss.map(s => Reader.fromBuf(Buf.Utf8(s), 16))
      val buf = Reader.readAll((Reader.fromSeq(readers).flatten))
      assert(await(buf) == Buf.Utf8(ss.mkString))
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

  test("Reader.flatten - re-register `curReaderClosep` to listen to the next reader in the stream") {
    val exceptionMsg = "boom"

    val r1 = Reader.value(1)
    val p2 = new Promise
    val i2 = p2.interruptible
    val r2 = Reader.fromFuture(i2)

    def rStreams: Stream[Reader[Any]] = r1 #:: r2 #:: rStreams

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
    def ones: Stream[Int] = 1 #:: ones
    val reader = Reader.fromSeq(ones)
    assert(await(reader.read()) == Some(1))
    assert(await(reader.read()) == Some(1))
    assert(await(reader.read()) == Some(1))
  }

  test("Reader.fromStream closes resources on EOF read") {
    val in = spy(new ByteArrayInputStream(arr(0, 10)))
    val r = Reader.fromStream(in, 4)
    val f = Reader.readAll(r)
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
    val f = Reader.readAll(r)
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

  test("Reader.framed reads framed data") {
    val getByteArrays: Gen[Seq[Buf]] = Gen.listOf(
      for {
        // limit arrays to a few kilobytes, otherwise we may generate a very large amount of data
        numBytes <- Gen.choose(0.bytes.inBytes, 2.kilobytes.inBytes)
        bytes <- Gen.containerOfN[Array, Byte](numBytes.toInt, Arbitrary.arbitrary[Byte])
      } yield Buf.ByteArray.Owned(bytes)
    )

    forAll(getByteArrays) { buffers: Seq[Buf] =>
      val buffersWithLength = buffers.map(buf => Buf.U32BE(buf.length).concat(buf))

      val r = Reader.framed(BufReader(Buf(buffersWithLength)), new ReaderTest.U32BEFramer())

      // read all of the frames
      buffers.foreach { buf =>
        assert(await(r.read()).contains(buf))
      }

      // make sure the reader signals EOF
      assert(await(r.read()).isEmpty)
    }
  }

  test("Reader.framed reads empty frames") {
    val r = Reader.framed(BufReader(Buf.U32BE(0)), new ReaderTest.U32BEFramer())
    assert(await(r.read()).contains(Buf.Empty))
    assert(await(r.read()).isEmpty)
  }

  test("Framed works on non-list collections") {
    val r = Reader.framed(BufReader(Buf.U32BE(0)), i => Vector(i))
    assert(await(r.read()).isDefined)
  }

  test("Reader.flatMap") {
    forAll { s: String =>
      val reader1 = Reader.fromBuf(Buf.Utf8(s), 8)
      val reader2 = reader1.flatMap { buf =>
        val pipe = new Pipe[Buf]
        pipe.write(buf).flatMap(_ => pipe.write(Buf.Empty)).flatMap(_ => pipe.close())
        pipe
      }

      assert(Buf.decodeString(await(Reader.readAll(reader2)), JChar.UTF_8) == s)
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
      assert(await(readAllString(reader2)) == l.mkString)
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
}

object ReaderTest {

  /**
   * Used to test Reader.framed, extract fields in terms of
   * frames, signified by a 32-bit BE value preceding
   * each frame.
   */
  private class U32BEFramer() extends (Buf => Seq[Buf]) {
    var state: Buf = Buf.Empty

    @tailrec
    private def loop(acc: Seq[Buf], buf: Buf): Seq[Buf] = {
      buf match {
        case Buf.U32BE(l, d) if d.length >= l =>
          loop(acc :+ d.slice(0, l), d.slice(l, d.length))
        case _ =>
          state = buf
          acc
      }
    }

    def apply(buf: Buf): Seq[Buf] = synchronized {
      loop(Seq.empty, state concat buf)
    }
  }
}
