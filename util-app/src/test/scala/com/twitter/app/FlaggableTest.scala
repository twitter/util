package com.twitter.app

import org.scalatest.FunSuite
import com.twitter.util.{Duration, Time}
import java.time.LocalTime

class FlaggableTest extends FunSuite {

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

    val ip = "141.211.133.111"
    val remote = Flaggable.ofInetSocketAddress.parse(s"$ip:$port")

    assert(remote.getHostName == remote.getHostName)
    assert(remote.getPort == port)

    assert(Flaggable.ofInetSocketAddress.show(local) == s":$port")
    assert(Flaggable.ofInetSocketAddress.show(remote) == s"${remote.getHostName}:$port")
  }

  test("Flaggable: parse seqs") {
    assert(Flaggable.ofSeq[Int].parse("1,2,3,4") == Seq(1, 2, 3, 4))
  }

  test("Flaggable: parse maps") {
    assert(Flaggable.ofMap[Int, Int].parse("1=2,3=4") == Map(1 -> 2, 3 -> 4))
  }

  test("Flaggable: parse maps with comma-separated values") {
    assert(
      Flaggable.ofMap[String, Seq[Int]].parse("a=1,2,3,3,b=4,5") ==
        Map("a" -> Seq(1, 2, 3, 3), "b" -> Seq(4, 5))
    )
  }

  test("Flaggable: parse maps of sets with comma-separated values") {
    assert(
      Flaggable.ofMap[String, Set[Int]].parse("a=1,2,3,3,b=4,5") ==
        Map("a" -> Set(1, 2, 3), "b" -> Set(4, 5))
    )
  }

  test("Flaggable: parse tuples") {
    assert(Flaggable.ofTuple[Int, String].parse("1,hello") == ((1, "hello")))
    intercept[IllegalArgumentException] { Flaggable.ofTuple[Int, String].parse("1") }
  }
}
