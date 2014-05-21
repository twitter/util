package com.twitter.concurrent

import org.specs.SpecificationWithJUnit
import com.twitter.util.{Promise, Return, Throw, Await}
import java.util.concurrent.atomic.AtomicInteger

class SpoolSourceSpec extends SpecificationWithJUnit {
  import Spool.*::

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
      Await.result(futureSpool flatMap (_.toSeq)) mustEqual Seq(1, 2, 3)
      Await.result(source.closed) mustEqual ()
    }

    "add values to the spool, ignoring values after offerAndClose" in {
      val futureSpool = source()
      source.offer(1)
      source.offer(2)
      source.offerAndClose(3)
      source.offer(4)
      source.offer(5)
      Await.result(futureSpool flatMap (_.toSeq)) mustEqual Seq(1, 2, 3)
      Await.result(source.closed) mustEqual ()
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
      Await.result(futureSpool1 flatMap (_.toSeq)) mustEqual Seq(1, 2, 3)
      Await.result(futureSpool2 flatMap (_.toSeq)) mustEqual Seq(2, 3)
      Await.result(futureSpool3 flatMap (_.toSeq)) mustEqual Seq(3)
      Await.result(futureSpool4).isEmpty must beTrue
      Await.result(source.closed) mustEqual ()
    }

    "throw exception and close spool when exception is raised" in {
      val futureSpool1 = source()
      source.offer(1)
      source.raise(new Exception("sad panda"))
      val futureSpool2 = source()
      source.offer(1)
      Await.result(futureSpool1 flatMap (_.toSeq)) must throwA[Exception]
      Await.result(futureSpool2).isEmpty must beTrue
      Await.result(source.closed) must throwA[Exception]
    }

    "invoke registered interrupt handlers" in {
      val fired = new AtomicInteger(0)

      val isource = new SpoolSource[Int]({ case exc =>
        fired.addAndGet(1)
      })

      val a = isource()
      val b = a.flatMap(_.tail)
      val c = b.flatMap(_.tail)
      val d = c.flatMap(_.tail)

      fired.get() mustEqual 0

      a.raise(new Exception("sad panda 0"))
      fired.get() mustEqual 1

      isource.offer(1)
      b.raise(new Exception("sad panda 1"))
      fired.get() mustEqual 2

      isource.offer(2)
      c.raise(new Exception("sad panda 2"))
      fired.get() mustEqual 3

      // ignore exceptions once the source is closed
      isource.offerAndClose(3)
      d.raise(new Exception("sad panda 3"))
      fired.get() mustEqual 3

      Await.result(a flatMap (_.toSeq)) mustEqual Seq(1, 2, 3)
    }
  }
}
