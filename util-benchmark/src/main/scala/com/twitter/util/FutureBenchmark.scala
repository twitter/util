package com.twitter.util

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
class FutureBenchmark {
  import FutureBenchmark._

  @Benchmark
  @OperationsPerInvocation(N)
  def timeCallback(state: CallbackState) {
    import state._

    assert(n < 4)
    var j = 0
    while (j < N) {
      val p = new Promise[Unit]
      if (n > 0) p.respond(RespondFn)
      if (n > 1) p.respond(RespondFn)
      if (n > 2) p.respond(RespondFn)
      j+=1
    }
  }

  @Benchmark
  def timeCollect(state: CollectState) {
    import state._

    Future.collect(stream)
  }

  @Benchmark
  def timeRespond(state: PromiseUnitState) {
    import state._

    promise.respond(RespondFn)
    promise.setDone()
  }

  @Benchmark
  def timeFlatMap(state: PromiseUnitState) {
    import state._

    promise.flatMap(FlatMapFn)
    promise.setDone()
  }

  @Benchmark
  def timeSelect(state: SelectState) {
    import state._

    Future.select(futures)
  }

  @Benchmark
  def timeSelectIndex(state: SelectIndexState) {
    import state._

    Future.selectIndex(futures)
  }

}

object FutureBenchmark {
  final val N = 10

  private val RespondFn: Try[Unit] => Unit = { _ => () }

  private val NumToSelect = 5

  @State(Scope.Thread)
  private class CollectState {
    var stream: Stream[Future[Int]] = _

    @Setup
    def prepare() {
      stream = (0 until FutureBenchmark.N * 100).map { i =>
        Future.value(i)
      }.toStream
    }
  }

  @State(Scope.Thread)
  private class PromiseUnitState {
    val FlatMapFn = { _: Unit => Future.Unit }

    var promise: Promise[Unit] = _

    @Setup
    def prepare() { promise = new Promise[Unit] }
  }

  @State(Scope.Benchmark)
  private class CallbackState {
    @Param(Array("0", "1", "2", "3"))
    var n: Int = _
  }

  @State(Scope.Benchmark)
  private class SelectState {
    val p = Promise[Unit]
    val futures: Seq[Future[Unit]] =
      Seq.fill(NumToSelect - 1) { p } :+ Future.Done
  }

  @State(Scope.Benchmark)
  private class SelectIndexState {
    val p = Promise[Unit]
    val futures: IndexedSeq[Future[Unit]] =
      IndexedSeq.fill(NumToSelect - 1) { p } :+ Future.Done
  }

}
