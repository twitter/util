package com.twitter.concurrent


import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import com.twitter.util.{Promise, Return, Throw, Await}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SpoolSourceTest extends WordSpec with ShouldMatchers {
  "SpoolSource" should {
    class SpoolSourceHelper {
      val source = new SpoolSource[Int]
    }

    "add values to the spool, ignoring values after close" in {
      val h = new SpoolSourceHelper
      import h._

      val futureSpool = source()
      source.offer(1)
      source.offer(2)
      source.offer(3)
      source.close()
      source.offer(4)
      source.offer(5)
      Await.result(futureSpool flatMap (_.toSeq)) shouldEqual Seq(1, 2, 3)
    }

    "return multiple Future Spools that only see values added later" in {
      val h = new SpoolSourceHelper
      import h._

      val futureSpool1 = source()
      source.offer(1)
      val futureSpool2 = source()
      source.offer(2)
      val futureSpool3 = source()
      source.offer(3)
      val futureSpool4 = source()
      source.close()
      Await.result(futureSpool1 flatMap (_.toSeq)) shouldEqual Seq(1, 2, 3)
      Await.result(futureSpool2 flatMap (_.toSeq)) shouldEqual Seq(2, 3)
      Await.result(futureSpool3 flatMap (_.toSeq)) shouldEqual Seq(3)
      Await.result(futureSpool4).isEmpty shouldEqual true
    }

    "throw exception and close spool when exception is raised" in {
      val h = new SpoolSourceHelper
      import h._

      val futureSpool1 = source()
      source.offer(1)
      source.raise(new Exception("sad panda"))
      val futureSpool2 = source()
      source.offer(1)
      intercept[Exception] {
        Await.result(futureSpool1 flatMap (_.toSeq))
      }
      Await.result(futureSpool2).isEmpty shouldEqual true
    }
  }
}
