package com.twitter.app

import com.twitter.util.Duration
import com.twitter.util.Time
import java.time.LocalTime
import org.scalatest.funsuite.AnyFunSuite

class FlaggableTest extends AnyFunSuite {

  test("Flaggable: parse booleans") {
    assert(Flaggable.ofBoolean.parse("true"))
    assert(!Flaggable.ofBoolean.parse("false"))

    intercept[Throwable] { Flaggable.ofBoolean.parse("") }
    intercept[Throwable] { Flaggable.ofBoolean.parse("gibberish") }
  }

  test("Flaggable: parse strings") {
    assert(Flaggable.ofString.parse("blah") == "blah")
  }

  test("Flaggagle: parse dates and times") {
    val t = Time.fromSeconds(123)
    assert(Flaggable.ofTime.parse(t.toString) == t)

    assert(Flaggable.ofDuration.parse("1.second") == Duration.fromSeconds(1))

    val tt = LocalTime.ofSecondOfDay(123)
    assert(Flaggable.ofJavaLocalTime.parse(tt.toString) == tt)
  }

  test("Flaggable: parse/show inet addresses") {
    // we aren't binding to this port, so any one will do.
    val port = 1589
    val local = Flaggable.ofInetSocketAddress.parse(s":$port")
    assert(local.getAddress.isAnyLocalAddress)
    assert(local.getPort == port)

    val ip = "127.0.0.1"
    val remote = Flaggable.ofInetSocketAddress.parse(s"$ip:$port")

    assert(remote.getHostName == "localhost")
    assert(remote.getPort == port)

    assert(Flaggable.ofInetSocketAddress.show(local) == s":$port")
    assert(Flaggable.ofInetSocketAddress.show(remote) == s"${remote.getHostName}:$port")
  }

  test("Flaggable: parse seqs") {
    assert(Flaggable.ofSeq[Int].parse("1,2,3,4") == Seq(1, 2, 3, 4))
    assert(Flaggable.ofSeq[Int].parse("") == Seq.empty[Int])
    assert(Flaggable.ofSeq[Boolean].parse("true,false,true") == Seq(true, false, true))
  }

  test("Flaggable: parse sets") {
    assert(Flaggable.ofSet[Int].parse("1,2,3,4") == Set(1, 2, 3, 4))
    assert(Flaggable.ofSet[Int].parse("") == Set.empty[Int])
    assert(Flaggable.ofSeq[Boolean].parse("true,false,true") == Seq(true, false, true))
  }

  test("Flaggable: parse maps") {
    assert(Flaggable.ofMap[Int, Int].parse("1=2,3=4") == Map(1 -> 2, 3 -> 4))
    assert(
      Flaggable
        .ofMap[String, Boolean].parse("foo=true,bar=false") == Map("foo" -> true, "bar" -> false))
  }

  test("Flaggable: parse maps with comma-separated values") {
    assert(
      Flaggable.ofMap[String, Seq[Int]].parse("a=1,2,3,3,b=4,5") == // 1000191039
        Map("a" -> Seq(1, 2, 3, 3), "b" -> Seq(4, 5))
    )
  }

  test("Flaggable: parse tuples") {
    assert(Flaggable.ofTuple[Int, String].parse("1,hello") == ((1, "hello")))
    assert(Flaggable.ofTuple[String, Boolean].parse("foo,true") == (("foo", true)))
    intercept[IllegalArgumentException] { Flaggable.ofTuple[Int, String].parse("1") }
  }

  test("Flaggable: expose types and kinds") {
    assert(Flaggable.ofFile.isInstanceOf[Flaggable.Typed[_]])
    assert(Flaggable.ofJavaBoolean.isInstanceOf[Flaggable.Typed[_]])

    assert(Flaggable.ofSeq[Int].isInstanceOf[Flaggable.Generic[_]])
    assert(Flaggable.ofJavaSet[Int].isInstanceOf[Flaggable.Generic[_]])
  }
}
