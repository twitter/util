package com.twitter.io

import com.twitter.conversions.DurationOps._
import com.twitter.util.{Await, Future, Promise}
import java.io.{ByteArrayOutputStream, OutputStream}
import org.scalatest.{FunSuite, Matchers}

class WriterTest extends FunSuite with Matchers {

  private def await[A](f: Future[A]): A = Await.result(f, 5.seconds)

  test("fromOutputStream - close") {
    val w = Writer.fromOutputStream(new ByteArrayOutputStream)
    await(w.close())
    intercept[IllegalStateException] { await(w.write(Buf.Empty)) }
  }

  test("fromOutputStream - fail") {
    val w = Writer.fromOutputStream(new ByteArrayOutputStream)
    w.fail(new Exception)
    intercept[Exception] { await(w.write(Buf.Empty)) }
  }

  test("fromOutputStream - contramap") {
    val w = Writer.fromOutputStream(new ByteArrayOutputStream)
    val nw = w.contramap[String](s => Buf.Utf8(s))
    await(nw.write("hi"))
    await(nw.close())
  }

  test("fromOutputStream - error") {
    val os = new OutputStream {
      def write(b: Int): Unit = ()
      override def write(b: Array[Byte], n: Int, m: Int): Unit = throw new Exception
    }
    val f = Writer.fromOutputStream(os).write(Buf.Utf8("."))
    intercept[Exception] { await(f) }
  }

  test("fromOutputStream") {
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
    await(w.write(Buf.Utf8(".")))
    assert(writep.isDefined)
    assert(!closep.isDefined)
    await(w.close())
    assert(closep.isDefined)
  }
}
