package com.twitter.io

import com.twitter.conversions.DurationOps._
import com.twitter.conversions.StorageUnitOps._
import com.twitter.util.{Await, Future}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scala.annotation.tailrec
import org.scalatest.funsuite.AnyFunSuite

class BufReaderTest extends AnyFunSuite with ScalaCheckDrivenPropertyChecks {

  private def await[A](f: Future[A]): A = Await.result(f, 5.seconds)

  test("BufReader - apply(buf)") {
    forAll { (bytes: String) =>
      val buf = Buf.Utf8(bytes)
      val r = BufReader(buf)
      assert(await(BufReader.readAll(r)) == buf)
    }
  }

  test("BufReader - apply(buf, chunkSize)") {
    val stringAndChunk = for {
      s <- Gen.alphaStr
      i <- Gen.posNum[Int].suchThat(_ <= s.length)
    } yield (s, i)

    forAll(stringAndChunk) {
      case (bytes, chunkSize) =>
        val buf = Buf.Utf8(bytes)
        val r = BufReader(buf, chunkSize)
        assert(await(BufReader.readAll(r)) == buf)
    }
  }

  test("BufReader - readAll") {
    forAll { (bytes: String) =>
      val r = Reader.fromBuf(Buf.Utf8(bytes))
      assert(await(BufReader.readAll(r)) == Buf.Utf8(bytes))
    }
  }

  test("BufReader.chunked") {
    val stringAndChunk = for {
      s <- Gen.alphaStr
      i <- Gen.posNum[Int].suchThat(_ <= s.length)
    } yield (s, i)

    forAll(stringAndChunk) {
      case (s, i) =>
        val r = BufReader.chunked(Reader.fromBuf(Buf.Utf8(s), 32), i)
        assert(await(BufReader.readAll(r)) == Buf.Utf8(s))
    }
  }

  test("BufReader.chunked by the chunkSize") {
    val stringAndChunk = for {
      s <- Gen.alphaStr
      i <- Gen.posNum[Int].suchThat(_ <= s.length)
    } yield (s, i)

    forAll(stringAndChunk) {
      case (s, i) =>
        val r = BufReader.chunked(Reader.fromBuf(Buf.Utf8(s), 32), i)

        @tailrec
        def readLoop(): Unit = await(r.read()) match {
          case Some(b) =>
            assert(b.length <= i)
            readLoop()
          case None => ()
        }

        readLoop()
    }
  }

  test("BufReader.framed reads framed data") {
    val getByteArrays: Gen[Seq[Buf]] = Gen.listOf(
      for {
        // limit arrays to a few kilobytes, otherwise we may generate a very large amount of data
        numBytes <- Gen.choose(0.bytes.inBytes, 2.kilobytes.inBytes)
        bytes <- Gen.containerOfN[Array, Byte](numBytes.toInt, Arbitrary.arbitrary[Byte])
      } yield Buf.ByteArray.Owned(bytes)
    )

    forAll(getByteArrays) { (buffers: Seq[Buf]) =>
      val buffersWithLength = buffers.map(buf => Buf.U32BE(buf.length).concat(buf))

      val r = BufReader.framed(BufReader(Buf(buffersWithLength)), new BufReaderTest.U32BEFramer())

      // read all of the frames
      buffers.foreach { buf => assert(await(r.read()).contains(buf)) }

      // make sure the reader signals EOF
      assert(await(r.read()).isEmpty)
    }
  }

  test("BufReader.framed reads empty frames") {
    val r = BufReader.framed(BufReader(Buf.U32BE(0)), new BufReaderTest.U32BEFramer())
    assert(await(r.read()).contains(Buf.Empty))
    assert(await(r.read()).isEmpty)
  }

  test("Framed works on non-list collections") {
    val r = BufReader.framed(BufReader(Buf.U32BE(0)), i => Vector(i))
    assert(await(r.read()).isDefined)
  }
}

object BufReaderTest {

  /**
   * Used to test BufReader.framed, extract fields in terms of
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
