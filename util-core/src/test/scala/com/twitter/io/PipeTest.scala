package com.twitter.io

import com.twitter.conversions.DurationOps._
import com.twitter.util.{Await, Future, MockTimer, Return, Time}
import java.io.ByteArrayOutputStream
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PipeTest extends AnyFunSuite with Matchers with ScalaCheckDrivenPropertyChecks {

  private def await[A](f: Future[A]): A = Await.result(f, 5.seconds)

  private def arr(i: Int, j: Int) = Array.range(i, j).map(_.toByte)
  private def buf(i: Int, j: Int) = Buf.ByteArray.Owned(arr(i, j))

  // Workaround methods for dealing with Scala compiler warnings:
  //
  // assert(f.isDone) cannot be called directly because it results in a Scala compiler
  // warning: 'possible missing interpolator: detected interpolated identifier `$conforms`'
  //
  // This is due to the implicit evidence required for `Future.isDone` which checks to see
  // whether the Future that is attempting to have `isDone` called on it conforms to the type
  // of `Future[Unit]`. This is done using `Predef.$conforms`
  // https://www.scala-lang.org/api/2.12.2/scala/Predef$.html#$conforms[A]:A%3C:%3CA
  //
  // Passing that directly to `assert` causes problems because the `$conforms` is also seen as
  // an interpolated string. We get around it by evaluating first and passing the result to
  // `assert`.
  private def isDone(f: Future[Unit]): Boolean =
    f.isDone

  private def assertIsDone(f: Future[Unit]): Unit =
    assert(isDone(f))

  private def assertIsNotDone(f: Future[Unit]): Unit =
    assert(!isDone(f))

  private def assertRead(r: Reader[Buf], i: Int, j: Int): Unit = {
    val f = r.read()
    assertRead(f, i, j)
  }

  private def assertRead(f: Future[Option[Buf]], i: Int, j: Int): Unit = {
    assert(f.isDefined)
    val b = await(f)
    assert(toSeq(b) == Seq.range(i, j))
  }

  private def toSeq(b: Option[Buf]): Seq[Byte] = b match {
    case None => fail("Expected full buffer")
    case Some(buf) =>
      val a = new Array[Byte](buf.length)
      buf.write(a, 0)
      a.toSeq
  }

  private def assertWrite(w: Writer[Buf], i: Int, j: Int): Unit = {
    val buf = Buf.ByteArray.Owned(Array.range(i, j).map(_.toByte))
    val f = w.write(buf)
    assert(f.isDefined)
    assert(await(f.liftToTry) == Return.Unit)
  }

  private def assertWriteEmpty(w: Writer[Buf]): Unit = {
    val f = w.write(Buf.Empty)
    assert(f.isDefined)
    assert(await(f.liftToTry) == Return.Unit)
  }

  private def assertReadEofAndClosed(rw: Pipe[Buf]): Unit = {
    assertReadNone(rw)
    assertIsDone(rw.close())
  }

  private def assertReadNone(r: Reader[Buf]): Unit =
    assert(await(r.read()).isEmpty)

  private val failedEx = new RuntimeException("ʕ •ᴥ•ʔ")

  private def assertFailedEx(f: Future[_]): Unit = {
    val thrown = intercept[RuntimeException] {
      await(f)
    }
    assert(thrown == failedEx)
  }

  test("Pipe") {
    val rw = new Pipe[Buf]
    val wf = rw.write(buf(0, 6))
    assert(!wf.isDefined)
    assertRead(rw, 0, 6)
    assert(wf.isDefined)
    assert(await(wf.liftToTry) == Return(()))
  }

  test("Reader.readAll") {
    val rw = new Pipe[Buf]
    val all = BufReader.readAll(rw)
    assert(!all.isDefined)
    assertWrite(rw, 0, 3)
    assertWrite(rw, 3, 6)
    assert(!all.isDefined)
    assertWriteEmpty(rw)
    assert(!all.isDefined)
    await(rw.close())
    assert(all.isDefined)
    val buf = await(all)
    assert(toSeq(Some(buf)) == Seq.range(0, 6))
  }

  test("write before read") {
    val rw = new Pipe[Buf]
    val wf = rw.write(buf(0, 6))
    assert(!wf.isDefined)
    val rf = rw.read()
    assert(rf.isDefined)
    assert(toSeq(await(rf)) == Seq.range(0, 6))
  }

  test("fail while reading") {
    val rw = new Pipe[Buf]
    var closed = false
    rw.onClose.ensure { closed = true }
    val rf = rw.read()
    assert(!rf.isDefined)
    assert(!closed)
    val exc = new Exception
    rw.fail(exc)
    assert(closed)
    assert(rf.isDefined)
    val exc1 = intercept[Exception] { await(rf) }
    assert(exc eq exc1)
  }

  test("fail before reading") {
    val rw = new Pipe[Buf]
    rw.fail(new Exception)
    val rf = rw.read()
    assert(rf.isDefined)
    intercept[Exception] { await(rf) }
  }

  test("discard") {
    val rw = new Pipe[Buf]
    var closed = false
    rw.onClose.ensure { closed = true }
    rw.discard()
    val rf = rw.read()
    assert(rf.isDefined)
    assert(closed)
    intercept[ReaderDiscardedException] { await(rf) }
  }

  test("close") {
    val rw = new Pipe[Buf]
    var closed = false
    rw.onClose.ensure { closed = true }
    val wf = rw.write(buf(0, 6)) before rw.close()
    assert(!wf.isDefined)
    assert(!closed)
    assert(await(rw.read()).contains(buf(0, 6)))
    assert(!wf.isDefined)
    assertReadEofAndClosed(rw)
    assert(closed)
  }

  test("write then reads then close") { testWriteReadClose() }
  def testWriteReadClose() = {
    val rw = new Pipe[Buf]
    val wf = rw.write(buf(0, 6))

    assertIsNotDone(wf)
    assertRead(rw, 0, 6)
    assertIsDone(wf)
    assertIsNotDone(rw.close())
    assertReadEofAndClosed(rw)
  }

  test("read then write then close") { readWriteClose() }
  def readWriteClose() = {
    val rw = new Pipe[Buf]

    val rf = rw.read()
    assert(!rf.isDefined)

    val wf = rw.write(buf(0, 6))
    assertIsDone(wf)
    assertRead(rf, 0, 6)

    assertIsNotDone(rw.close())
    assertReadEofAndClosed(rw)
  }

  test("write after fail") {
    val rw = new Pipe[Buf]
    rw.fail(failedEx)

    assertFailedEx(rw.write(buf(0, 6)))
    val cf = rw.close()
    assertIsNotDone(cf)

    assertFailedEx(rw.read())
    assertFailedEx(cf)
  }

  test("write after close") {
    val rw = new Pipe[Buf]
    val cf = rw.close()
    assertIsNotDone(cf)
    assertReadEofAndClosed(rw)
    assertIsDone(cf)

    intercept[IllegalStateException] {
      await(rw.write(buf(0, 1)))
    }
  }

  test("write while write pending") {
    val rw = new Pipe[Buf]
    var closed = false
    rw.onClose.ensure { closed = true }
    val wf = rw.write(buf(0, 1))
    assertIsNotDone(wf)

    intercept[IllegalStateException] {
      await(rw.write(buf(0, 1)))
    }

    // the extraneous write should not mess with the 1st one.
    assertRead(rw, 0, 1)
    assert(!closed)
  }

  test("read after fail") {
    val rw = new Pipe[Buf]
    rw.fail(failedEx)
    assertFailedEx(rw.read())
  }

  def readAfterCloseNoPendingReads() = {
    val rw = new Pipe[Buf]
    assertIsNotDone(rw.close())
    assertReadEofAndClosed(rw)
  }
  test("read after close with no pending reads") {
    readAfterCloseNoPendingReads()
  }

  def readAfterClosePendingData = {
    val rw = new Pipe[Buf]

    val wf = rw.write(buf(0, 1))
    assertIsNotDone(wf)

    // close before the write is satisfied wipes the pending write
    assertIsNotDone(rw.close())
    intercept[IllegalStateException] {
      await(wf)
    }
    assertReadNone(rw)
    intercept[IllegalStateException] {
      await(rw.onClose)
    }
  }
  test("read after close with pending data") { readAfterClosePendingData }

  test("read while reading") {
    val rw = new Pipe[Buf]
    var closed = false
    rw.onClose.ensure { closed = true }
    val rf = rw.read()
    intercept[IllegalStateException] {
      await(rw.read())
    }
    assert(!rf.isDefined)
    assert(!closed)
  }

  test("discard with pending read") {
    val rw = new Pipe[Buf]

    val rf = rw.read()
    rw.discard()

    intercept[ReaderDiscardedException] {
      await(rf)
    }
  }

  test("discard with pending write") {
    val rw = new Pipe[Buf]

    val wf = rw.write(buf(0, 1))
    rw.discard()

    intercept[ReaderDiscardedException] {
      await(wf)
    }
  }

  test("close not satisfied until writes are read") {
    val rw = new Pipe[Buf]
    val cf = rw.write(buf(0, 6)).before(rw.close())
    assertIsNotDone(cf)

    assertRead(rw, 0, 6)
    assertIsNotDone(cf)
    assertReadEofAndClosed(rw)
  }

  def closeNotSatisfiedUntillAllReadsDone() = {
    val rw = new Pipe[Buf]
    val rf = rw.read()
    val cf = rf.flatMap { _ => rw.close() }
    assert(!rf.isDefined)
    assertIsNotDone(cf)

    assertIsDone(rw.write(buf(0, 3)))

    assertRead(rf, 0, 3)
    assertIsNotDone(cf)
    assertReadEofAndClosed(rw)
  }
  test("close not satisfied until reads are fulfilled")(closeNotSatisfiedUntillAllReadsDone())

  def closeWhileReadPending = {
    val rw = new Pipe[Buf]
    val rf = rw.read()
    assert(!rf.isDefined)

    assertIsDone(rw.close())
    assert(rf.isDefined)
  }
  test("close while read pending")(closeWhileReadPending)

  def closeTwice() = {
    val rw = new Pipe[Buf]
    assertIsNotDone(rw.close())
    assertReadEofAndClosed(rw)
    assertIsDone(rw.close())
    assertReadEofAndClosed(rw)
  }
  test("close then close")(closeTwice())

  test("close after fail") {
    val rw = new Pipe[Buf]
    rw.fail(failedEx)
    val cf = rw.close()
    assertIsNotDone(cf)

    assertFailedEx(rw.read())
    assertFailedEx(cf)
  }

  test("close before fail") {
    val timer = new MockTimer()
    Time.withCurrentTimeFrozen { ctrl =>
      val rw = new Pipe[Buf](timer)
      val cf = rw.close(1.second)
      assertIsNotDone(cf)

      ctrl.advance(1.second)
      timer.tick()

      rw.fail(failedEx)

      assertFailedEx(rw.read())
    }
  }

  test("close before fail within deadline") {
    val timer = new MockTimer()
    Time.withCurrentTimeFrozen { _ =>
      val rw = new Pipe[Buf](timer)
      val cf = rw.close(1.second)
      assertIsNotDone(cf)

      rw.fail(failedEx)
      assertIsNotDone(cf)

      assertFailedEx(rw.read())
      assertFailedEx(cf)
    }
  }

  test("close while write pending") {
    val rw = new Pipe[Buf]
    val wf = rw.write(buf(0, 1))
    assertIsNotDone(wf)
    val cf = rw.close()
    assertIsNotDone(cf)
    intercept[IllegalStateException] {
      await(wf)
    }
    assertReadNone(rw)
    intercept[IllegalStateException] {
      await(rw.onClose)
    }
  }

  test("close respects deadline") {
    val mockTimer = new MockTimer()
    Time.withCurrentTimeFrozen { timeCtrl =>
      val rw = new Pipe[Buf](mockTimer)
      val wf = rw.write(buf(0, 6))

      rw.close(1.second)

      assert(!wf.isDefined)
      assert(!rw.onClose.isDefined)

      timeCtrl.advance(1.second)
      mockTimer.tick()

      intercept[IllegalStateException] {
        await(wf)
      }

      intercept[IllegalStateException] {
        await(rw.onClose)
      }
      assertReadNone(rw)
    }
  }

  test("read complete data before close deadline") {
    val mockTimer = new MockTimer()
    val rw = new Pipe[Buf](mockTimer)
    Time.withCurrentTimeFrozen { timeCtrl =>
      val wf = rw.write(buf(0, 6)) before rw.close(1.second)

      assert(!wf.isDefined)
      assertRead(rw, 0, 6)
      assertReadNone(rw)
      assert(wf.isDefined)

      timeCtrl.advance(1.second)
      mockTimer.tick()

      assertReadEofAndClosed(rw)
    }
  }

  test("multiple reads read complete data before close deadline") {
    val mockTimer = new MockTimer()
    val buf = Buf.Utf8("foo")
    Time.withCurrentTimeFrozen { timeCtrl =>
      val rw = new Pipe[Buf](mockTimer)
      val writef = rw.write(buf)

      rw.close(1.second)

      assert(!writef.isDefined)
      assert(await(BufReader.readAll(rw)) == buf)
      assertReadNone(rw)
      assert(writef.isDefined)

      timeCtrl.advance(1.second)
      mockTimer.tick()

      assertReadEofAndClosed(rw)
    }
  }

  test("Pipe.copy - source and destination equality") {
    forAll { (p: Array[Byte], q: Array[Byte], r: Array[Byte]) =>
      val rw = new Pipe[Buf]
      val bos = new ByteArrayOutputStream

      val w = Writer.fromOutputStream(bos, 31)
      val f = Pipe.copy(rw, w).ensure(w.close())
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

  test("Pipe.copy - discard the source if cancelled") {
    var cancelled = false
    val src = new Reader[Int] {
      def read(): Future[Option[Int]] = Future.value(Some(1))
      def discard(): Unit = cancelled = true
      def onClose: Future[StreamTermination] = Future.never
    }
    val dest = new Pipe[Int]
    Pipe.copy(src, dest)
    assert(await(dest.read()) == Some(1))
    dest.discard()
    assert(cancelled)
  }

  test("Pipe.copy - discard the source if interrupted") {
    var cancelled = false
    val src = new Reader[Int] {
      def read(): Future[Option[Int]] = Future.value(Some(1))
      def discard(): Unit = cancelled = true
      def onClose: Future[StreamTermination] = Future.never
    }
    val dest = new Pipe[Int]
    val f = Pipe.copy(src, dest)
    assert(await(dest.read()) == Some(1))
    f.raise(new Exception("Freeze!"))
    assert(cancelled)
  }

}
