package com.twitter.util

import com.twitter.util.Promise.Detachable
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class PromiseBenchmark extends StdBenchAnnotations {

  private[this] val StringFuture = Future.value("hi")

  private[this] val Value = Return("okok")

  @Benchmark
  def attached(): Promise[String] = {
    Promise.attached(StringFuture)
  }

  @Benchmark
  def detach(state: PromiseBenchmark.PromiseDetachState) {
    import state._
    promise.detach()
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
  def interrupts(state: PromiseBenchmark.InterruptsState): Promise[String] = {
    Promise.interrupts(state.futures: _*)
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

    @Setup
    def prepare(): Unit = {
      global = new Promise[Unit]
      attachedFutures = List.fill(numAttached) { Promise.attached(global) }
      promise = Promise.attached(global)
    }
  }

  @State(Scope.Thread)
  class InterruptsState {
    var futures: List[Future[Int]] = _

    @Setup
    def prepare(): Unit = {
      futures = (0 until 100).map { i =>
        Future.value(i)
      }.toList
    }
  }
}