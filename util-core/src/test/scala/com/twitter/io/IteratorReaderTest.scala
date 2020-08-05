package com.twitter.io

import com.twitter.conversions.DurationOps._
import com.twitter.util.{Await, Future}
import org.scalatestplus.scalacheck.Checkers
import org.scalatest.funsuite.AnyFunSuite

class IteratorReaderTest extends AnyFunSuite with Checkers {

  private def await[A](f: Future[A]): A = Await.result(f, 5.seconds)

  test("IteratorReader") {
    check { list: List[String] =>
      val r = Reader.fromIterator(list.iterator)
      await(Reader.readAllItems(r)).mkString == list.mkString
    }
  }

  test("IteratorReader - discard") {
    check { list: List[String] =>
      val r = Reader.fromIterator(list.iterator)
      r.discard()

      Await
        .ready(r.read(), 5.seconds).poll.exists(
          _.throwable.isInstanceOf[ReaderDiscardedException]
        )
    }
  }
}
