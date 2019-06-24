package com.twitter.finagle.stats

import org.scalatest.FunSuite

class MultiCategorizingExceptionStatsHandlerTest extends FunSuite {
  test("uses label, flags, source, exception chain and rolls up") {
    val receiver = new InMemoryStatsReceiver

    val handler =
      new MultiCategorizingExceptionStatsHandler(
        _ => "clienterrors",
        _ => Set("interrupted", "restartable"),
        _ => Some("service"),
        true
      )

    val cwe = new RuntimeException(new Exception("e"))
    handler.record(receiver, cwe)

    val keys = receiver.counters.keys.map(_.mkString("/")).toSeq.sorted

    assert(receiver.counters(Seq("clienterrors")) == 1)
    assert(receiver.counters(Seq("clienterrors", "interrupted")) == 1)
    assert(receiver.counters(Seq("clienterrors", "restartable")) == 1)

    assert(
      receiver.counters(Seq("clienterrors", "interrupted", classOf[RuntimeException].getName)) == 1
    )
    assert(
      receiver.counters(
        Seq(
          "clienterrors",
          "interrupted",
          classOf[RuntimeException].getName,
          classOf[Exception].getName
        )
      ) == 1
    )

    assert(
      receiver.counters(Seq("clienterrors", "restartable", classOf[RuntimeException].getName)) == 1
    )
    assert(
      receiver.counters(
        Seq(
          "clienterrors",
          "restartable",
          classOf[RuntimeException].getName,
          classOf[Exception].getName
        )
      ) == 1
    )

    assert(receiver.counters(Seq("sourcedfailures", "service")) == 1)
    assert(
      receiver.counters(Seq("sourcedfailures", "service", classOf[RuntimeException].getName)) == 1
    )
    assert(
      receiver.counters(
        Seq(
          "sourcedfailures",
          "service",
          classOf[RuntimeException].getName,
          classOf[Exception].getName
        )
      ) == 1
    )

    assert(
      keys == Seq(
        "clienterrors",
        "clienterrors/interrupted",
        "clienterrors/interrupted/java.lang.RuntimeException",
        "clienterrors/interrupted/java.lang.RuntimeException/java.lang.Exception",
        "clienterrors/restartable",
        "clienterrors/restartable/java.lang.RuntimeException",
        "clienterrors/restartable/java.lang.RuntimeException/java.lang.Exception",
        "sourcedfailures/service",
        "sourcedfailures/service/java.lang.RuntimeException",
        "sourcedfailures/service/java.lang.RuntimeException/java.lang.Exception"
      )
    )
  }

  test("skips flags when it's empty") {
    val receiver = new InMemoryStatsReceiver

    val handler =
      new MultiCategorizingExceptionStatsHandler(
        _ => "clienterrors",
        _ => Set(),
        _ => Some("service"),
        true
      )

    val cwe = new RuntimeException(new Exception("e"))
    handler.record(receiver, cwe)

    val keys = receiver.counters.keys.map(_.mkString("/")).toSeq.sorted

    assert(receiver.counters(Seq("clienterrors")) == 1)
    assert(receiver.counters(Seq("clienterrors", classOf[RuntimeException].getName)) == 1)
    assert(
      receiver.counters(
        Seq("clienterrors", classOf[RuntimeException].getName, classOf[Exception].getName)
      ) == 1
    )

    assert(receiver.counters(Seq("sourcedfailures", "service")) == 1)
    assert(
      receiver.counters(Seq("sourcedfailures", "service", classOf[RuntimeException].getName)) == 1
    )
    assert(
      receiver.counters(
        Seq(
          "sourcedfailures",
          "service",
          classOf[RuntimeException].getName,
          classOf[Exception].getName
        )
      ) == 1
    )

    assert(
      keys == Seq(
        "clienterrors",
        "clienterrors/java.lang.RuntimeException",
        "clienterrors/java.lang.RuntimeException/java.lang.Exception",
        "sourcedfailures/service",
        "sourcedfailures/service/java.lang.RuntimeException",
        "sourcedfailures/service/java.lang.RuntimeException/java.lang.Exception"
      )
    )
  }

  test("skips unknown source and defaults to failures") {
    val receiver = new InMemoryStatsReceiver

    val handler = new MultiCategorizingExceptionStatsHandler
    handler.record(receiver, new RuntimeException(new Exception("e")))

    val keys = receiver.counters.keys.map(_.mkString("/")).toSeq.sorted

    assert(receiver.counters.filterKeys(_.contains("failures")).size == 3)
    assert(receiver.counters.filterKeys(_.contains("sourcedfailures")).size == 0)

    assert(
      keys == Seq(
        "failures",
        "failures/java.lang.RuntimeException",
        "failures/java.lang.RuntimeException/java.lang.Exception"
      )
    )
  }

  test("support no roll up") {
    val receiver = new InMemoryStatsReceiver

    val handler =
      new MultiCategorizingExceptionStatsHandler(
        _ => "clienterrors",
        _ => Set("interrupted", "restartable"),
        _ => Some("service"),
        false
      )

    val cwe = new RuntimeException(new Exception("e"))
    handler.record(receiver, cwe)

    val keys = receiver.counters.keys.map(_.mkString("/")).toSeq.sorted

    assert(receiver.counters(Seq("clienterrors")) == 1)
    assert(receiver.counters(Seq("clienterrors", "interrupted")) == 1)
    assert(receiver.counters(Seq("clienterrors", "restartable")) == 1)

    assert(
      receiver.counters(
        Seq(
          "clienterrors",
          "interrupted",
          classOf[RuntimeException].getName,
          classOf[Exception].getName
        )
      ) == 1
    )

    assert(
      receiver.counters(
        Seq(
          "clienterrors",
          "restartable",
          classOf[RuntimeException].getName,
          classOf[Exception].getName
        )
      ) == 1
    )

    assert(receiver.counters(Seq("sourcedfailures", "service")) == 1)
    assert(
      receiver.counters(
        Seq(
          "sourcedfailures",
          "service",
          classOf[RuntimeException].getName,
          classOf[Exception].getName
        )
      ) == 1
    )

    assert(
      keys == Seq(
        "clienterrors",
        "clienterrors/interrupted",
        "clienterrors/interrupted/java.lang.RuntimeException/java.lang.Exception",
        "clienterrors/restartable",
        "clienterrors/restartable/java.lang.RuntimeException/java.lang.Exception",
        "sourcedfailures/service",
        "sourcedfailures/service/java.lang.RuntimeException/java.lang.Exception"
      )
    )
  }
}
