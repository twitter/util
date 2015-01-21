package com.twitter.finagle.stats

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CategorisingExceptionStatsHandlerTest extends FunSuite {
  val categoriser = (t: Throwable) => { "clienterrors" }

  test("CategorisingExceptionStatsHandler uses exports sensible set of stats") {
    val receiver = new InMemoryStatsReceiver

    val esh = new CategorizingExceptionStatsHandler(_ => Some("clienterrors"),
      PartialFunction.apply(_ => Some("service")), true)

    val cwe = new RuntimeException(new Exception("e"))
    esh.record(receiver, cwe)

    val keys = receiver.counters.keys.map(_.mkString("/")).toSeq.sorted

    assert(keys === Seq(
      "clienterrors",
      "clienterrors/java.lang.RuntimeException",
      "clienterrors/java.lang.RuntimeException/java.lang.Exception",
      "sourcedfailures/service",
      "sourcedfailures/service/java.lang.RuntimeException",
      "sourcedfailures/service/java.lang.RuntimeException/java.lang.Exception"
    ))
  }

  test("CategorisingExceptionStatsHandler uses category, source, exception chains and rollup") {
    val receiver = new InMemoryStatsReceiver

    val esh = new CategorizingExceptionStatsHandler(_ => Some("clienterrors"),
      _ => Some("service"), true)

    val cwe = new RuntimeException(new Exception("e"))
    esh.record(receiver, cwe)

    assert(receiver.counters.filterKeys(_.contains("failures")).size === 0)

    assert(receiver.counters.filterKeys(_.contains("clienterrors")).size === 3)
    assert(receiver.counters(Seq("clienterrors")) === 1)
    assert(receiver.counters(Seq("clienterrors", classOf[RuntimeException].getName)) === 1)
    assert(receiver.counters(Seq("clienterrors", classOf[RuntimeException].getName,
      classOf[Exception].getName)) === 1)

    assert(receiver.counters.filterKeys(_.contains("sourcedfailures")).size === 3)
    assert(receiver.counters(Seq("sourcedfailures", "service")) === 1)
    assert(receiver.counters(Seq("sourcedfailures", "service",
      classOf[RuntimeException].getName)) === 1)
    assert(receiver.counters(Seq("sourcedfailures", "service",
      classOf[RuntimeException].getName, classOf[Exception].getName)) === 1)
  }

  test("CategorisingExceptionStatsHandler skips unknown source and defaults to failures") {
    val receiver = new InMemoryStatsReceiver

    val esh = new CategorizingExceptionStatsHandler(_ => None, _ => None, true)

    esh.record(receiver, new RuntimeException(new Exception("e")))

    assert(receiver.counters.filterKeys(_.contains("failures")).size === 3)

    assert(receiver.counters.filterKeys(_.contains("sourcedfailures")).size === 0)
  }

  test("CategorisingExceptionStatsHandler supports no rollup") {
    val receiver = new InMemoryStatsReceiver

    val esh = new CategorizingExceptionStatsHandler(_ => Some("clienterrors"),
      _ => Some("service"), false)

    val cwe = new RuntimeException(new Exception("e"))
    esh.record(receiver, cwe)

    assert(receiver.counters.filterKeys(_.contains("failures")).size === 0)

    assert(receiver.counters.filterKeys(_.contains("clienterrors")).size === 2)
    assert(receiver.counters(Seq("clienterrors")) === 1)
    assert(receiver.counters(Seq("clienterrors", classOf[RuntimeException].getName,
      classOf[Exception].getName)) === 1)

    assert(receiver.counters.filterKeys(_.contains("sourcedfailures")).size === 2)
    assert(receiver.counters(Seq("sourcedfailures", "service")) === 1)
    assert(receiver.counters(Seq("sourcedfailures", "service",
      classOf[RuntimeException].getName, classOf[Exception].getName)) === 1)
  }
}
