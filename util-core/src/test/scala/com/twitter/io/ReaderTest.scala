package com.twitter.io

import com.twitter.concurrent.exp.AsyncStream
import com.twitter.io.Reader.ReaderDiscarded
import com.twitter.util.{Await, Future, Promise}
import java.io.{ByteArrayOutputStream, OutputStream}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers}

@RunWith(classOf[JUnitRunner])
class ReaderTest extends FunSuite with GeneratorDrivenPropertyChecks with Matchers {

  def arr(i: Int, j: Int) = Array.range(i, j).map(_.toByte)
  def buf(i: Int, j: Int) = Buf.ByteArray.Owned(arr(i, j))

  def toSeq(b: Option[Buf]) = b match {
    case None => fail("Expected full buffer")
    case Some(buf) =>
      val a = new Array[Byte](buf.length)
      buf.write(a, 0)
      a.toSeq
  }

  def assertRead(r: Reader, i: Int, j: Int) {
    val n = j-i
    val f = r.read(n)
    assertRead(f, i, j)
  }

  def undefined: AsyncStream[Reader] = throw new Exception

  private def assertRead(f: Future[Option[Buf]], i: Int, j: Int): Unit = {
    assert(f.isDefined)
    val b = Await.result(f)
    assert(toSeq(b) === Seq.range(i, j))
  }

  def assertWrite(w: Writer, i: Int, j: Int) {
    val buf = Buf.ByteArray.Owned(Array.range(i, j).map(_.toByte))
    val f = w.write(buf)
    assert(f.isDefined)
    assert(Await.result(f) === ((): Unit))
  }

  def assertWriteEmpty(w: Writer) {
    val f = w.write(Buf.Empty)
    assert(f.isDefined)
    assert(Await.result(f) === ((): Unit))
  }

  def assertDiscard(r: Reader) {
    val f = r.read(1)
    assert(!f.isDefined)
    r.discard()
    intercept[Reader.ReaderDiscarded] { Await.result(f) }
  }

  def assertReadWhileReading(r: Reader) {
    val f = r.read(1)
    intercept[IllegalStateException] { Await.result(r.read(1)) }
    assert(!f.isDefined)
  }

  def assertFailed(r: Reader, p: Promise[Option[Buf]]) {
    val f = r.read(1)
    assert(!f.isDefined)
    p.setException(new Exception)
    intercept[Exception] { Await.result(f) }
    intercept[Exception] { Await.result(r.read(0)) }
    intercept[Exception] { Await.result(r.read(1)) }
  }

  private def assertReadNone(r: Reader): Unit =
    assert(Await.result(r.read(1)) === None)

  private def assertReadEofAndClosed(rw: Reader.Writable): Unit = {
    assertReadNone(rw)
    assert(rw.close().isDone)
  }

  private val failedEx = new RuntimeException("ʕ •ᴥ•ʔ")

  private def assertFailedEx(f: Future[_]): Unit = {
    val thrown = intercept[RuntimeException] {
      Await.result(f)
    }
    assert(thrown === failedEx)
  }

  test("Reader.copy - source and destination equality") {
    forAll { (p: Array[Byte], q: Array[Byte], r: Array[Byte]) =>
      val rw = Reader.writable()
      val bos = new ByteArrayOutputStream

      val w = Writer.fromOutputStream(bos, 31)
      val f = Reader.copy(rw, w) ensure w.close()
      val g =
        rw.write(Buf.ByteArray.Owned(p)) before
          rw.write(Buf.ByteArray.Owned(q)) before
            rw.write(Buf.ByteArray.Owned(r)) before rw.close()

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
      def write(b: Int) { }
      override def write(b: Array[Byte], n: Int, m: Int) { throw new Exception }
    }
    val f = Writer.fromOutputStream(os).write(Buf.Utf8("."))
    intercept[Exception] { Await.result(f) }
  }

