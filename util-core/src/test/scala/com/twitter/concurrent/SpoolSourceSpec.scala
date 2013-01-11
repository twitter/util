package com.twitter.concurrent

import org.specs.SpecificationWithJUnit
import com.twitter.util.{Promise, Return, Throw}

class SpoolSourceSpec extends SpecificationWithJUnit {
  "SpoolSource" should {
    val source = new SpoolSource[Int]

    "add values to the spool, ignoring values after close" in {
      val futureSpool = source()
      source.offer(1)
      source.offer(2)
      source.offer(3)
      source.close()
      source.offer(4)
      source.offer(5)
      futureSpool().toSeq() mustEqual Seq(1, 2, 3)
    }

    "return multiple Future Spools that only see values added later" in {
      val futureSpool1 = source()
      source.offer(1)
      val futureSpool2 = source()
      source.offer(2)
      val futureSpool3 = source()
      source.offer(3)
      val futureSpool4 = source()
      source.close()
      futureSpool1().toSeq() mustEqual Seq(1, 2, 3)
      futureSpool2().toSeq() mustEqual Seq(2, 3)
      futureSpool3().toSeq() mustEqual Seq(3)
      futureSpool4().isEmpty must beTrue
    }
  }
}
