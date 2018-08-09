package com.twitter.io

import com.twitter.concurrent.AsyncStream
import com.twitter.conversions.time._
import com.twitter.util.{Await, Future, Promise}
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, OutputStream}
import org.mockito.Mockito._
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers}

class ReaderTest
    extends FunSuite
    with GeneratorDrivenPropertyChecks
    with Matchers
    with Eventually
    with IntegrationPatience {

  private def arr(i: Int, j: Int) = Array.range(i, j).map(_.toByte)
  private def buf(i: Int, j: Int) = Buf.ByteArray.Owned(arr(i, j))

  private def toSeq(b: Option[Buf]): Seq[Byte] = b match {
    case None => fail("Expected full buffer")
    case Some(buf) =>
      val a = new Array[Byte](buf.length)
      buf.write(a, 0)
      a.toSeq
  }

  def undefined: AsyncStream[Reader[Buf]] = throw new Exception

  private def assertReadWhileReading(r: Reader[Buf]): Unit = {
    val f = r.read(1)
    intercept[IllegalStateException] { Await.result(r.read(1)) }
    assert(!f.isDefined)
  }

  private def assertFailed(r: Reader[Buf], p: Promise[Option[Buf]]): Unit = {
    val f = r.read(1)
    assert(!f.isDefined)
    p.setException(new Exception)
    intercept[Exception] { Await.result(f) }
    intercept[Exception] { Await.result(r.read(0)) }
    intercept[Exception] { Await.result(r.read(1)) }
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

      Await.result(Future.join(f, g))

      val b = new ByteArrayOutputStream
      b.write(p)
      b.write(q)
      b.write(r)
      b.flush()

      bos.toByteArray should equal(b.toByteArray)
    }
  }

  test("Writer.fromOutputStream - close") {
    val w = Writer.fromOutputStream(new ByteArrayOutputStream)
    w.close()
    intercept[IllegalStateException] { Await.result(w.write(Buf.Empty)) }
  }

  test("Writer.fromOutputStream - fail") {
    val w = Writer.fromOutputStream(new ByteArrayOutputStream)
    w.fail(new Exception)
    intercept[Exception] { Await.result(w.write(Buf.Empty)) }
  }

  test("Writer.fromOutputStream - error") {
    val os = new OutputStream {
      def write(b: Int): Unit = ()
      override def write(b: Array[Byte], n: Int, m: Int): Unit = throw new Exception
    }
    val f = Writer.fromOutputStream(os).write(Buf.Utf8("."))
    intercept[Exception] { Await.result(f) }
  }

  test("Writer.fromOutputStream") {
    val closep = new Promise[Unit]
    val writep = new Promise[Array[Byte]]
    val os = new OutputStream {
      def write(b: Int): Unit = ()
      override def write(b: Array[Byte], n: Int, m: Int): Unit = writep.setValue(b)
      override def close(): Unit = closep.setDone()
    }
    val w = Writer.fromOutputStream(os)
    assert(!writep.isDefined)
    assert(!closep.isDefined)
    Await.ready(w.write(Buf.Utf8(".")))
    assert(writep.isDefined)
    assert(!closep.isDefined)
    Await.ready(w.close())
    assert(closep.isDefined)
  }

  test("Reader.concat") {
    forAll { (ss: List[String]) =>
      val readers = ss.map { s =>
        BufReader(Buf.Utf8(s))
      }
      val buf = Reader.readAll(Reader.concat(AsyncStream.fromSeq(readers)))
      Await.result(buf) should equal(Buf.Utf8(ss.mkString))
    }
  }

  test("Reader.concat - discard") {
    val p = new Promise[Option[Buf]]
    val head = new Reader[Buf] {
      def read(n: Int): Future[Option[Buf]] = p
      def discard(): Unit = p.setException(new Reader.ReaderDiscarded)
    }
    val reader = Reader.concat(head +:: undefined)
    reader.discard()
    assert(p.isDefined)
  }

  test("Reader.concat - read while reading") {
    val p = new Promise[Option[Buf]]
    val head = new Reader[Buf] {
      def read(n: Int): Future[Option[Buf]] = p
      def discard(): Unit = p.setException(new Reader.ReaderDiscarded)
    }
    val reader = Reader.concat(head +:: undefined)
    assertReadWhileReading(reader)
  }

  test("Reader.concat - failed") {
    val p = new Promise[Option[Buf]]
    val head = new Reader[Buf] {
      def read(n: Int): Future[Option[Buf]] = p
      def discard(): Unit = p.setException(new Reader.ReaderDiscarded)
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
    intercept[Exception] { Await.result(buf) }
    assert(!p.isDefined)
  }

  test("Reader.fromStream closes resources on EOF read") {
    val in = spy(new ByteArrayInputStream(arr(0, 10)))
    val r = Reader.fromStream(in)
    val f = Reader.readAll(r)
    assert(Await.result(f, 5.seconds) == buf(0, 10))
    eventually {
      verify(in).close()
    }
  }

  test("Reader.fromStream closes resources on discard") {
    val in = spy(new ByteArrayInputStream(arr(0, 10)))
    val r = Reader.fromStream(in)
    r.discard()
    eventually {
      verify(in).close()
    }
  }
}
