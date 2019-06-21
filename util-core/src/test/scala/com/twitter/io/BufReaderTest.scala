package com.twitter.io

import com.twitter.conversions.DurationOps._
import com.twitter.util.{Await, Future}
import org.scalatest.FunSuite
import org.scalatest.prop.Checkers

class BufReaderTest extends FunSuite with Checkers {

  private def await[A](f: Future[A]): A = Await.result(f, 5.seconds)

  test("BufReader") {
    check { bytes: String =>
      val buf = Buf.Utf8(bytes)
      val r = Reader.fromBuf(buf, 8)
      await(Reader.readAll(r)) == buf
    }
  }

  test("BufReader - discard") {
    check { (bytes: String, n: Int) =>
      val r = Reader.fromBuf(Buf.Utf8(bytes), n)
      r.discard()

      n < 0 ||
      bytes.length == 0 ||
      Await
        .ready(r.read(), 5.seconds).poll.exists(
          _.throwable.isInstanceOf[ReaderDiscardedException]
        )
    }
  }
}
