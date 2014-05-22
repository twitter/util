package com.twitter.concurrent

import com.twitter.util.{Await, Future, Promise, Return, Throw}
import java.io.EOFException
import org.scalatest.WordSpec

import scala.collection.mutable.ArrayBuffer
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import Spool.{*::, **::, seqToSpool}

@RunWith(classOf[JUnitRunner])
class SpoolTest extends WordSpec {
  "Empty Spool" should {
    val s = Spool.empty[Int]

    "iterate over all elements" in {
      val xs = new ArrayBuffer[Int]
      s foreach { xs += _ }
      assert(xs.size === 0)
    }

    "map" in {
      assert((s map { _ * 2 } ) === Spool.empty[Int])
    }

    "deconstruct" in {
      assert(s match {
        case x **:: rest => false
        case _ => true
      })
    }

    "append via ++"  in {
      assert((s ++ Spool.empty[Int]) === Spool.empty[Int])
      assert((Spool.empty[Int] ++ s) === Spool.empty[Int])

      val s2 = s ++ (3 **:: 4 **:: Spool.empty[Int])
      assert(Await.result(s2.toSeq) === Seq(3, 4))
    }

    "append via ++ with Future rhs"  in {
      assert(Await.result(s ++ Future(Spool.empty[Int])) === Spool.empty[Int])
      assert(Await.result(Spool.empty[Int] ++ Future(s)) === Spool.empty[Int])

      val s2 = s ++ Future(3 **:: 4 **:: Spool.empty[Int])
      assert(Await.result(s2 flatMap (_.toSeq)) === Seq(3, 4))
    }

    "flatMap" in {
      val f = (x: Int) => Future(x.toString **:: (x * 2).toString **:: Spool.empty)
      assert(Await.result(s flatMap f) === Spool.empty[Int])
    }

    "fold left" in {
      val fold = s.foldLeft(0){(x, y) => x + y}
      assert(Await.result(fold) === 0)
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
      assert(xs.toSeq === Seq(1,2))
    }

    "buffer to a sequence" in {
      assert(Await.result(s.toSeq) === Seq(1, 2))
    }

    "map" in {
      assert(Await.result(s map { _ * 2 } toSeq) === Seq(2, 4))
    }

    "deconstruct" in {
      assert(s match {
        case x **:: rest =>
          assert(x === 1)
          rest match {
            case y **:: rest if y == 2 && rest.isEmpty => true
          }
      })
    }

    "append via ++"  in {
      assert(Await.result((s ++ Spool.empty[Int]).toSeq) === Seq(1, 2))
      assert(Await.result((Spool.empty[Int] ++ s).toSeq) === Seq(1, 2))

      val s2 = s ++ (3 **:: 4 **:: Spool.empty)
      assert(Await.result(s2.toSeq) === Seq(1, 2, 3, 4))
    }

    "append via ++ with Future rhs"  in {
      assert(Await.result(s ++ Future(Spool.empty[Int]) flatMap (_.toSeq)) === Seq(1, 2))
      assert(Await.result(Spool.empty[Int] ++ Future(s) flatMap (_.toSeq)) === Seq(1, 2))

      val s2 = s ++ Future(3 **:: 4 **:: Spool.empty)
      assert(Await.result(s2 flatMap (_.toSeq)) === Seq(1, 2, 3, 4))
    }

    "flatMap" in {
      val f = (x: Int) => Future(x.toString **:: (x * 2).toString **:: Spool.empty)
      val s2 = s flatMap f
      assert(Await.result(s2 flatMap (_.toSeq)) === Seq("1", "2", "2", "4"))
    }

    "fold left" in {
      val fold = s.foldLeft(0){(x, y) => x + y}
      assert(Await.result(fold) === 3)
    }

    "reduce left" in {
      val fold = s.reduceLeft{(x, y) => x + y}
      assert(Await.result(fold) === 3)
    }

    "be roundtrippable through toSeq/toSpool" in {
      val seq = (0 to 10).toSeq
      assert(Await.result(seq.toSpool.toSeq) === seq)
    }

    "flatten via flatMap of toSpool" in {
      val spool = Seq(1, 2) **:: Seq(3, 4) **:: Spool.empty
      val seq = Await.result(spool.toSeq)

      val flatSpool =
        spool.flatMap { inner =>
          Future.value(inner.toSpool)
        }

      assert(Await.result(flatSpool.flatMap(_.toSeq)) === seq.flatten)
    }
  }

  "Simple resolved spool with EOFException" should {
    val p = new Promise[Spool[Int]](Throw(new EOFException("sad panda")))
    val s = 1 **:: 2 *:: p

    "EOF iteration on EOFException" in {
        val xs = new ArrayBuffer[Option[Int]]
        s foreachElem { xs += _ }
        assert(xs.toSeq === Seq(Some(1), Some(2), None))
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
      assert(xs.toSeq === Seq(1))
      p() = Return(2 *:: p1)
      assert(xs.toSeq === Seq(1, 2))
      p1() = Return(Spool.empty)
      assert(xs.toSeq === Seq(1, 2))
    }

    "EOF iteration on EOFException" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val xs = new ArrayBuffer[Option[Int]]
      s foreachElem { xs += _ }
      assert(xs.toSeq === Seq(Some(1)))
      p() = Throw(new EOFException("sad panda"))
      assert(xs.toSeq === Seq(Some(1), None))
    }

    "return with exception on error" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val xs = new ArrayBuffer[Option[Int]]
      s foreachElem { xs += _ }
      assert(xs.toSeq === Seq(Some(1)))
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
      assert(f.isDefined === false)
      p() = Return(2 *:: p1)
      assert(f.isDefined === false)
      p1() = Return(Spool.empty)
      assert(f.isDefined === true)
      assert(Await.result(f) === Seq(1,2))
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

      assert(f.isDefined === false)  // 1 != 2 mod 0
      p() = Return(2 *:: p1)
      assert(f.isDefined === true)
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
      assert(s1s.isDefined === true)
      assert(Await.result(s1s) === Seq(4, 8))
    }

    "fold left" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val f = s.foldLeft(0){(x, y) => x + y}

      assert(f.isDefined === false)
      p() = Return(2 *:: p1)
      assert(f.isDefined === false)
      p1() = Return(Spool.empty)
      assert(f.isDefined === true)
      assert(Await.result(f) === 3)
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
