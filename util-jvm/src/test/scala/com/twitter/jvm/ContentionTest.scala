package com.twitter.jvm

import java.util.concurrent.locks.ReentrantLock

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.concurrent.Eventually
import org.scalatest.junit.JUnitRunner
import org.scalatest.time.{Millis, Seconds, Span}

import com.twitter.util.{Await, Promise}

class Philosopher {
  val ready = new Promise[Unit]
  private val lock = new ReentrantLock()
  def dine(neighbor: Philosopher): Unit = {
    lock.lockInterruptibly()
    ready.setValue(Unit)
    Await.ready(neighbor.ready)
    neighbor.dine(this)
    lock.unlock()
  }
}

@RunWith(classOf[JUnitRunner])
class ContentionTest extends FunSuite with Eventually {

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(15, Seconds)), interval = scaled(Span(5, Millis)))

  test("Deadlocks") {
    val c = new ContentionSnapshot()

    val descartes = new Philosopher()
    val plato = new Philosopher()

    val d = new Thread(new Runnable() {
      def run() { descartes.dine(plato) }
    })
    d.start()

    val p = new Thread(new Runnable() {
      def run() { plato.dine(descartes) }
    })
    p.start()
    Await.all(descartes.ready, plato.ready)

    eventually { assert(c.snap().deadlocks.size == 2) }
    d.interrupt()
    p.interrupt()
    p.join()
    d.join()
    assert(c.snap().deadlocks.size == 0)
  }
}
