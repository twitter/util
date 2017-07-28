package com.twitter.finagle.stats

import org.scalatest.{FunSuite, OneInstancePerTest}

class VerbosityAdjustingStatsReceiverTest extends FunSuite with OneInstancePerTest {

  val inMemory = new InMemoryStatsReceiver()
  val verbose = new VerbosityAdjustingStatsReceiver(inMemory, Verbosity.Debug)

  test("adjusts default verbosity") {
    verbose.counter("foo")
    verbose.stat("bar")
    verbose.addGauge("baz")(0f)

    assert(inMemory.verbosity(Seq("foo")) == Verbosity.Debug)
    assert(inMemory.verbosity(Seq("bar")) == Verbosity.Debug)
    assert(inMemory.verbosity(Seq("baz")) == Verbosity.Debug)
  }

  test("prefers explicit verbosity") {
    verbose.counter(Verbosity.Default, "foo")
    verbose.stat(Verbosity.Default, "bar")
    verbose.addGauge(Verbosity.Default, "baz")(0f)

    assert(inMemory.verbosity(Seq("foo")) == Verbosity.Default)
    assert(inMemory.verbosity(Seq("bar")) == Verbosity.Default)
    assert(inMemory.verbosity(Seq("baz")) == Verbosity.Default)
  }
}
