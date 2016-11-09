package com.twitter.util

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalacheck.Gen
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.prop.GeneratorDrivenPropertyChecks

@RunWith(classOf[JUnitRunner])
class ImmutableLRUTest extends FunSuite with GeneratorDrivenPropertyChecks {

  // don't waste too much time testing this and keep things small
  implicit override val generatorDrivenConfig =
    PropertyCheckConfiguration(minSuccessful=5, minSize=2, sizeRange=8)

  test("ImmutableLRU insertion") {
    forAll (Gen.zip(Gen.identifier, arbitrary[Int])) { case (s:String, i:Int) =>
      val lru = ImmutableLRU[String, Int](50)

      // first-time insertion should not evict
      val (key, lru2) = lru + (s -> i)
      assert(key == None)

      // test get method
      val (key2, _) = lru2.get(s)
      assert(key2 == Some(i))
    }
  }

  // given a list of entries, build an LRU containing them
  private def buildLRU[V](
    lru: ImmutableLRU[String,V],
    entries: List[(String,V)]): ImmutableLRU[String,V] = {
    entries match {
      case Nil => lru
      case head :: tail => buildLRU((lru+head)._2, tail)
    }
  }

  test("ImmutableLRU eviction") {
    forAll(LRUEntriesGenerator[Int]) { entries =>
      val lru = buildLRU(ImmutableLRU[String, Int](4), entries)
      assert(lru.keySet == entries.map(_._1).slice(entries.size - 4, entries.size).toSet)
    }
  }

  test("ImmutableLRU removal") {
    val gen = for {
      entries <- LRUEntriesGenerator[Double]
      entry <- Gen.oneOf(entries)
    } yield (entries, entry._1, entry._2)

    forAll(gen) { case (entries, k, v) =>
      val lru = buildLRU(ImmutableLRU[String, Double](entries.size+1), entries)

      // make sure it's there
      assert(lru.get(k)._1 == Some(v))

      // remove once
      val (key, newLru) = lru.remove(k)
      assert(key == Some(v))

      // remove it again
      val (key2, _) = newLru.remove(k)
      assert(key2 == None)

      // still should be able to get it out of the original
      val (key3, _) = lru.remove(k)
      assert(key3 == Some(v))
    }
  }
}
