package com.twitter.io

import com.twitter.util.Await
import org.junit.runner.RunWith
import org.scalacheck.Prop
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.Checkers

@RunWith(classOf[JUnitRunner])
class BufReaderTest extends FunSuite with Checkers {
  import Prop.{forAll, throws}

  test("BufReader") {
    check(forAll { (bytes: String) =>
      val buf = Buf.Utf8(bytes)
      val r = BufReader(buf)
      Await.result(Reader.readAll(r)) == buf
    })
  }

  test("BufReader - discard") {
    check(forAll { (bytes: String, n: Int) =>
      val r = BufReader(Buf.Utf8(bytes))
      r.discard()
      n < 0 ||
      bytes.length == 0 ||
      throws(classOf[Reader.ReaderDiscarded])({ Await.result(r.read(n)) })
    })
  }
}
