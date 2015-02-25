package com.twitter.io

import java.io.{ByteArrayOutputStream, OutputStream}

import com.twitter.concurrent.Spool
import com.twitter.util.{Await, Future, Promise}
import org.junit.runner.RunWith
import org.scalatest.{Matchers, FunSuite}
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.GeneratorDrivenPropertyChecks

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
    assert(f.isDefined)
    val b = Await.result(f)
    assert(toSeq(b) === Seq.range(i, j))
  }

  def assertWrite(w: Writer, i: Int, j: Int) {
    val buf = Buf.ByteArray.Owned(Array.range(i, j).map(_.toByte))
    val f = w.write(buf)
    assert(f.isDefined)
    assert(Await.result(f) === ())
  }

  def assertWriteEmpty(w: Writer) {
    val f = w.write(Buf.Empty)
    assert(f.isDefined)
    assert(Await.result(f) === ())
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
    assert(Await.result(wf) === ())
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
    Await.result(wf)
    assert(Await.result(rw.read(6)) === None)
  }

  test("Reader.writable - close while write pending") {
    val rw = Reader.writable()
    val wf = rw.write(buf(0, 6))
    assert(!wf.isDefined)
    Await.ready(rw.close())

    intercept[IllegalStateException] {
      Await.result(wf)
    }
  }

  test("Reader.concat") {
    import Spool.seqToSpool

    forAll { (ss: List[String]) =>
      val readers = ss map { s => BufReader(Buf.Utf8(s)) }
      val buf = Reader.readAll(Reader.concat(readers.toSpool))
      Await.result(buf) should equal(Buf.Utf8(ss.mkString))
    }
  }

  test("Reader.concat - discard") {
    val p = new Promise[Option[Buf]]
    val head = new Reader {
      def read(n: Int) = p
      def discard() = p.setException(new Reader.ReaderDiscarded)
    }
    val tail = new Promise[Spool[Reader]]
    val reader = Reader.concat(head *:: tail)
    reader.discard()
    assert(p.isDefined === true)
  }

  test("Reader.concat - read while reading") {
    val p = new Promise[Option[Buf]]
    val head = new Reader {
      def read(n: Int) = p
      def discard() = p.setException(new Reader.ReaderDiscarded)
    }
    val tail = new Promise[Spool[Reader]]
    val reader = Reader.concat(head *:: tail)
    assertReadWhileReading(reader)
  }

  test("Reader.concat - failed") {
    val p = new Promise[Option[Buf]]
    val head = new Reader {
      def read(n: Int) = p
      def discard() = p.setException(new Reader.ReaderDiscarded)
    }
    val tail = new Promise[Spool[Reader]]
    val reader = Reader.concat(head *:: tail)
    assertFailed(reader, p)
  }

  test("Reader.concat - lazy tail") {
    val head = new Reader {
      def read(n: Int) = Future.exception(new Exception)
      def discard() { }
    }
    val p = new Promise[Spool[Reader]]
    def tail: Future[Spool[Reader]] = {
      p.setException(new Exception)
      p
    }
    val combined = Reader.concat(head *:: tail)
    val buf = Reader.readAll(combined)
    intercept[Exception] { Await.result(buf) }
    assert(p.isDefined === false)
  }
}
