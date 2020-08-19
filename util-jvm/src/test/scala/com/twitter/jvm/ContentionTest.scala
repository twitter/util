package com.twitter.jvm

import com.twitter.util.{Await, Promise}
import java.util.concurrent.locks.ReentrantLock
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.funsuite.AnyFunSuite

class Philosopher {
  val ready = new Promise[Unit]
  private val lock = new ReentrantLock()
  def dine(neighbor: Philosopher): Unit = {
    lock.lockInterruptibly()
    ready.setValue(())
    Await.ready(neighbor.ready)
    neighbor.dine(this)
    lock.unlock()
  }
}

class ContentionTest extends AnyFunSuite with Eventually {

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(15, Seconds)), interval = scaled(Span(5, Millis)))

  test("Deadlocks") {
    val c = new ContentionSnapshot()

    val descartes = new Philosopher()
    val plato = new Philosopher()

    val d = new Thread(new Runnable() {
      def run(): Unit = { descartes.dine(plato) }
    })
    d.start()

    val p = new Thread(new Runnable() {
      def run(): Unit = { plato.dine(descartes) }
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
