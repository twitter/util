package com.twitter.io

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import com.twitter.util.{Await, Return}

@RunWith(classOf[JUnitRunner])
class ReaderTest extends FunSuite {
  def arr(i: Int, j: Int) = Array.range(i, j).map(_.toByte)
  def buf(i: Int, j: Int) = Buf.ByteArray(arr(i, j))
  
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
    val buf = Buf.ByteArray(Array.range(i, j).map(_.toByte))
    val f = w.write(buf)
    assert(f.isDefined)
    assert(Await.result(f) === ())
  }
  
  def assertWriteEmpty(w: Writer) {
    val f = w.write(Buf.Empty)
    assert(f.isDefined)
    assert(Await.result(f) === ())
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
}
