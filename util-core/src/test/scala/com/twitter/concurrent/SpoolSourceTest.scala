package com.twitter.concurrent

import com.twitter.util.{Return, Await}
import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.wordspec.AnyWordSpec

class SpoolSourceTest extends AnyWordSpec {
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
      assert(Await.result(futureSpool flatMap (_.toSeq)) == Seq(1, 2, 3))
    }

    "add values to the spool, ignoring values after offerAndClose" in {
      val h = new SpoolSourceHelper
      import h._

      val futureSpool = source()
      source.offer(1)
      source.offer(2)
      source.offerAndClose(3)
      source.offer(4)
      source.offer(5)
      assert(Await.result(futureSpool flatMap (_.toSeq)) == Seq(1, 2, 3))
      assert(Await.result(source.closed.liftToTry) == Return(()))
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
      assert(Await.result(futureSpool1 flatMap (_.toSeq)) == Seq(1, 2, 3))
      assert(Await.result(futureSpool2 flatMap (_.toSeq)) == Seq(2, 3))
      assert(Await.result(futureSpool3 flatMap (_.toSeq)) == Seq(3))
      assert(Await.result(futureSpool4).isEmpty == true)
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
      assert(Await.result(futureSpool2).isEmpty == true)
    }

    "invoke registered interrupt handlers" in {
      val h = new SpoolSourceHelper

      val fired = new AtomicInteger(0)

      val isource = new SpoolSource[Int]({
        case exc =>
          fired.addAndGet(1)
      })

      val a = isource()
      val b = a.flatMap(_.tail)
      val c = b.flatMap(_.tail)
      val d = c.flatMap(_.tail)

      assert(fired.get() == 0)

      a.raise(new Exception("sad panda 0"))
      assert(fired.get() == 1)

      isource.offer(1)
      b.raise(new Exception("sad panda 1"))
      assert(fired.get() == 2)

      isource.offer(2)
      c.raise(new Exception("sad panda 2"))
      assert(fired.get() == 3)

      // ignore exceptions once the source is closed
      isource.offerAndClose(3)
      d.raise(new Exception("sad panda 3"))
      assert(fired.get() == 3)

      assert(Await.result(a flatMap (_.toSeq)) == Seq(1, 2, 3))
    }
  }
}