  test("Writer.fromOutputStream") {
    val closep = new Promise[Unit]
    val writep = new Promise[Array[Byte]]
    val os = new OutputStream {
      def write(b: Int) { }
      override def write(b: Array[Byte], n: Int, m: Int) { writep.setValue(b) }
      override def close() { closep.setDone() }
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

  test("Reader.writable") {
    val rw = Reader.writable()
    val wf = rw.write(buf(0, 6))
    assert(!wf.isDefined)
    assertRead(rw, 0, 3)
    assert(!wf.isDefined)
    assertRead(rw, 3, 6)
    assert(wf.isDefined)
  }

  test("Reader.readAll") {
    val rw = Reader.writable()
    val all = Reader.readAll(rw)
    assert(!all.isDefined)
    assertWrite(rw, 0, 3)
    assertWrite(rw, 3, 6)
    assert(!all.isDefined)
    assertWriteEmpty(rw)
    assert(!all.isDefined)
    Await.result(rw.close())
    assert(all.isDefined)
    val buf = Await.result(all)
    assert(toSeq(Some(buf)) === Seq.range(0, 6))
  }

  test("Reader.writable - write before read") {
    val rw = Reader.writable()
    val wf = rw.write(buf(0, 6))
    assert(!wf.isDefined)
    val rf = rw.read(6)
    assert(rf.isDefined)
    assert(toSeq(Await.result(rf)) === Seq.range(0, 6))
  }

  test("Reader.writable - partial read, then short read") {
    val rw = Reader.writable()
    val wf = rw.write(buf(0, 6))
    assert(!wf.isDefined)
    val rf = rw.read(4)
    assert(rf.isDefined)
    assert(toSeq(Await.result(rf)) === Seq.range(0, 4))

    assert(!wf.isDefined)
    val rf2 = rw.read(4)
    assert(rf2.isDefined)
    assert(toSeq(Await.result(rf2)) === Seq.range(4, 6))

    assert(wf.isDefined)
    assert(Await.result(wf) === ((): Unit))
  }

  test("Reader.writeable - fail while reading") {
    val rw = Reader.writable()
    val rf = rw.read(6)
    assert(!rf.isDefined)
    val exc = new Exception
    rw.fail(exc)
    assert(rf.isDefined)
    val exc1 = intercept[Exception] { Await.result(rf) }
    assert(exc eq exc1)
  }

  test("Reader.writable - fail before reading") {
    val rw = Reader.writable()
    rw.fail(new Exception)
    val rf = rw.read(10)
    assert(rf.isDefined)
    intercept[Exception] { Await.result(rf) }
  }

  test("Reader.writable - discard") {
    val rw = Reader.writable()
    rw.discard()
    val rf = rw.read(10)
    assert(rf.isDefined)
    intercept[Reader.ReaderDiscarded] { Await.result(rf) }
  }

  test("Reader.writable - close") {
    val rw = Reader.writable()
    val wf = rw.write(buf(0, 6)) before rw.close()
    assert(!wf.isDefined)
    assert(Await.result(rw.read(6)) === Some(buf(0, 6)))
    assert(!wf.isDefined)
    assertReadEofAndClosed(rw)
  }

  test("Reader.writable - write then reads then close") {
    val rw = Reader.writable()
    val wf = rw.write(buf(0, 6))

    assert(!wf.isDone)
    assertRead(rw, 0, 3)
    assert(!wf.isDone)
    assertRead(rw, 3, 6)
    assert(wf.isDone)

    assert(!rw.close().isDone)
    assertReadEofAndClosed(rw)
  }

  test("Reader.writable - read then write then close") {
    val rw = Reader.writable()

    val rf = rw.read(6)
    assert(!rf.isDefined)

    val wf = rw.write(buf(0, 6))
    assert(wf.isDone)
    assertRead(rf, 0, 6)

    assert(!rw.close().isDone)
    assertReadEofAndClosed(rw)
  }


  test("Reader.writable - write after fail") {
    val rw = Reader.writable()
    rw.fail(failedEx)

    assertFailedEx(rw.write(buf(0, 6)))
    val cf = rw.close()
    assert(!cf.isDone)

    assertFailedEx(rw.read(1))
    assertFailedEx(cf)
  }

  test("Reader.writable - write after close") {
    val rw = Reader.writable()
    val cf = rw.close()
    assert(!cf.isDone)
    assertReadEofAndClosed(rw)
    assert(cf.isDone)

    intercept[IllegalStateException] {
      Await.result(rw.write(buf(0, 1)))
    }
  }

  test("Reader.writable - write smaller buf than read is waiting for") {
    val rw = Reader.writable()
    val rf = rw.read(6)
    assert(!rf.isDefined)

    val wf = rw.write(buf(0, 5))
    assert(wf.isDone)
    assertRead(rf, 0, 5)

    assert(!rw.read(1).isDefined) // nothing pending
    rw.close()
    assertReadEofAndClosed(rw)
  }

  test("Reader.writable - write larger buf than read is waiting for") {
    val rw = Reader.writable()
    val rf = rw.read(3)
    assert(!rf.isDefined)

    val wf = rw.write(buf(0, 6))
    assert(!wf.isDone)
    assertRead(rf, 0, 3)
    assert(!wf.isDone)

    assertRead(rw.read(5), 3, 6) // read the rest
    assert(wf.isDone)

    assert(!rw.read(1).isDefined) // nothing pending to read
    assert(rw.close().isDone)
  }

  test("Reader.writable - write while write pending") {
    val rw = Reader.writable()
    val wf = rw.write(buf(0, 1))
    assert(!wf.isDone)

    intercept[IllegalStateException] {
      Await.result(rw.write(buf(0, 1)))
    }

    // the extraneous write should not mess with the 1st one.
    assertRead(rw, 0, 1)
  }

  test("Reader.writable - read after fail") {
    val rw = Reader.writable()
    rw.fail(failedEx)
    assertFailedEx(rw.read(1))
  }

  test("Reader.writable - read after close with no pending reads") {
    val rw = Reader.writable()
    assert(!rw.close().isDone)
    assertReadEofAndClosed(rw)
  }

  test("Reader.writable - read after close with pending data") {
    val rw = Reader.writable()

    val wf = rw.write(buf(0, 1))
    assert(!wf.isDone)

    // close before the write is satisfied wipes the pending write
    assert(!rw.close().isDone)
    intercept[IllegalStateException] {
      Await.result(wf)
    }
    assertReadEofAndClosed(rw)
  }

  test("Reader.writable - read while reading") {
    val rw = Reader.writable()
    val rf = rw.read(1)
    intercept[IllegalStateException] {
      Await.result(rw.read(1))
    }
    assert(!rf.isDefined)
  }

  test("Reader.writable - discard with pending read") {
    val rw = Reader.writable()

    val rf = rw.read(1)
    rw.discard()

    intercept[ReaderDiscarded] {
      Await.result(rf)
    }
  }

  test("Reader.writable - discard with pending write") {
    val rw = Reader.writable()

    val wf = rw.write(buf(0, 1))
    rw.discard()

    intercept[ReaderDiscarded] {
      Await.result(wf)
    }
  }

  test("Reader.writable - close not satisfied until writes are read") {
    val rw = Reader.writable()
    val cf = rw.write(buf(0, 6)).before(rw.close())
    assert(!cf.isDone)

    assertRead(rw, 0, 3)
    assert(!cf.isDone)

    assertRead(rw, 3, 6)
    assert(!cf.isDone)
    assertReadEofAndClosed(rw)
  }

  test("Reader.writable - close not satisfied until reads are fulfilled") {
    val rw = Reader.writable()
    val rf = rw.read(6)
    val cf = rf.flatMap { _ => rw.close() }
    assert(!rf.isDefined)
    assert(!cf.isDone)

    assert(rw.write(buf(0, 3)).isDone)

    assertRead(rf, 0, 3)
    assert(!cf.isDone)
    assertReadEofAndClosed(rw)
  }

  test("Reader.writable - close while read pending") {
    val rw = Reader.writable()
    val rf = rw.read(6)
    assert(!rf.isDefined)

    assert(rw.close().isDone)
    assert(rf.isDefined)
  }

  test("Reader.writable - close then close") {
    val rw = Reader.writable()
    assert(!rw.close().isDone)
    assertReadEofAndClosed(rw)
    assert(rw.close().isDone)
    assertReadEofAndClosed(rw)
  }

  test("Reader.writable - close after fail") {
    val rw = Reader.writable()
    rw.fail(failedEx)
    val cf = rw.close()
    assert(!cf.isDone)

    assertFailedEx(rw.read(1))
    assertFailedEx(cf)
  }

  test("Reader.writable - close before fail") {
    val rw = Reader.writable()
    val cf = rw.close()
    assert(!cf.isDone)

    rw.fail(failedEx)
    assert(!cf.isDone)

    assertFailedEx(rw.read(1))
    assertFailedEx(cf)
  }

  test("Reader.writable - close while write pending") {
    val rw = Reader.writable()
    val wf = rw.write(buf(0, 1))
    assert(!wf.isDone)
    val cf = rw.close()
    assert(!cf.isDone)
    intercept[IllegalStateException] {
      Await.result(wf)
    }
    assertReadEofAndClosed(rw)
  }

  test("Reader.concat") {
    forAll { (ss: List[String]) =>
      val readers = ss map { s => BufReader(Buf.Utf8(s)) }
      val buf = Reader.readAll(Reader.concat(AsyncStream.fromSeq(readers)))
      Await.result(buf) should equal(Buf.Utf8(ss.mkString))
    }
  }

  test("Reader.concat - discard") {
    val p = new Promise[Option[Buf]]
    val head = new Reader {
      def read(n: Int) = p
      def discard() = p.setException(new Reader.ReaderDiscarded)
    }
    val reader = Reader.concat(head +:: undefined)
    reader.discard()
    assert(p.isDefined)
  }

  test("Reader.concat - read while reading") {
    val p = new Promise[Option[Buf]]
    val head = new Reader {
      def read(n: Int) = p
      def discard() = p.setException(new Reader.ReaderDiscarded)
    }
    val reader = Reader.concat(head +:: undefined)
    assertReadWhileReading(reader)
  }

  test("Reader.concat - failed") {
    val p = new Promise[Option[Buf]]
    val head = new Reader {
      def read(n: Int) = p
      def discard() = p.setException(new Reader.ReaderDiscarded)
    }
    val reader = Reader.concat(head +:: undefined)
    assertFailed(reader, p)
  }

  test("Reader.concat - lazy tail") {
    val head = new Reader {
      def read(n: Int) = Future.exception(new Exception)
      def discard() { }
    }
    val p = new Promise[Unit]
    def tail: AsyncStream[Reader] = {
      p.setDone()
      AsyncStream.empty
    }
    val combined = Reader.concat(head +:: tail)
    val buf = Reader.readAll(combined)
    intercept[Exception] { Await.result(buf) }
    assert(!p.isDefined)
  }
}
