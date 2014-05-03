package com.twitter.concurrent

import com.twitter.util.{Await, Future, Promise, Return, Throw}
import java.io.EOFException
import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import scala.collection.mutable.ArrayBuffer
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import Spool.{*::, **::, seqToSpool}

@RunWith(classOf[JUnitRunner])
class SpoolTest extends WordSpec with ShouldMatchers {
  "Empty Spool" should {
    val s = Spool.empty[Int]

    "iterate over all elements" in {
      val xs = new ArrayBuffer[Int]
      s foreach { xs += _ }
      xs.size shouldEqual(0)
    }

    "map" in {
      (s map { _ * 2 } ) shouldEqual(Spool.empty[Int])
    }

    "deconstruct" in {
      assert(s match {
        case x **:: rest => false
        case _ => true
      })
    }

    "append via ++"  in {
      (s ++ Spool.empty[Int]) shouldEqual(Spool.empty[Int])
      (Spool.empty[Int] ++ s) shouldEqual(Spool.empty[Int])

      val s2 = s ++ (3 **:: 4 **:: Spool.empty[Int])
      Await.result(s2.toSeq) shouldEqual(Seq(3, 4))
    }

    "append via ++ with Future rhs"  in {
      Await.result(s ++ Future(Spool.empty[Int])) shouldEqual(Spool.empty[Int])
      Await.result(Spool.empty[Int] ++ Future(s)) shouldEqual(Spool.empty[Int])

      val s2 = s ++ Future(3 **:: 4 **:: Spool.empty[Int])
      Await.result(s2 flatMap (_.toSeq)) shouldEqual(Seq(3, 4))
    }

    "flatMap" in {
      val f = (x: Int) => Future(x.toString **:: (x * 2).toString **:: Spool.empty)
      Await.result(s flatMap f) shouldEqual(Spool.empty[Int])
    }

    "fold left" in {
      val fold = s.foldLeft(0){(x, y) => x + y}
      Await.result(fold) shouldEqual(0)
    }

    "reduce left" in {
      val fold = s.reduceLeft{(x, y) => x + y}
      intercept[UnsupportedOperationException] {
        Await.result(fold)
      }
    }
  }

  "Simple resolved Spool" should {
    val s = 1 **:: 2 **:: Spool.empty

    "iterate over all elements" in {
      val xs = new ArrayBuffer[Int]
      s foreach { xs += _ }
      xs.toSeq shouldEqual(Seq(1,2))
    }

    "buffer to a sequence" in {
      Await.result(s.toSeq) shouldEqual(Seq(1, 2))
    }

    "map" in {
      Await.result(s map { _ * 2 } toSeq) shouldEqual(Seq(2, 4))
    }

    "deconstruct" in {
      assert(s match {
        case x **:: rest =>
          x shouldEqual(1)
          rest match {
            case y **:: rest if y == 2 && rest.isEmpty => true
          }
      })
    }

    "append via ++"  in {
      Await.result((s ++ Spool.empty[Int]).toSeq) shouldEqual(Seq(1, 2))
      Await.result((Spool.empty[Int] ++ s).toSeq) shouldEqual(Seq(1, 2))

      val s2 = s ++ (3 **:: 4 **:: Spool.empty)
      Await.result(s2.toSeq) shouldEqual(Seq(1, 2, 3, 4))
    }

    "append via ++ with Future rhs"  in {
      Await.result(s ++ Future(Spool.empty[Int]) flatMap (_.toSeq)) shouldEqual(Seq(1, 2))
      Await.result(Spool.empty[Int] ++ Future(s) flatMap (_.toSeq)) shouldEqual(Seq(1, 2))

      val s2 = s ++ Future(3 **:: 4 **:: Spool.empty)
      Await.result(s2 flatMap (_.toSeq)) shouldEqual(Seq(1, 2, 3, 4))
    }

    "flatMap" in {
      val f = (x: Int) => Future(x.toString **:: (x * 2).toString **:: Spool.empty)
      val s2 = s flatMap f
      Await.result(s2 flatMap (_.toSeq)) shouldEqual(Seq("1", "2", "2", "4"))
    }

    "fold left" in {
      val fold = s.foldLeft(0){(x, y) => x + y}
      Await.result(fold) shouldEqual(3)
    }

    "reduce left" in {
      val fold = s.reduceLeft{(x, y) => x + y}
      Await.result(fold) shouldEqual(3)
    }

    "be roundtrippable through toSeq/toSpool" in {
      val seq = (0 to 10).toSeq
      Await.result(seq.toSpool.toSeq) shouldEqual(seq)
    }

    "flatten via flatMap of toSpool" in {
      val spool = Seq(1, 2) **:: Seq(3, 4) **:: Spool.empty
      val seq = Await.result(spool.toSeq)

      val flatSpool =
        spool.flatMap { inner =>
          Future.value(inner.toSpool)
        }

      Await.result(flatSpool.flatMap(_.toSeq)) shouldEqual(seq.flatten)
    }
  }

