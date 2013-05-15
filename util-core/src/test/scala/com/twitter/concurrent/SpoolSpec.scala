package com.twitter.concurrent

import scala.collection.mutable.ArrayBuffer
import org.specs.SpecificationWithJUnit
import com.twitter.util.{Await, Future, Promise, Return, Throw}

import Spool.{*::, **::}

class SpoolSpec extends SpecificationWithJUnit {
  "Empty Spool" should {
    val s = Spool.empty[Int]

    "iterate over all elements" in {
      val xs = new ArrayBuffer[Int]
      s foreach { xs += _ }
      xs.size must be_==(0)
    }

    "map" in {
      (s map { _ * 2 } ) must be_==(Spool.empty[Int])
    }

    "deconstruct" in {
      s must beLike {
        case x **:: rest => false
        case _ => true
      }
    }

    "append via ++"  in {
      (s ++ Spool.empty[Int]) must be_==(Spool.empty[Int])
      (Spool.empty[Int] ++ s) must be_==(Spool.empty[Int])

      val s2 = s ++ (3 **:: 4 **:: Spool.empty[Int])
      Await.result(s2.toSeq) must be_==(Seq(3, 4))
    }

    "append via ++ with Future rhs"  in {
      Await.result(s ++ Future(Spool.empty[Int])) must be_==(Spool.empty[Int])
      Await.result(Spool.empty[Int] ++ Future(s)) must be_==(Spool.empty[Int])

      val s2 = s ++ Future(3 **:: 4 **:: Spool.empty[Int])
      Await.result(s2 flatMap (_.toSeq)) must be_==(Seq(3, 4))
    }

    "flatMap" in {
      val f = (x: Int) => Future(x.toString **:: (x * 2).toString **:: Spool.empty)
      Await.result(s flatMap f) must be_==(Spool.empty[Int])
    }

    "fold left" in {
      val fold = s.foldLeft(0){(x, y) => x + y}
      Await.result(fold) must be_==(0)
    }

    "reduce left" in {
      val fold = s.reduceLeft{(x, y) => x + y}
      Await.result(fold) must throwAn[UnsupportedOperationException]
    }

  }

  "Simple resolved Spool" should {
    val s = 1 **:: 2 **:: Spool.empty

    "iterate over all elements" in {
      val xs = new ArrayBuffer[Int]
      s foreach { xs += _ }
      xs.toSeq must be_==(Seq(1,2))
    }

    "buffer to a sequence" in {
      Await.result(s.toSeq) must be_==(Seq(1, 2))
    }

    "map" in {
      Await.result(s map { _ * 2 } toSeq) must be_==(Seq(2, 4))
    }

    "deconstruct" in {
      s must beLike {
        case x **:: rest =>
          x must be_==(1)
          rest must beLike {
            case y **:: rest if y == 2 && rest.isEmpty => true
          }
      }
    }

    "append via ++"  in {
      Await.result((s ++ Spool.empty[Int]).toSeq) must be_==(Seq(1, 2))
      Await.result((Spool.empty[Int] ++ s).toSeq) must be_==(Seq(1, 2))

      val s2 = s ++ (3 **:: 4 **:: Spool.empty)
      Await.result(s2.toSeq) must be_==(Seq(1, 2, 3, 4))
    }

    "append via ++ with Future rhs"  in {
      Await.result(s ++ Future(Spool.empty[Int]) flatMap (_.toSeq)) must be_==(Seq(1, 2))
      Await.result(Spool.empty[Int] ++ Future(s) flatMap (_.toSeq)) must be_==(Seq(1, 2))

      val s2 = s ++ Future(3 **:: 4 **:: Spool.empty)
      Await.result(s2 flatMap (_.toSeq)) must be_==(Seq(1, 2, 3, 4))
    }

    "flatMap" in {
      val f = (x: Int) => Future(x.toString **:: (x * 2).toString **:: Spool.empty)
      val s2 = s flatMap f
      Await.result(s2 flatMap (_.toSeq)) must be_==(Seq("1", "2", "2", "4"))
    }

    "fold left" in {
      val fold = s.foldLeft(0){(x, y) => x + y}
      Await.result(fold) must be_==(3)
    }

    "reduce left" in {
      val fold = s.reduceLeft{(x, y) => x + y}
      Await.result(fold) must be_==(3)
    }


  }

  "Simple resolved spool with error" should {
    val p = new Promise[Spool[Int]](Throw(new Exception("sad panda")))
    val s = 1 **:: 2 *:: p

    "EOF iteration on error" in {
        val xs = new ArrayBuffer[Option[Int]]
        s foreachElem { xs += _ }
        xs.toSeq must be_==(Seq(Some(1), Some(2), None))
    }
  }

  "Simple delayed Spool" should {
    val p = new Promise[Spool[Int]]
    val p1 = new Promise[Spool[Int]]
    val p2 = new Promise[Spool[Int]]
    val s = 1 *:: p

    "iterate as results become available" in {
      val xs = new ArrayBuffer[Int]
      s foreach { xs += _ }
      xs.toSeq must be_==(Seq(1))
      p() = Return(2 *:: p1)
      xs.toSeq must be_==(Seq(1, 2))
      p1() = Return(Spool.empty)
      xs.toSeq must be_==(Seq(1, 2))
    }

    "EOF iteration on failure" in {
      val xs = new ArrayBuffer[Option[Int]]
      s foreachElem { xs += _ }
      xs.toSeq must be_==(Seq(Some(1)))
      p() = Throw(new Exception("sad panda"))
      xs.toSeq must be_==(Seq(Some(1), None))
    }

    "return a buffered seq when complete" in {
      val f = s.toSeq
      f.isDefined must beFalse
      p() = Return(2 *:: p1)
      f.isDefined must beFalse
      p1() = Return(Spool.empty)
      f.isDefined must beTrue
      Await.result(f) must be_==(Seq(1,2))
    }

    "deconstruct" in {
      s must beLike {
        case fst *:: rest if fst == 1 && !rest.isDefined => true
      }
    }

    "collect" in {
      val f = s collect {
        case x if x % 2 == 0 => x * 2
      }

      f.isDefined must beFalse  // 1 != 2 mod 0
      p() = Return(2 *:: p1)
      f.isDefined must beTrue
      val s1 = Await.result(f)
      s1 must beLike {
        case x *:: rest if x == 4 && !rest.isDefined => true
      }
      p1() = Return(3 *:: p2)
      s1 must beLike {
        case x *:: rest if x == 4 && !rest.isDefined => true
      }
      p2() = Return(4 **:: Spool.empty)
      val s1s = s1.toSeq
      s1s.isDefined must beTrue
      Await.result(s1s) must be_==(Seq(4, 8))
    }

    "fold left" in {
      val f = s.foldLeft(0){(x, y) => x + y}

      f.isDefined must beFalse
      p() = Return(2 *:: p1)
      f.isDefined must beFalse
      p1() = Return(Spool.empty)
      f.isDefined must beTrue
      Await.result(f) must be_==(3)

    }

  }
}
