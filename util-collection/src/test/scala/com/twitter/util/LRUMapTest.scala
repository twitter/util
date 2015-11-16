package com.twitter.util

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks

@RunWith(classOf[JUnitRunner])
class LRUMapTest extends FunSuite with GeneratorDrivenPropertyChecks {

  // don't waste too much time testing this and keep things small
  implicit override val generatorDrivenConfig =
    PropertyCheckConfig(minSuccessful=5, minSize=2, maxSize=10)

  test("LRUMap creation") {
    forAll (Gen.choose(1, 200)) { size =>
      val lru = new LruMap[String, String](size)
      assert(lru.maxSize == size)

      val slru = new SynchronizedLruMap[String, String](size)
      assert(slru.maxSize == size)
    }
  }

  test("LRUMap insertion") {
    forAll (LRUEntriesGenerator[Int]) { entries =>
      val lru = new LruMap[String, Int](entries.size+10) // headroom
      for (entry <- entries) {
        lru += entry
      }

      for ((key,value) <- entries) {
        assert(lru.get(key) == Some(value))
      }
    }
  }

  test("LRUMap eviction") {
    forAll (LRUEntriesGenerator[Double]) { entries =>
      val slru = new SynchronizedLruMap[String, Double](5)
      for (entry <- entries) {
        slru += entry
      }

      val expectedKeys = entries.slice(entries.size-5, entries.size).map(_._1)
      assert(slru.keySet == expectedKeys.toSet)
    }
  }
}
