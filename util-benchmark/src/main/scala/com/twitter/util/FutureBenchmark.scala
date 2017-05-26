package com.twitter.util

import org.openjdk.jmh.annotations._

class FutureBenchmark extends StdBenchAnnotations {

  import FutureBenchmark._

  @Benchmark
  def timePromise(): Future[Unit] =
    new Promise[Unit]

  @Benchmark
  def timeBy(state: ByState): Future[Unit] = {
    import state._

    new Promise[Unit].by(timer, now, exc)
  }

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
      j += 1
    }
  }

  @Benchmark
  def timeCollect(state: CollectState) {
    import state._

    Future.collect(futures)
  }

  @Benchmark
  def timeCollectToTry(state: CollectState) {
    import state._

    Future.collectToTry(futures)
  }

  @Benchmark
  def timeJoin(state: CollectState) {
    import state._

    Future.join(futures)
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
  def timeWhileDo(state: WhileDoState) {
    import state._

    Future.whileDo(continue)(future)
  }

  @Benchmark
  def timeEach(state: EachState) {
    import state._

    Future.each(next)(body)
  }

  @Benchmark
  @OperationsPerInvocation(N)
  def timeParallel(): Unit = {

    Future.parallel(N)(Future.Unit)
  }

  @Benchmark
  def timeToOffer(): Unit = {

    Future.Unit.toOffer
  }

  @Benchmark
  def timeFlatten(state: FlattenState) {
    import state._

    future.flatten
  }

  @Benchmark
  def timeLiftTotry(): Unit = {

    Future.Unit.liftToTry
  }

  @Benchmark
  def timeLowerFromTry(state: LowerFromTryState) {
    import state._

    future.lowerFromTry
  }

  @Benchmark
  def timeUnit(state: PromiseUnitState): Unit = {
    import state._
    val unit = promise.unit
    promise.setDone()
    Await.result(unit)
  }

  @Benchmark
  def timeVoided(state: PromiseUnitState): Void = {
    import state._
    val voided = promise.voided
    promise.setDone()
    Await.result(voided)
  }

  @Benchmark
  def runqBaseline(state: RunqState): Promise[Int] = {
    val p = new Promise[Int]()
    var next: Future[Int] = p
    var sum = 0
    var i = 0
    while (i < state.depth) {
      next = next.ensure {
        sum += 1
      }
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

  @Benchmark
  def monitored(): String = {
    val f = Future.monitored {
      StringFuture
    }
    Await.result(f)
  }

  @Benchmark
  def timeDelayed(): Future[Unit] = Future.Done.delayed(Duration.Top)(Timer.Nil)

  @Benchmark
  def timeSleep(): Future[Unit] = Future.sleep(Duration.Top)(Timer.Nil)
}

object FutureBenchmark {
  final val N = 10

  private val RespondFn: Try[Unit] => Unit = { _ => () }
  
  @State(Scope.Benchmark)
  class ByState {
    val timer = Timer.Nil
    val now: Time = Time.now
    val exc =  new TimeoutException("")
  }

  @State(Scope.Benchmark)
  class RunqState {
    @Param(Array("1", "2", "3", "10", "20"))
    var depth: Int = 0
  }

  val StringFuture = Future.value("hi")

  @State(Scope.Thread)
  class CollectState {
    @Param(Array("0", "1", "10", "100"))
    var size: Int = 0

    var futures: List[Future[Int]] = _

    @Setup
    def prepare(): Unit = {
      futures = (0 until size).map { i =>
        Future.value(i)
      }.toList
    }
  }

  @State(Scope.Thread)
  class PromiseUnitState {
    val FlatMapFn = { _: Unit => Future.Unit }
    val MapFn = { _: Unit => "hi" }

    var promise: Promise[Unit] = _

    @Setup
    def prepare(): Unit = {
      promise = new Promise[Unit]
    }
  }

  @State(Scope.Thread)
  class WhileDoState {
    private var i = 0

    def continue =
      if (i < N) {
        i += 1
        true
      } else
        false

    val future = Future.Unit

    @Setup
    def prepare(): Unit = {
      i = 0
    }
  }

  @State(Scope.Thread)
  class EachState {
    private var i = 0

    def next =
      if (i < N) {
        i += 1
        Future.Unit
      } else
        Future.???

    val body: Unit => Unit = _ => ()

    @Setup
    def prepare(): Unit = {
      i = 0
    }
  }

  @State(Scope.Benchmark)
  class FlattenState {
    val future = Future.value(Future.Unit)
  }

  @State(Scope.Benchmark)
  class LowerFromTryState {
    val future = Future.Unit.liftToTry
  }

  @State(Scope.Benchmark)
  class CallbackState {
    @Param(Array("0", "1", "2", "3"))
    var n: Int = _
  }

  @State(Scope.Benchmark)
  class SelectState {

    @Param(Array("0", "1", "10", "100"))
    var numToSelect = 0

    val p = new Promise[Unit]
    val futures: Seq[Future[Unit]] =
      if (numToSelect == 0)
        IndexedSeq.empty
      else
        IndexedSeq.fill(numToSelect - 1) { p } :+ Future.Done
  }

  @State(Scope.Benchmark)
  class SelectIndexState {

    @Param(Array("0", "1", "10", "100"))
    var numToSelect = 0

    val p = new Promise[Unit]
    val futures: IndexedSeq[Future[Unit]] =
      if (numToSelect == 0)
        IndexedSeq.empty
      else
        IndexedSeq.fill(numToSelect - 1) { p } :+ Future.Done
  }
}
