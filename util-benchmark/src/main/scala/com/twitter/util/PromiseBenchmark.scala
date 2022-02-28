package com.twitter.util

import com.twitter.util.Promise.Detachable
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
class PromiseBenchmark extends StdBenchAnnotations {

  private[this] val StringFuture = Future.value("hi")

  private[this] val Value = Return("okok")

  private[this] val NoopCallback: Try[String] => Unit = _ => ()

  @Benchmark
  def attached(): Promise[String] = {
    Promise.attached(StringFuture)
  }

  @Benchmark
  def detach(state: PromiseBenchmark.PromiseDetachState): Unit = {
    import state._
    promise.detach()
  }

  @Benchmark
  def detachDetachableFuture(state: PromiseBenchmark.PromiseDetachState): Unit = {
    import state._
    detachableFuture.detach()
  }

  // used to isolate the work in the `updateIfEmpty` benchmark
  @Benchmark
  def newUnsatisfiedPromise(): Promise[String] = {
    new Promise[String]()
  }

  @Benchmark
  def updateIfEmpty(): Boolean = {
    val p = new Promise[String]()
    p.updateIfEmpty(Value)
  }

  @Benchmark
  def interrupts1(state: PromiseBenchmark.InterruptsState): Promise[String] = {
    Promise.interrupts(state.a)
  }

  @Benchmark
  def interrupts2(state: PromiseBenchmark.InterruptsState): Promise[String] = {
    Promise.interrupts(state.a, state.b)
  }

  @Benchmark
  def interruptsN(state: PromiseBenchmark.InterruptsState): Promise[String] = {
    Promise.interrupts(state.futures: _*)
  }

  @Benchmark
  def promiseIsDefined(state: PromiseBenchmark.PromiseState): Boolean = {
    if (state.i == state.len) state.i = 0
    val ret = state.promises(state.i).isDefined
    state.i += 1
    ret
  }

  @Benchmark
  def promisePoll(state: PromiseBenchmark.PromiseState): Option[Try[Unit]] = {
    if (state.i == state.len) state.i = 0
    val ret = state.promises(state.i).poll
    state.i += 1
    ret
  }

  @Benchmark
  def become(state: PromiseBenchmark.PromiseState): Promise[String] = {
    val p = new Promise[String](state.f)
    p.respond(NoopCallback)

    val q = new Promise[String]

    q.become(p) // p.link(q)
    p
  }
}

object PromiseBenchmark {
  @State(Scope.Thread)
  class PromiseDetachState {

    @Param(Array("10", "100", "1000"))
    var numAttached: Int = _

    var global: Promise[Unit] = _
    var attachedFutures: List[Future[Unit]] = _
    var promise: Promise[Unit] with Detachable = _

    var detachableFuture: Promise[Int] with Detachable = _

    @Setup
    def prepare(): Unit = {
      global = new Promise[Unit]
      attachedFutures = List.fill(numAttached) { Promise.attached(global) }
      promise = Promise.attached(global)

      // explicitly using a Future that is not a `Promise` to
      // test the `DetachableFuture` code paths.
      detachableFuture = Promise.attached(new ConstFuture(Return(5)))
    }
  }

  @State(Scope.Benchmark)
  class InterruptsState {
    val futures: List[Future[Int]] = (0 until 100).map(i => Future.value(i)).toList
    val a: Future[String] = Future.value("a")
    val b: Future[String] = Future.value("b")
  }

  @State(Scope.Thread)
  class PromiseState {
    /* state == WaitQueue */
    val pw: Promise[Unit] = new Promise[Unit]()

    /* state == Try */
    val pt: Promise[Unit] = new Promise[Unit](Return.Unit)

    /* state == Interruptible */
    val f: PartialFunction[Throwable, Unit] = { case _ => () }
    val pi: Promise[Unit] = new Promise[Unit](f)

    /* state == Promise */
    val pl: Promise[Unit] = Promise.attached[Unit](new Promise[Unit]())

    var i: Int = 0
    val promises: Array[Promise[Unit]] = Array(pw, pt, pi, pl)
    val len: Int = promises.length
  }
}
