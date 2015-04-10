package com.twitter.concurrent

import java.io.EOFException

import scala.collection.mutable.ArrayBuffer

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner

import com.twitter.concurrent.Spool.{**::, *::, seqToSpool}
import com.twitter.util.{Await, Future, Promise, Return, Throw}

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

    "mapFuture" in {
      val mapFuture = s mapFuture { Future.value(_) }
      assert(mapFuture.poll === Some(Return(s)))
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

    "take" in {
      assert(s.take(10) === Spool.empty[Int])
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
      assert(Await.result(s.map { _ * 2 }.toSeq) === Seq(2, 4))
    }

    "mapFuture" in {
      val f = s.mapFuture { Future.value(_) }.flatMap { _.toSeq }.poll
      assert(f === Some(Return(Seq(1, 2))))
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

    "take" in {
      val ls = (1 to 4).toSeq.toSpool
      assert(Await.result(ls.take(2).toSeq) === Seq(1,2))
      assert(Await.result(ls.take(1).toSeq) === Seq(1))
      assert(Await.result(ls.take(0).toSeq) === Seq.empty)
      assert(Await.result(ls.take(-2).toSeq) === Seq.empty)
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

    "take while" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val taken = s.takeWhile(_ < 3)
      assert(taken.isEmpty === false)
      val f = taken.toSeq

      assert(f.isDefined === false)
      p() = Return(2 *:: p1)
      assert(f.isDefined === false)
      p1() = Return(3 *:: p2)
      // despite the Spool having an unfulfilled tail, the takeWhile is satisfied
      assert(f.isDefined === true)
      assert(Await.result(f) === Seq(1, 2))
    }
  }

  // These set of tests assert that any method called on Spool consumes only the
  // head, and doesn't force the tail. An exception is collect, which consumes
  // up to the first defined value. This is because collect takes a
  // PartialFunction, which if not defined for head, recurses on the tail,
  // forcing it.
  "Lazily evaluated Spool" should {
    "be constructed lazily" in {
      applyLazily(Future.value _)
    }

    "collect lazily" in {
      applyLazily { spool =>
        spool.collect {
          case x if x % 2 == 0 => x
        }
      }
    }

    "map lazily" in {
      applyLazily { spool =>
        Future.value(spool.map(_ + 1))
      }
    }

    "mapFuture lazily" in {
      applyLazily { spool =>
        spool.mapFuture(Future.value(_))
      }
    }

    "flatMap lazily" in {
      applyLazily { spool =>
        spool.flatMap { item =>
          Future.value((item to (item + 5)).toSpool)
        }
      }
    }

    "takeWhile lazily" in {
      applyLazily { spool =>
        Future.value {
          spool.takeWhile(_ < Int.MaxValue)
        }
      }
    }

    "take lazily" in {
      applyLazily { spool =>
        Future.value {
          spool.take(2)
        }
      }
    }

    "act eagerly when forced" in {
      val (spool, tailReached) =
        applyLazily { spool =>
          Future.value(spool.map(_ + 1))
        }
      Await.ready { spool.map(_.force) }
      assert(tailReached.isDefined)
    }

    /**
     * Confirms that the given operation does not consume an entire Spool, and then
     * returns the resulting Spool and tail check for further validation.
     */
    def applyLazily(f: Spool[Int]=>Future[Spool[Int]]): (Future[Spool[Int]], Future[Spool[Int]]) = {
      val tail = new Promise[Spool[Int]]

      // A spool where only the head is valid.
      def spool: Spool[Int] = 0 *:: { tail.setException(new Exception); tail }

      // create, apply, poll
      val s = f(spool)
      assert(!tail.isDefined)
      (s, tail)
    }
  }

  // Note: ++ is different from the other methods because it doesn't force its
  // argument.
  "lazy ++" in {
    val nil = Future.value(Spool.empty[Unit])
    val a = () *:: nil
    val p = new Promise[Unit]

    def spool = {
      p.setDone()
      () *:: nil
    }

    def f = Future.value(spool)

    assert(!p.isDefined)

    val b = a ++ spool
    assert(!p.isDefined)

    val c = a ++ f
    assert(!p.isDefined)

    Await.result(c.flatMap(_.tail).select(b.tail))
    assert(p.isDefined)
  }
}
