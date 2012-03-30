package com.twitter.concurrent

import java.util.concurrent.CountDownLatch
import scala.collection.mutable.{ArrayBuffer, SynchronizedBuffer}

import org.specs.SpecificationWithJUnit

class IVarSpec extends SpecificationWithJUnit {
  "IVar" should {
    val iv = new IVar[Int]
    "invoke gets after value is set" in {
      var value: Option[Int] = None
      iv.get { v => value = Some(v) }
      value must beNone

      iv.set(123) must beTrue
      value must beSome(123)
    }

    "set value once" in {
      iv.set(123) must beTrue
      iv() must be_==(123)
      iv.set(333) must beFalse
      iv() must be_==(123)
    }

    "invoke multiple gets" in {
      var count = 0
      iv.get { _ => count += 1 }
      iv.get { _ => count += 1 }
      iv.set(123)
      count must be_==(2)
      iv.get { _ => count += 1 }
      count must be_==(3)
    }

    "chain properly" in {
      val order = new ArrayBuffer[Int]
      iv.chained.chained.get { _ => order += 3 }
      iv.chained.get { _ => order += 2 }
      iv.get { _ => order += 1 }
      iv.set(123)
      order.toSeq must be_==(Seq(1, 2, 3))
    }

    "defer recursive gets (run a schedule)" in {
      var order = new ArrayBuffer[Int]
      def get(n: Int) {
        iv.get { _ =>
          if (n > 0) get(n - 1)
          order += n
        }
      }
      get(10)
      iv.set(123)
      order.toSeq must be_==(10 to 0 by -1 toSeq)
    }

    "remove waiters on unget" in {
      var didrun = false
      val k = { _: Int => didrun = true }
      iv.get(k)
      iv.unget(k)
      iv.set(1)
      didrun must beFalse
    }
    
    "not remove another waiter on unget" in {
      var ran = false
      iv.get { _: Int => ran = true }
      iv.unget({_: Int => ()})
      iv.set(1)
      ran must beTrue
    }

    "merge" in {
      val a, b, c = new IVar[Int]
      val events = new ArrayBuffer[String]
      "merges waiters" in {
        b.get { v => events += "b(%d)".format(v) }
        a.get { v => events += "a(%d)".format(v) }
        val expected = Seq("a(1)", "b(1)")
        a.merge(b)
        def test(x: IVar[Int], y: IVar[Int]) {
          x.set(1)
          events must haveTheSameElementsAs(expected)
          x.isDefined must beTrue
          y.isDefined must beTrue

        }
        "a <- b" in test(a, b)
        "b <- a" in test(b, a)
      }

      "works transitively" in {
        val c = new IVar[Int]
        a.merge(b)
        b.merge(c)

        c.set(1)
        a.isDefined must beTrue
        b.isDefined must beTrue
        a() must be_==(1)
        b() must be_==(1)
      }

      "inherits an already defined value" in {
        a.set(1)
        b.merge(a)
        b.isDefined must beTrue
        b() must be_==(1)
      }

      "does not fail if already defined" in {
        a.set(1)
        a.merge(b)
        b.isDefined must beTrue
        b() must be_==(1)
      }

      "twoway merges" in {
        "succeed when values are equal" in {
          a.set(1)
          b.set(1)
          a.merge(b) mustNot throwA[Throwable]
        }

        "succeeed when values aren't equal, retaining values (it's a noop)" in {
          a.set(1)
          b.set(2)
          a.merge(b) must throwA[IllegalArgumentException]
          a.poll must beSome(1)
          b.poll must beSome(2)
        }
      }

      "is idempotent" in {
        a.merge(b)
        a.merge(b)
        a.merge(b) mustNot throwA[Throwable]
        b.set(123)
        a.isDefined must beTrue
      }

      "performs path compression" in {
        var first = new IVar[Int]
        var i = new IVar[Int]
        i.set(1)
        first.merge(i)

        for (_ <- 0 until 100)
          (new IVar[Int]).merge(i)

        first.depth must be_==(0)
        i.depth must be_==(0)
      }

      "cycles" >> {
        "deals with cycles in the done state" in {
          a.set(1)
          a.isDefined must beTrue
          a.merge(a)
          a() must be_==(1)
        }

        "deals with shallow cycles in the waiting state" in {
          a.merge(a)
          a.set(1)
          a.isDefined must beTrue
          a() must be_==(1)
        }

        "deals with simple indirect cycles" in {
          a.merge(b)
          b.merge(c)
          c.merge(a)
          b.set(1)
          a.isDefined must beTrue
          b.isDefined must beTrue
          c.isDefined must beTrue
          a() must be_==(1)
          b() must be_==(1)
          c() must be_==(1)
        }
      }
    }

    "apply() recursively schedule (no deadlock)" in {
      @volatile var didit = false
      val t = new Thread("IVarSpec") {
        override def run() {
          val a, b = new IVar[Int]
          a.get { _ =>  // inside of the scheduler now
            a.get { _ =>
              b.set(1)  // this gets delayed
            }
            b.isDefined must beFalse
            b()
            didit = true
          }

          a.set(1)
        }
      }
      t.start()
      t.join(500/*ms*/)
      didit must beTrue
    }
  }
}
