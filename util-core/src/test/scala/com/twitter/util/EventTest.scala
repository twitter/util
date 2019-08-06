package com.twitter.util

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.concurrent.{CountDownLatch, Executors}
import org.scalatest.FunSuite
import scala.collection.mutable
import scala.collection.compat._

class EventTest extends FunSuite {

  test("pub/sub while active") {
    val e = Event[Int]()
    val ref = new AtomicReference[Seq[Int]](Seq.empty)
    val sub = e.build.register(Witness(ref))

    assert(ref.get == Seq.empty)
    e.notify(1)
    assert(ref.get == Seq(1))
    e.notify(2)
    assert(ref.get == Seq(1, 2))

    Await.ready(sub.close())
    e.notify(3)
    assert(ref.get == Seq(1, 2))
  }

  test("Event.filter") {
    val calls = new AtomicInteger()
    val e = Event[Int]()
    val evens = e.filter { p =>
      calls.incrementAndGet()
      p % 2 == 0
    }
    val ref = new AtomicReference[Seq[Int]](Seq.empty)
    evens.build.register(Witness(ref))

    e.notify(1)
    assert(ref.get == Seq.empty)
    assert(1 == calls.get())

    e.notify(2)
    assert(ref.get == Seq(2))
    assert(2 == calls.get())
  }

  test("Event.map") {
    val calls = new AtomicInteger()
    val e = Event[Int]()
    val mapped = e.map { p =>
      calls.incrementAndGet()
      p * 2
    }

    val ref = new AtomicReference[Seq[Int]](Seq.empty)
    mapped.build.register(Witness(ref))

    e.notify(1)
    assert(ref.get == Seq(2))
    assert(1 == calls.get())
  }

  test("Event.collect") {
    val e = Event[Int]()
    val events = e collect { case i if i % 2 == 0 => i * 2 }
    val ref = new AtomicReference[Seq[Int]](Seq.empty)
    events.build.register(Witness(ref))

    e.notify(1)
    assert(ref.get == Seq.empty)
    e.notify(2)
    assert(ref.get == Seq(4))
    e.notify(3); e.notify(4)
    assert(ref.get == Seq(4, 8))
  }

  test("Event.foldLeft") {
    val e = Event[Int]()
    val sum = e.foldLeft(0)(_ + _)
    val ref = new AtomicReference[Int](0)
    sum.register(Witness(ref))
    e.notify(0)
    assert(ref.get == 0)
    e.notify(1)
    assert(ref.get == 1)
    e.notify(12)
    assert(ref.get == 13)
  }

  test("Event.sliding") {
    val e = Event[Int]()
    val w = e.sliding(3)
    val ref = new AtomicReference[Seq[Int]](Seq.empty)
    w.register(Witness(ref))

    e.notify(1)
    assert(ref.get == Seq(1))
    e.notify(2)
    assert(ref.get == Seq(1, 2))
    e.notify(3)
    assert(ref.get == Seq(1, 2, 3))
    e.notify(4)
    assert(ref.get == Seq(2, 3, 4))
  }

  test("Event.mergeMap") {
    val e = Event[Int]()
    val inners = new mutable.ArrayBuffer[Witness[String]]
    val e2 = e mergeMap { i =>
      val e = Event[String]()
      inners += e
      e
    }
    val ref = new AtomicReference[String]("")
    val closable = e2.register(Witness(ref))

    assert(inners.isEmpty)
    e.notify(1)
    assert(inners.size == 1)
    assert(ref.get == "")
    inners(0).notify("okay")
    assert(ref.get == "okay")

    e.notify(2)
    assert(inners.size == 2)
    assert(ref.get == "okay")
    inners(0).notify("notokay")
    assert(ref.get == "notokay")
    inners(1).notify("yay")
    assert(ref.get == "yay")
  }

  test("Event.mergeMap closes constituent witnesses") {
    @volatile var n = 0

    val e1, e2 = new Event[Int] {
      def register(w: Witness[Int]) = {
        n += 1
        w.notify(1)
        Closable.make { _ =>
          n -= 1; Future.Done
        }
      }
    }

    val e12 = e1 mergeMap { _ =>
      e2
    }

    val ref = new AtomicReference(Seq.empty[Int])
    val closable = e12.build.register(Witness(ref))
    assert(ref.get == Seq(1))
    assert(n == 2)
    Await.result(closable.close())
    assert(n == 0)
  }

  test("Event.select") {
    val e1 = Event[Int]()
    val e2 = Event[String]()
    val e = e1 select e2
    val ref = new AtomicReference[Seq[Either[Int, String]]](Seq.empty)
    e.build.register(Witness(ref))
    assert(ref.get.isEmpty)

    e1.notify(1)
    e1.notify(2)
    e2.notify("1")
    e1.notify(3)
    e2.notify("2")

    assert(ref.get == Seq(Left(1), Left(2), Right("1"), Left(3), Right("2")))
  }

