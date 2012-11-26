package com.twitter.benchmark.util

import com.google.caliper.{SimpleBenchmark, Runner}
import com.twitter.util.{Future, Promise, Try}

class FutureBenchmark extends SimpleBenchmark {
  // Multiplier required to do enough work
  // to be able to measure anything at all.
  val N = 10

  private def doCallback(nreps: Int, n: Int) {
    assert(n < 4)
    val f: Try[Unit] => Unit = {r => ()}
    var i = 0
    var j = 0
    while (i < nreps) {
      j = 0
      while (j<N) {
        val p = new Promise[Unit]
        if (n > 0) p.respond(f)
        if (n > 1) p.respond(f)
        if (n > 2) p.respond(f)
        j+=1
      }
      i += 1
    }
  }

  def timeCallback0(nreps: Int) = doCallback(nreps, 0)  // ie. creation
  def timeCallback1(nreps: Int) = doCallback(nreps, 1)
  def timeCallback2(nreps: Int) = doCallback(nreps, 2)
  def timeCallback3(nreps: Int) = doCallback(nreps, 3)

  def timeRespond(reps: Int) {
    var i = 0
    val f = { _: Try[Unit] => () }
    while (i < reps) {
      val p = new Promise[Unit]
      p respond f
      p.setValue(())
      i += 1
    }
  }

  def timeFlatMap(reps: Int) {
    var i = 0
    val unit = Future.value(())
    val f = { _: Unit => unit }
    while (i < reps) {
      val p =new Promise[Unit]
      p flatMap f
      p.setValue(())
      i += 1
    }
  }
}
