package com.twitter.util

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Gen, Arbitrary}

object LRUEntriesGenerator {

  /**
   * Generate a nonempty list of map entries keyed with strings
   * and having any arbitrarily typed values.
   */
  def apply[V: Arbitrary]: Gen[List[(String, V)]] = {
    val gen = for {
      size <- Gen.choose(1, 200)
      uniqueKeys <- Gen.listOfN(size, Gen.identifier).map(_.toSet)
      vals <- Gen.listOfN(uniqueKeys.size, arbitrary[V])
    } yield uniqueKeys.toList.zip(vals)
    gen suchThat (_.nonEmpty)
  }
}