  test("Event.zip") {
    val e1 = Event[Int]()
    val e2 = Event[String]()
    val e = e1 zip e2
    val ref = new AtomicReference[Seq[(Int, String)]](Seq.empty)
    e.build.register(Witness(ref))

    for (i <- 0 until 50) e1.notify(i)
    for (i <- 0 until 50) e2.notify(i.toString)
    for (i <- 50 until 100) e2.notify(i.toString)
    for (i <- 50 until 100) e1.notify(i)

    assert(ref.get == ((0 until 100) zip ((0 until 100) map (_.toString))))
  }

  test("Event.joinLast") {
    val e1 = Event[Int]()
    val e2 = Event[String]()
    val e = e1 joinLast e2
    val ref = new AtomicReference[(Int, String)]((0, ""))
    e.register(Witness(ref))

    assert(ref.get == ((0, "")))
    e1.notify(1)
    assert(ref.get == ((0, "")))
    e2.notify("ok")
    assert(ref.get == ((1, "ok")))
    e2.notify("ok1")
    assert(ref.get == ((1, "ok1")))
    e1.notify(2)
    assert(ref.get == ((2, "ok1")))
  }

  test("Event.take") {
    val e = Event[Int]()
    val e1 = e.take(5)
    val ref = new AtomicReference[Seq[Int]](Seq.empty)
    e1.build.register(Witness(ref))

    e.notify(1)
    e.notify(2)
    assert(ref.get == Seq(1, 2))
    e.notify(3)
    e.notify(4)
    e.notify(5)
    assert(ref.get == Seq(1, 2, 3, 4, 5))
    e.notify(6)
    e.notify(7)
    assert(ref.get == Seq(1, 2, 3, 4, 5))
  }

  test("Event.merge") {
    val e1, e2 = Event[Int]()
    val e = e1 merge e2
    val ref = new AtomicReference[Seq[Int]](Seq.empty)
    e.build.register(Witness(ref))

    for (i <- 0 until 100) e1.notify(i)
    for (i <- 100 until 200) e2.notify(i)
    for (i <- 200 until 300) {
      if (i % 2 == 0) e1.notify(i)
      else e2.notify(i)
    }

    assert(ref.get == Seq.range(0, 300))
  }

  test("Event.toVar") {
    val e = Event[Int]()
    val v = Var(0, e)

    val ref = new AtomicReference[Seq[Int]](Seq.empty)
    v.changes.build.register(Witness(ref))

    for (i <- 1 until 100) e.notify(i)
    assert(ref.get == Seq.range(0, 100))
  }

  test("Event.toFuture") {
    val e = Event[Int]()
    val f = e.toFuture()

    assert(!f.isDefined)
    e.notify(123)
    assert(f.isDefined)
    assert(Await.result(f) == 123)
  }

  test("Event.toFuture[Interrupted]") {
    val e = Event[Int]()
    val f = e.toFuture()

    assert(!f.isDefined)
    val exc = new Exception
    f.raise(exc)
    assert(f.isDefined)
    val caught = intercept[Exception] { Await.result(f) }
    assert(caught == exc)
  }

  test("Jake's composition test") {
    def sum(v: Var[Int]): Var[Int] = {
      val e = v.changes.foldLeft(0)(_ + _)
      Var(0, e)
    }

    def ite[T](i: Var[Boolean], t: Var[T], e: Var[T]) =
      i flatMap { i =>
        if (i) t else e
      }

    val b = Var(true)
    val x = Var(7)
    val y = Var(9)

    val z = ite(b, sum(x), sum(y))

    val ref = new AtomicReference[Int]
    z.changes.register(Witness(ref))

    assert(ref.get == 7)
    x() = 10
    assert(ref.get == 17)
    b() = false
    assert(ref.get == 9)
    y() = 10
    assert(ref.get == 19)
    b() = true
    assert(ref.get == 17)
    x() = 3
    assert(ref.get == 20)
  }

  test("Event.register: no races between registered witnesses") {
    val e = Event[Unit]()
    val counter = new AtomicInteger
    val n = 1000

    val nThreads = 8
    val latch = new CountDownLatch(n)
    val ex = Executors.newFixedThreadPool(nThreads)
    val addTask = new Runnable {
      def run() = {
        e.respond(_ => counter.incrementAndGet())
        latch.countDown()
      }
    }
    for (_ <- 1 to n) ex.execute(addTask)
    latch.await()
    ex.shutdown()

    e.notify(())
    assert(counter.get == n)
  }

  test("Event.dedupWith") {
    val e = Event[Int]()
    val ref = new AtomicReference[Seq[Int]]
    e.dedupWith { (a, b) =>
        a >= b
      }
      .build
      .register(Witness(ref))
    e.notify(0)
    e.notify(0)
    e.notify(1)
    e.notify(-1)
    e.notify(2)
    e.notify(1)
    e.notify(3)

    assert(ref.get() == List(0, 1, 2, 3))
  }

  test("Event.dedup") {
    val e = Event[Int]()
    val ref = new AtomicReference[Seq[Int]]
    e.dedup.buildAny[IndexedSeq[Int]].register(Witness(ref))
    e.notify(0)
    e.notify(0)
    e.notify(1)
    e.notify(1)
    e.notify(0)
    e.notify(0)

    assert(ref.get() == List(0, 1, 0))
  }

}
