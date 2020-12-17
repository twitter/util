package com.twitter.util

import com.twitter.concurrent.Offer
import org.openjdk.jmh.annotations._

// ./sbt 'project util-benchmark' 'jmh:run FutureBenchmark'
class FutureBenchmark extends StdBenchAnnotations {

  import FutureBenchmark._

  @Benchmark
  def timePromise(): Future[Unit] =
    new Promise[Unit]

  @Benchmark
  def timeConstUnit(): Future[Unit] =
    Future.Done.unit

  @Benchmark
  def timeConstVoid(): Future[Void] =
    Future.Done.voided

  @Benchmark
  def timeBy(state: ByState): Future[Unit] = {
    import state._

    new Promise[Unit].by(timer, now, exc)
  }

  @Benchmark
  @OperationsPerInvocation(N)
  def timeCallback(state: CallbackState): Unit = {
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
  def timeCollect(state: CollectState): Future[Seq[Int]] = {
    import state._

    Future.collect(futures)
  }

  @Benchmark
  def timeCollectToTry(state: CollectState): Future[Seq[Try[Int]]] = {
    import state._

    Future.collectToTry(futures)
  }

  @Benchmark
  def timeJoin(state: CollectState): Future[Unit] = {
    import state._

    Future.join(futures)
  }

  // The following three benchmarks (timeRespond, timeMap, timeFlatMap) only account
  // for continuation creation and its registration within a wait queue.
  //
  // See runqSize and runqBaseline benchmarks for analysing Promise.WaitQueue.run
  // (i.e., promise satisfaction) performance.

  @Benchmark
  def timeRespond(): Future[Unit] = {
    new Promise[Unit].respond(RespondFn)
  }

  @Benchmark
  def timeMap(): Future[Unit] = {
    new Promise[Unit].map(MapFn)
  }

  @Benchmark
  def timeFlatMap(): Future[Unit] = {
    new Promise[Unit].flatMap(FlatMapFn)
  }

  @Benchmark
  def fromConstMap: Future[Unit] =
    Future.Done.map(MapFn)

  @Benchmark
  def fromConstFlatMapToConst: Future[Unit] =
    Future.Done.flatMap(FlatMapFn)

  @Benchmark
  def fromConstTransformToConst: Future[Unit] =
    Future.Done.transform(TransformFn)

  @Benchmark
  def timeConstFilter(): Future[Unit] =
    Future.Done.filter(FilterFn)

  @Benchmark
  def timeSelect(state: SelectState): Future[(Try[Unit], Seq[Future[Unit]])] = {
    import state._

    Future.select(futures)
  }

  @Benchmark
  def timeSelectIndex(state: SelectIndexState): Future[Int] = {
    import state._

    Future.selectIndex(futures)
  }

  @Benchmark
  def timeWhileDo(): Future[Unit] = {
    var i = 0

    def continue: Boolean =
      if (i < N) {
        i += 1
        true
      } else {
        false
      }

    Future.whileDo(continue)(Future.Done)
  }

  @Benchmark
  def timeEach(state: EachState): Future[Nothing] = {
    import state._

    var i = 0

    def next: Future[Unit] =
      if (i < num) {
        i += 1
        Future.Unit
      } else {
        Future.???
      }

    Future.each(next)(body)
  }

  @Benchmark
  @OperationsPerInvocation(N)
  def timeParallel(): Seq[Future[Unit]] = {
    Future.parallel(N)(Future.Unit)
  }

  @Benchmark
  def timeToOffer(): Offer[Try[Unit]] = {
    Future.Unit.toOffer
  }

  @Benchmark
  def timeFlatten(state: FlattenState): Future[Unit] = {
    import state._

    future.flatten
  }

  @Benchmark
  def timeLiftTotry(): Future[Try[Unit]] = {
    Future.Unit.liftToTry
  }

  @Benchmark
  def timeLowerFromTry(state: LowerFromTryState): Future[Unit] = {
    import state._

    future.lowerFromTry
  }

  @Benchmark
  def timeUnit(): Unit = {
    val promise = new Promise[Unit]()
    val unit = promise.unit
    promise.setDone()
    Await.result(unit)
  }

  @Benchmark
  def timeVoided(): Void = {
    val promise = new Promise[Unit]()
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

  def buildChain(root: Promise[Int], state: RunqState): Future[Int] = {
    var next: Future[Int] = root
    var sum = 0
    var i = 0

    val fmap = { i: Int => Future.value(i + 1) }
    val respond = { _: Try[Int] => sum += 1 }
    val map = { i: Int => i + 1 }
    val filter = { _: Int => true }

    while (i < state.depth) {
      next = i % 4 match {
        case 0 => next.flatMap(fmap)
        case 1 => next.respond(respond)
        case 2 => next.map(map)
        case 3 => next.filter(filter)
      }
      i += 1
    }
    next
  }

  @Benchmark
  def runChain(state: RunqState): Int = {
    val p = new Promise[Int]()
    val f = buildChain(p, state)
    // trigger the callbacks
    p.setValue(5)
    Await.result(f)
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
  private val FlatMapFn: Unit => Future[Unit] = { _: Unit => Future.Unit }
  private val MapFn: Unit => Unit = { _: Unit => () }
  private val TransformFn: Try[Unit] => Future[Unit] = { _: Try[Unit] => Future.Done }
  private val FilterFn: Unit => Boolean = { _ => true }

  @State(Scope.Benchmark)
  class ByState {
    val timer: Timer = Timer.Nil
    val now: Time = Time.now
    val exc: TimeoutException = new TimeoutException("")
  }

  @State(Scope.Benchmark)
  class RunqState {
    @Param(Array("1", "2", "3", "10", "20"))
    var depth: Int = 0
  }

  private val StringFuture = Future.value("hi")

  @State(Scope.Thread)
  class CollectState {
    @Param(Array("0", "1", "10", "100"))
    var size: Int = 0

    var futures: List[Future[Int]] = _

    @Setup
    def prepare(): Unit = {
      futures = (0 until size).map { i => Future.value(i) }.toList
    }
  }

  @State(Scope.Benchmark)
  class EachState {
    @Param(Array("10"))
    var num: Int = _

    val body: Unit => Unit = _ => ()
  }

  @State(Scope.Benchmark)
  class FlattenState {
    val future: Future[Future[Unit]] = Future.value(Future.Unit)
  }

  @State(Scope.Benchmark)
  class LowerFromTryState {
    val future: Future[Try[Unit]] = Future.Unit.liftToTry
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

    val p: Promise[Unit] = new Promise[Unit]
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

    val p: Promise[Unit] = new Promise[Unit]
    val futures: IndexedSeq[Future[Unit]] =
      if (numToSelect == 0)
        IndexedSeq.empty
      else
        IndexedSeq.fill(numToSelect - 1) { p } :+ Future.Done
  }
}
