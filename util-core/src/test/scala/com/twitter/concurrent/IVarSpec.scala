package com.twitter.concurrent

import java.util.concurrent.CountDownLatch
import scala.collection.mutable.{ArrayBuffer, SynchronizedBuffer}

import org.specs.Specification

object IVarSpec extends Specification {
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

   "connect" in {
     val a, b = new IVar[Int]
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

     "fails if already defined" in {
       a.set(1)
       a.merge(b) must throwA[AssertionError]
     }

     "twoway merges" in {
       "succeed when values are equal" in {
         a.set(1)
         b.set(1)
         a.merge(b, twoway=true) mustNot throwA[AssertionError]
       }

       "fails when values aren't equal" in {
         a.set(1)
         b.set(2)
         a.merge(b, twoway=true) must throwA[AssertionError]
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