  "Simple resolved spool with EOFException" should {
    val p = new Promise[Spool[Int]](Throw(new EOFException("sad panda")))
    val s = 1 **:: 2 *:: p

    "EOF iteration on EOFException" in {
        val xs = new ArrayBuffer[Option[Int]]
        s foreachElem { xs += _ }
        xs.toSeq shouldEqual(Seq(Some(1), Some(2), None))
    }
  }

  "Simple resolved spool with error" should {
    val p = new Promise[Spool[Int]](Throw(new Exception("sad panda")))
    val s = 1 **:: 2 *:: p

    "return with exception on error" in {
      val xs = new ArrayBuffer[Option[Int]]
      s foreachElem { xs += _ }
      intercept[Exception] {
        Await.result(s.toSeq)
      }
    }

    "return with exception on error in callback" in {
      val xs = new ArrayBuffer[Option[Int]]
      val f = s foreach { _ => throw new Exception("sad panda") }
      intercept[Exception] {
        Await.result(f)
      }
    }

    "return with exception on EOFException in callback" in {
      val xs = new ArrayBuffer[Option[Int]]
      val f = s foreach { _ => throw new EOFException("sad panda") }
      intercept[EOFException] {
        Await.result(f)
      }
    }
  }

  "Simple delayed Spool" should {
    class SimpleDelayedSpoolHelper {
      val p = new Promise[Spool[Int]]
      val p1 = new Promise[Spool[Int]]
      val p2 = new Promise[Spool[Int]]
      val s = 1 *:: p
    }

    "iterate as results become available" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val xs = new ArrayBuffer[Int]
      s foreach { xs += _ }
      xs.toSeq shouldEqual(Seq(1))
      p() = Return(2 *:: p1)
      xs.toSeq shouldEqual(Seq(1, 2))
      p1() = Return(Spool.empty)
      xs.toSeq shouldEqual(Seq(1, 2))
    }

    "EOF iteration on EOFException" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val xs = new ArrayBuffer[Option[Int]]
      s foreachElem { xs += _ }
      xs.toSeq shouldEqual(Seq(Some(1)))
      p() = Throw(new EOFException("sad panda"))
      xs.toSeq shouldEqual(Seq(Some(1), None))
    }

    "return with exception on error" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val xs = new ArrayBuffer[Option[Int]]
      s foreachElem { xs += _ }
      xs.toSeq shouldEqual(Seq(Some(1)))
      p() = Throw(new Exception("sad panda"))
      intercept[Exception] {
        Await.result(s.toSeq)
      }
    }

    "return with exception on error in callback" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val xs = new ArrayBuffer[Option[Int]]
      val f = s foreach { _ => throw new Exception("sad panda") }
      p() = Return(2 *:: p1)
      intercept[Exception] {
        Await.result(f)
      }
    }

    "return with exception on EOFException in callback" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val xs = new ArrayBuffer[Option[Int]]
      val f = s foreach { _ => throw new EOFException("sad panda") }
      p() = Return(2 *:: p1)
      intercept[EOFException] {
        Await.result(f)
      }
    }

    "return a buffered seq when complete" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val f = s.toSeq
      f.isDefined shouldEqual false
      p() = Return(2 *:: p1)
      f.isDefined shouldEqual false
      p1() = Return(Spool.empty)
      f.isDefined shouldEqual true
      Await.result(f) shouldEqual(Seq(1,2))
    }

    "deconstruct" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      assert(s match {
        case fst *:: rest if fst == 1 && !rest.isDefined => true
        case _ => false
      })
    }

    "collect" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val f = s collect {
        case x if x % 2 == 0 => x * 2
      }

      f.isDefined shouldEqual false  // 1 != 2 mod 0
      p() = Return(2 *:: p1)
      f.isDefined shouldEqual true
      val s1 = Await.result(f)
      assert(s1 match {
        case x *:: rest if x == 4 && !rest.isDefined => true
        case _ => false
      })
      p1() = Return(3 *:: p2)
      assert(s1 match {
        case x *:: rest if x == 4 && !rest.isDefined => true
        case _ => false
      })
      p2() = Return(4 **:: Spool.empty)
      val s1s = s1.toSeq
      s1s.isDefined shouldEqual true
      Await.result(s1s) shouldEqual(Seq(4, 8))
    }

    "fold left" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val f = s.foldLeft(0){(x, y) => x + y}

      f.isDefined shouldEqual false
      p() = Return(2 *:: p1)
      f.isDefined shouldEqual false
      p1() = Return(Spool.empty)
      f.isDefined shouldEqual true
      Await.result(f) shouldEqual(3)
    }

    "be lazy" in {
      def mkSpool(i: Int = 0): Future[Spool[Int]] =
        Future.value {
          if (i < 3)
            i *:: mkSpool(i + 1)
          else
            throw new AssertionError("Should not have produced " + i)
        }
      mkSpool()
    }
  }
}
