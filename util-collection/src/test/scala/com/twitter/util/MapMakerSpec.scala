package com.twitter.util

import com.twitter.conversions.time._
import org.specs.Specification

object MapMakerSpec extends Specification {
  "MapMaker" should {
    class Item
    case class Cell[A](var elem: A)
    val id = 1010101L
    val r = Runtime.getRuntime()

    "Compute keys" in {
      val computed = MapMaker[String, String](_.compute { k => "computed"+k })
      computed.get("ok") must beSome("computedok")
      computed.putIfAbsent("ok", "yes")
      computed.get("ok") must beSome("computedok")

      computed.putIfAbsent("notok", "no")
      computed.get("notok") must beSome("no")
    }

    "Store values" in {
      val map = MapMaker[String, String](Function.const(()))
      map.get("ok") must beNone
      map.put("ok", "yes")
      map.get("ok") must beSome("yes")
    }

    "A weak value map removes items when there is no longer a reference to them" in {
      val weakValueMap = MapMaker[Int, Item](_.weakValues)
      val cell = new Cell(new Item)
      weakValueMap += 1 -> cell.elem
      r.gc()
      weakValueMap get(1) must beSome(cell.elem)
      cell.elem = null
      r.gc()
      weakValueMap get(1) must beNone
    }

    "calling contains does not trigger compute function" in {
      val map = MapMaker[Int, Item](_.compute(_ => throw new Exception))
      map.contains(3) must not(throwA[Exception])
    }

    "max size is enforced" in {
      val fixedMap = MapMaker[Int, Int](_.maximumSize(1))
      fixedMap += 1 -> 10
      fixedMap += 2 -> 20

      fixedMap.size mustEqual 1
      fixedMap.get(1) must beNone
      fixedMap.get(2) must beSome(20)
    }

    "EvictionListener is called upon eviction" in {
      val wasEvicted = new CountDownLatch(2)

      def onEviction(m: Int, n: Int) = wasEvicted.countDown()

      val evictionMap = MapMaker[Int, Int](_.maximumSize(1).withEvictionListener(onEviction))

      evictionMap += 1 -> 10
      evictionMap += 2 -> 20
      evictionMap += 3 -> 30

      evictionMap.size mustEqual 1 // 2 elements were evicted

      wasEvicted.await(100.milliseconds)
      wasEvicted.isZero mustBe true
    }
  }
}
