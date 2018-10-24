package com.twitter.io

import com.twitter.concurrent.AsyncStream
import com.twitter.conversions.time._
import com.twitter.conversions.storage._
import com.twitter.util.{Await, Awaitable, Future, Promise}
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.concurrent.atomic.AtomicBoolean
import org.mockito.Mockito._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers}
import scala.annotation.tailrec

class ReaderTest
    extends FunSuite
    with GeneratorDrivenPropertyChecks
    with Matchers
    with Eventually
    with IntegrationPatience {

  private def arr(i: Int, j: Int) = Array.range(i, j).map(_.toByte)
  private def buf(i: Int, j: Int) = Buf.ByteArray.Owned(arr(i, j))

  private def await[T](t: Awaitable[T]): T = Await.result(t, 5.seconds)

  def undefined: AsyncStream[Reader[Buf]] = throw new Exception

  private def assertReadWhileReading(r: Reader[Buf]): Unit = {
    val f = r.read(1)
    intercept[IllegalStateException] { await(r.read(1)) }
    assert(!f.isDefined)
  }

  private def assertFailed(r: Reader[Buf], p: Promise[Option[Buf]]): Unit = {
    val f = r.read(1)
    assert(!f.isDefined)
    p.setException(new Exception)
    intercept[Exception] { await(f) }
    intercept[Exception] { await(r.read(0)) }
    intercept[Exception] { await(r.read(1)) }
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
            rw.write(Buf.ByteArray.Owned(r)).before { rw.close() }
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

  test("Reader.chunked") {
    val stringAndChunk = for {
      s <- Gen.alphaStr
      i <- Gen.posNum[Int].suchThat(_ <= s.length)
    } yield (s, i)

    forAll(stringAndChunk) {
      case (s, i) =>
        val r = Reader.chunked(Reader.fromBuf(Buf.Utf8(s), 32), i)

        def readLoop(): Unit = await(r.read(Int.MaxValue)) match {
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
      def read(n: Int): Future[Option[Buf]] = p
      def discard(): Unit = p.setException(new ReaderDiscardedException)
    }
    val reader = Reader.concat(head +:: undefined)
    reader.discard()
    assert(p.isDefined)
  }

  test("Reader.concat - read while reading") {
    val p = new Promise[Option[Buf]]
    val head = new Reader[Buf] {
      def read(n: Int): Future[Option[Buf]] = p
      def discard(): Unit = p.setException(new ReaderDiscardedException)
    }
    val reader = Reader.concat(head +:: undefined)
    assertReadWhileReading(reader)
  }

  test("Reader.concat - failed") {
    val p = new Promise[Option[Buf]]
    val head = new Reader[Buf] {
      def read(n: Int): Future[Option[Buf]] = p
      def discard(): Unit = p.setException(new ReaderDiscardedException)
    }
    val reader = Reader.concat(head +:: undefined)
    assertFailed(reader, p)
  }

  test("Reader.concat - lazy tail") {
    val head = new Reader[Buf] {
      def read(n: Int): Future[Option[Buf]] = Future.exception(new Exception)
      def discard(): Unit = ()
    }
    val p = new Promise[Unit]
    def tail: AsyncStream[Reader[Buf]] = {
      p.setDone()
      AsyncStream.empty
    }
    val combined = Reader.concat(head +:: tail)
    val buf = Reader.readAll(combined)
    intercept[Exception] { await(buf) }
    assert(!p.isDefined)
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
    val as = AsyncStream(buf(1, 10))
    val r = Reader.fromAsyncStream(as)
    val f = Reader.readAll(r)
    assert(await(f) == buf(1, 10))
  }

  test("Reader.fromAsyncStream fails on exceptional stream") {
    val as = AsyncStream.exception(new Exception())
    val r = Reader.fromAsyncStream(as)
    val f = Reader.readAll(r)
    intercept[Exception] { await(f) }
  }

  test("Reader.fromAsyncStream only evaluates tail when buffer is exhausted") {
    val tailEvaluated = new AtomicBoolean(false)
    def tail: AsyncStream[Buf] = {
      tailEvaluated.set(true)
      AsyncStream.empty
    }
    val as = AsyncStream.mk(buf(0, 10), tail)
    val r = Reader.fromAsyncStream(as)

    // partially read the buffer
    await(r.read(9))
    assert(!tailEvaluated.get())

    // read the rest of the buffer
    await(r.read(2))
    assert(tailEvaluated.get())
  }

  test("Reader.toAsyncStream") {
    forAll { l: List[Byte] =>
      val buf = Buf.ByteArray.Owned(l.toArray)
      val as = Reader.toAsyncStream(Reader.fromBuf(buf, 1), chunkSize = 1)

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
        assert(await(r.read(Int.MaxValue)).contains(buf))
      }

      // make sure the reader signals EOF
      assert(await(r.read(Int.MaxValue)).isEmpty)
    }
  }

  test("Reader.framed reads empty frames") {
    val r = Reader.framed(BufReader(Buf.U32BE(0)), new ReaderTest.U32BEFramer())
    assert(await(r.read(Int.MaxValue)).contains(Buf.Empty))
    assert(await(r.read(Int.MaxValue)).isEmpty)
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
