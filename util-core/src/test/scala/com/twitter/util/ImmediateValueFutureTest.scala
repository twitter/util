package com.twitter.util

import com.twitter.concurrent.Scheduler
import java.util.concurrent.atomic.AtomicBoolean
import org.scalatest.funsuite.AnyFunSuite

class ImmediateValueFutureTest extends AnyFunSuite {

  // The following is intended to demonstrate the differing recursive behaviour of `ConstFuture`
  // and `ImmediateValueFuture`. `ConstFuture` essentially does tail-call elimination which means
  // the stack will not grow during a recursive call. `ImmediateValueFuture` does not, so the stack
  // will grow with each call!
  def recurseAndGetStackSizes(f: Future[Unit]): Seq[Int] = {
    val stop = new AtomicBoolean(false)

    @volatile var loopStackSizes: Seq[Int] = Seq.empty

    def loop(): Future[Unit] = {
      if (stop.get) {
        Future.Done
      } else {
        f.flatMap { _ =>
          loopStackSizes = loopStackSizes :+ new Throwable().getStackTrace.length
          loop()
        }
      }
    }

    FuturePool.unboundedPool {
      loop()
    }

    while (loopStackSizes.size < 10) {}
    stop.set(true)
    loopStackSizes
  }

  test("ConstFuture recursion does not grow the stack") {
    val loopStackSizes = recurseAndGetStackSizes(Future.const(Return[Unit](())))
    assert(loopStackSizes.forall(_ == loopStackSizes.head))
  }

  test("ImmediateValueFuture recursion does grow the stack") {
    val loopStackSizes = recurseAndGetStackSizes(new ImmediateValueFuture(()))
    assert(loopStackSizes.zip(loopStackSizes.tail).forall { case (a, b) => a < b })
  }

  test(s"ImmediateValueFuture.interruptible should do nothing") {
    val f = new ImmediateValueFuture(())
    val i = f.interruptible()
    i.raise(new Exception())
    assert(f.poll.contains(Return[Unit](())))
    assert(i.poll.contains(Return[Unit](())))
  }

  test(s"ImmediateValueFuture should propagate locals and restore original context in `respond`") {
    val local = new Local[Int]
    val f = new ImmediateValueFuture(111)

    var ran = 0
    local() = 1010

    f.ensure {
      assert(local().contains(1010))
      local() = 1212
      f.ensure {
        assert(local().contains(1212))
        local() = 1313
        ran += 1
      }
      assert(local().contains(1212))
      ran += 1
    }

    assert(local().contains(1010))
    assert(ran == 2)
  }

  test(
    s"ImmediateValueFuture should propagate locals and restore original context in `transform`") {
    val local = new Local[Int]
    val f = new ImmediateValueFuture(111)

    var ran = 0
    local() = 1010

    f.transform { tryRes =>
      assert(local().contains(1010))
      local() = 1212
      f.transform { tryRes =>
        assert(local().contains(1212))
        local() = 1313
        ran += 1
        Future.const(tryRes)
      }
      assert(local().contains(1212))
      ran += 1
      Future.const(tryRes)
    }

    assert(local().contains(1010))
    assert(ran == 2)
  }

  test(s"ImmediateValueFuture should propagate locals and restore original context in `map`") {
    val local = new Local[Int]
    val f = new ImmediateValueFuture(111)

    var ran = 0
    local() = 1010

    f.map { i =>
      assert(local().contains(1010))
      local() = 1212
      f.map { i =>
        assert(local().contains(1212))
        local() = 1313
        ran += 1
        i
      }
      assert(local().contains(1212))
      ran += 1
      i
    }

    assert(local().contains(1010))
    assert(ran == 2)
  }

  test(s"ImmediateValueFuture should not delay execution") {
    val numDispatchesBefore = Scheduler().numDispatches
    val f = new ImmediateValueFuture(111)

    var count = 0
    f.onSuccess { _ =>
      assert(count == 0)
      f.ensure {
        assert(count == 0)
        count += 1
      }

      assert(count == 1)
      count += 1
    }

    assert(count == 2)
    assert(Scheduler().numDispatches == numDispatchesBefore)
  }

  test(s"ImmediateValueFuture side effects should be monitored") {
    val inner = new ImmediateValueFuture(111)
    val exc = new Exception("a raw exception")

    var monitored = false

    val monitor = new Monitor {
      override def handle(exc: Throwable): Boolean = {
        monitored = true
        true
      }
    }

    Monitor.using(monitor) {
      val f = inner.respond { _ =>
        throw exc
      }

      assert(f.poll.contains(Return(111)))
      assert(monitored == true)
    }
  }

  test("ImmediateValueFuture.rescue returns self") {
    val f = new ImmediateValueFuture(111)
    val r = f.rescue {
      case e: Exception => Future.value(1)
    }

    assert(f eq r)
  }

  test("ImmediateValueFuture.map returns ImmediateValueFuture if f does not throw") {
    val f1 = new ImmediateValueFuture(111)

    val f2 = f1.map { _ =>
      "hello"
    }

    assert(f2.isInstanceOf[ImmediateValueFuture[String]])
  }

  test("ImmediateValueFuture.map returns Future.exception if f throws") {
    val f1 = new ImmediateValueFuture(111)

    val f2 = f1.map { _ =>
      throw new Exception("boom!")
    }

    intercept[Exception](Await.result(f2))
  }

  test("ImmediateValueFuture.flatMap returns Future.exception if f returns exceptional Future") {
    val f1 = new ImmediateValueFuture(111)

    val f2 = f1.flatMap { _ =>
      Future.exception(new Exception("boom!"))
    }

    intercept[Exception](Await.result(f2))
  }

  test("ImmediateValueFuture.transform returns Future.exception if f throws") {
    val f1 = new ImmediateValueFuture(111)

    val f2 = f1.transform { _ =>
      throw new Exception("boom!")
    }

    intercept[Exception](Await.result(f2))
  }
}
