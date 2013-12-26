package com.twitter.benchmark.util

import com.google.caliper.SimpleBenchmark
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
      p.setDone()
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
      p.setDone()
      i += 1
    }
  }

  def timeSelect(reps: Int) {
    val numToSelect = 5
    val done = new Promise[Unit]
    done.setDone()

    val fs: Seq[Future[Unit]] =
      Seq.fill(numToSelect - 1) { new Promise[Unit] } :+ done

    var i = 0
    while (i < reps) {
      Future.select(fs)
      i += 1
    }
  }

  def timeSelectIndex(reps: Int) {
    val numToSelect = 5
    val done = new Promise[Unit]
    done.setDone()
    val fs: IndexedSeq[Future[Unit]] =
      IndexedSeq.fill(numToSelect - 1) { new Promise[Unit] } :+ done

    var i = 0
    while (i < reps) {
      Future.selectIndex(fs)
      i += 1
    }
  }

}
