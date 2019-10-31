package com.twitter.io

import com.twitter.conversions.DurationOps._
import com.twitter.util.{Await, Future}
import org.scalacheck.Prop.forAll
import org.scalacheck.Gen
import org.scalatest.FunSuite
import org.scalatestplus.scalacheck.Checkers

class BufReaderTest extends FunSuite with Checkers {

  private def await[A](f: Future[A]): A = Await.result(f, 5.seconds)

  test("BufReader") {
    check { bytes: String =>
      val buf = Buf.Utf8(bytes)
      val r = Reader.fromBuf(buf, 8)
      await(BufReader.readAll(r)) == buf
    }
  }

  test("BufReader - discard") {
    val alphaString = for {
      s <- Gen.alphaStr
    } yield s
    val positiveInt = for {
      n <- Gen.posNum[Int]
    } yield n

    check {
      forAll(alphaString, positiveInt) { (bytes, n) =>
        val r = Reader.fromBuf(Buf.Utf8(bytes), n)
        r.discard()

        bytes.length == 0 ||
        Await
          .ready(r.read(), 5.seconds).poll.exists(
            _.throwable.isInstanceOf[ReaderDiscardedException]
          )
      }
    }
  }

  test("BufReader - iterator") {
    val alphaString = for {
      s <- Gen.alphaStr
    } yield s
    val positiveInt = for {
      n <- Gen.posNum[Int]
    } yield n

    check {
      forAll(alphaString, positiveInt) { (bytes, n) =>
        var result = Buf.Empty
        val iterator = BufReader.iterator(Buf.Utf8(bytes), n)
        while (iterator.hasNext) {
          result = result.concat(iterator.next)
        }
        Buf.Utf8(bytes) == result
      }
    }
  }
  test("BufReader - readAll") {
    check { bytes: String =>
      val r = Reader.fromBuf(Buf.Utf8(bytes))
      await(BufReader.readAll(r)) == Buf.Utf8(bytes)
    }
  }
}
