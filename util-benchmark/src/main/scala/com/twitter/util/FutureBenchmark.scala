package com.twitter.util

import org.openjdk.jmh.annotations._

class FutureBenchmark extends StdBenchAnnotations {
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
  def timeMap(state: PromiseUnitState): String = {
    val mapped = state.promise.map(state.MapFn)
    state.promise.setDone()
    Await.result(mapped)
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

  @Benchmark
  def runqBaseline(state: RunqState): Promise[Int] = {
    val p = new Promise[Int]()
    var next: Future[Int] = p
    var sum = 0
    var i = 0
    while (i < state.depth) {
      next = next.ensure { sum += 1 }
      i += 1
    }
    p
  }

  @Benchmark
  def runqSize(state: RunqState): Int = {
    // This setup really should be done in the fixture and for that
    // we need a fresh `Promise` each time through here.
    // While JMH's `Level.Invocation` is what we are looking for,
    // as the docs note, it is actually not appropriate here given
    // that it takes nowhere close to a millisecond per invocation.
    // By factoring it out into a benchmark, we can at least separate
    // that from the work we are interested in.
    val p = runqBaseline(state)
    // trigger the callbacks
    p.setValue(5)
    Await.result(p)
  }

}

object FutureBenchmark {
  final val N = 10

  private val RespondFn: Try[Unit] => Unit = { _ => () }

  private val NumToSelect = 5

  @State(Scope.Benchmark)
  private class RunqState {
    @Param(Array("1", "2", "3", "10", "20"))
    var depth: Int = 0
  }

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
    val MapFn = { _: Unit => "hi" }

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
    val p = new Promise[Unit]
    val futures: Seq[Future[Unit]] =
      Seq.fill(NumToSelect - 1) { p } :+ Future.Done
  }

  @State(Scope.Benchmark)
  private class SelectIndexState {
    val p = new Promise[Unit]
    val futures: IndexedSeq[Future[Unit]] =
      IndexedSeq.fill(NumToSelect - 1) { p } :+ Future.Done
  }

}
