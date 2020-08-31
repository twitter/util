package com.twitter.inject.internal

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.{HashSet => MutableHashSet}
import scala.language.higherKinds

private[inject] object iterable {

  implicit class RichIterable[Elem, From[Elem] <: Iterable[Elem]](val self: From[Elem])
      extends AnyVal {

    /**
     * Distinct 'iterable' elements using the passed in 'hash' function
     * @param hash Hash function to determine unique elements
     * @return Distinct elements
     */
    def distinctBy[HashCodeType](
      hash: Elem => HashCodeType
    )(
      implicit cbf: CanBuildFrom[From[Elem], Elem, From[Elem]]
    ): From[Elem] = {
      val builder = cbf()
      val seen = MutableHashSet[HashCodeType]()

      for (elem <- self) {
        if (!seen(hash(elem))) {
          seen += hash(elem)
          builder += elem
        }
      }

      builder.result()
    }

  }

}
