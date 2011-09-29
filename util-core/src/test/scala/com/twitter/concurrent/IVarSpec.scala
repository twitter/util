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
 }
}
