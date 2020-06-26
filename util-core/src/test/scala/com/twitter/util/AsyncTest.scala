package com.twitter.util

import org.scalatest.WordSpec
import com.twitter.util.Async.{async, await}

class AsyncTest extends WordSpec {

  "async/await" should {
    "work" in {
      val c = async {
        val a = Future.value(10)
        val b = Future.value(5)
        await(a) + await(b)
      }
      assert(c.poll == Some(Return(15)))
    }
  }

}
