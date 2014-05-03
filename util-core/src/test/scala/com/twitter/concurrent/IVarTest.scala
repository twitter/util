package com.twitter.concurrent

import java.util.concurrent.CountDownLatch
import scala.collection.mutable.{ArrayBuffer, SynchronizedBuffer}
import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner


@RunWith(classOf[JUnitRunner])
class IVarTest extends WordSpec with ShouldMatchers {
  "IVar" should {
    class IVarHelper {
      val iv = new IVar[Int]
    }
    "invoke gets after value is set" in {
      val h = new IVarHelper
      import h._

      var value: Option[Int] = None
      iv.get { v => value = Some(v) }
      value shouldEqual None

      iv.set(123) shouldEqual true
      value shouldEqual Some(123)
    }

    "set value once" in {
      val h = new IVarHelper
      import h._

      iv.set(123) shouldEqual true
      iv() shouldEqual(123)
      iv.set(333) shouldEqual false
      iv() shouldEqual(123)
    }

    "invoke multiple gets" in {
      val h = new IVarHelper
      import h._

      var count = 0
      iv.get { _ => count += 1 }
      iv.get { _ => count += 1 }
      iv.set(123)
      count shouldEqual(2)
      iv.get { _ => count += 1 }
      count shouldEqual(3)
    }

    "chain properly" in {
      val h = new IVarHelper
      import h._

      val order = new ArrayBuffer[Int]
      iv.chained.chained.get { _ => order += 3 }
      iv.chained.get { _ => order += 2 }
      iv.get { _ => order += 1 }
      iv.set(123)
      order shouldEqual Seq(1, 2, 3)
    }

    "defer recursive gets (run a schedule)" in {
      val h = new IVarHelper
      import h._

      var order = new ArrayBuffer[Int]
      def get(n: Int) {
        iv.get { _ =>
          if (n > 0) get(n - 1)
          order += n
        }
      }
      get(10)
      iv.set(123)
      order.toSeq shouldEqual(10 to 0 by -1 toSeq)
    }

    "remove waiters on unget" in {
      val h = new IVarHelper
      import h._

      var didrun = false
      val k = { _: Int => didrun = true }
      iv.get(k)
      iv.unget(k)
      iv.set(1)
      didrun shouldEqual false
    }
    
    "not remove another waiter on unget" in {
      val h = new IVarHelper
      import h._

      var ran = false
      iv.get { _: Int => ran = true }
      iv.unget({_: Int => ()})
      iv.set(1)
      ran shouldEqual true
    }

    "merge" should {
      class MergeHelper {
        val a, b, c = new IVar[Int]
        val events = new ArrayBuffer[String]
      }
      "merges waiters" should {
        val h = new MergeHelper
        import h._

        val a = new IVar[Int]
        b.get { v => events += "b(%d)".format(v) }
        a.get { v => events += "a(%d)".format(v) }
        val expected = Seq("a(1)", "b(1)")
        a.merge(b)
        def test(x: IVar[Int], y: IVar[Int]) {
          x.set(1)
          events shouldEqual expected
          x.isDefined shouldEqual true
          y.isDefined shouldEqual true

        }
        "a <- b" in test(a, b)
        "b <- a" in test(b, a)
      }

      "works transitively" in {
        val h = new MergeHelper
        import h._

        val a = new IVar[Int]
        val c = new IVar[Int]
        a.merge(b)
        b.merge(c)

        c.set(1)
        a.isDefined shouldEqual true
        b.isDefined shouldEqual true
        a() shouldEqual(1)
        b() shouldEqual(1)
      }

      "inherits an already defined value" in {
        val h = new MergeHelper
        import h._

        val a = new IVar[Int]
        a.set(1)
        b.merge(a)
        b.isDefined shouldEqual true
        b() shouldEqual(1)
      }

      "does not fail if already defined" in {
        val h = new MergeHelper
        import h._

        val a = new IVar[Int]
        a.set(1)
        a.merge(b)
        b.isDefined shouldEqual true
        b() shouldEqual(1)
      }

      "twoway merges" should {
        "succeed when values are equal" in {
          val h = new MergeHelper
          import h._

          val a = new IVar[Int]
          a.set(1)
          b.set(1)
          a.merge(b)
        }

        "succeed when values aren't equal, retaining values (it's a noop)" in {
          val h = new MergeHelper
          import h._

          val a = new IVar[Int]
          a.set(1)
          b.set(2)
          intercept[IllegalArgumentException] {
            a.merge(b)
          }
          a.poll shouldEqual Some(1)
          b.poll shouldEqual Some(2)
        }
      }

      "is idempotent" in {
        val h = new MergeHelper
        import h._

        val a = new IVar[Int]
        a.merge(b)
        a.merge(b)
        a.merge(b)
        b.set(123)
        a.isDefined shouldEqual true
      }

      "performs path compression" in {
        var first = new IVar[Int]
        var i = new IVar[Int]
        i.set(1)
        first.merge(i)

        for (_ <- 0 until 100)
          (new IVar[Int]).merge(i)

        first.depth shouldEqual(0)
        i.depth shouldEqual(0)
      }

      "cycles" should {
        "deals with cycles in the done state" in {
          val a = new IVar[Int]
          a.set(1)
          a.isDefined shouldEqual true
          a.merge(a)
          a() shouldEqual(1)
        }

        "deals with shallow cycles in the waiting state" in {
          val a = new IVar[Int]
          a.merge(a)
          a.set(1)
          a.isDefined shouldEqual true
          a() shouldEqual(1)
        }

        "deals with simple indirect cycles" in {
          val h = new MergeHelper
          import h._

          val a = new IVar[Int]
          a.merge(b)
          b.merge(c)
          c.merge(a)
          b.set(1)
          a.isDefined shouldEqual true
          b.isDefined shouldEqual true
          c.isDefined shouldEqual true
          a() shouldEqual(1)
          b() shouldEqual(1)
          c() shouldEqual(1)
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
            b.isDefined shouldEqual false
            b()
            didit = true
          }

          a.set(1)
        }
      }
      t.start()
      t.join(500/*ms*/)
      didit shouldEqual true
    }
  }
}
