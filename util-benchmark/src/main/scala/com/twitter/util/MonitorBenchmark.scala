package com.twitter.util

import org.openjdk.jmh.annotations.{Benchmark, Scope, State}

// ./sbt 'project util-benchmark' 'jmh:run MonitorBenchmark'
@State(Scope.Benchmark)
class MonitorBenchmark extends StdBenchAnnotations {

  private[this] val exception = new Exception("yah mon")

  private[this] val trueMon = new Monitor {
    def handle(exc: Throwable): Boolean = true
  }

  private[this] val falseMon = new Monitor {
    def handle(exc: Throwable): Boolean = false
  }

  private[this] val orElseFalseTrueMon =
    falseMon.orElse(trueMon)

  private[this] val orElseTrueFalseMon =
    trueMon.orElse(falseMon)

  private[this] val andThenFalseTrueMon =
    falseMon.andThen(trueMon)

  private[this] val andThenTrueFalseMon =
    trueMon.andThen(falseMon)

  private[this] def handle(mon: Monitor): Boolean = {
    mon.handle(exception)
  }

  @Benchmark
  def orElse_handle_falseTrue(): Boolean =
    handle(orElseFalseTrueMon)

  @Benchmark
  def orElse_handle_trueFalse(): Boolean =
    handle(orElseTrueFalseMon)

  @Benchmark
  def andThen_handle_falseTrue(): Boolean =
    handle(andThenFalseTrueMon)

  @Benchmark
  def andThen_handle_trueFalse(): Boolean =
    handle(andThenTrueFalseMon)

}
