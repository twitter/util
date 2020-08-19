package com.twitter.concurrent

import com.twitter.concurrent.Spool.{*::, seqToSpool}
import com.twitter.conversions.DurationOps._
import com.twitter.util.{Await, Future, Promise, Return, Throw}
import java.io.EOFException
import org.scalacheck.Arbitrary
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scala.collection.mutable.ArrayBuffer
import org.scalatest.wordspec.AnyWordSpec

class SpoolTest extends AnyWordSpec with ScalaCheckDrivenPropertyChecks {

  def await[A](f: Future[A]): A = Await.result(f, 2.seconds)

  "Empty Spool" should {
    val s = Spool.empty[Int]

    "iterate over all elements" in {
      val xs = new ArrayBuffer[Int]
      s foreach { xs += _ }
      assert(xs.size == 0)
    }

    "map" in {
      assert((s map { _ * 2 }) == Spool.empty[Int])
    }

    "mapFuture" in {
      val mapFuture = s mapFuture { Future.value(_) }
      assert(mapFuture.poll == Some(Return(s)))
    }

    "deconstruct" in {
      assert(s match {
        case x *:: _ => false
        case _ => true
      })
    }

    "append via ++" in {
      assert((s ++ Spool.empty[Int]) == Spool.empty[Int])
      assert((Spool.empty[Int] ++ s) == Spool.empty[Int])

      val s2 = s ++ (3 *:: Future.value(4 *:: Future.value(Spool.empty[Int])))
      assert(await(s2.toSeq) == Seq(3, 4))
    }

    "append via ++ with Future rhs" in {
      assert(await(s ++ Future(Spool.empty[Int])) == Spool.empty[Int])
      assert(await(Spool.empty[Int] ++ Future(s)) == Spool.empty[Int])

      val s2 = s ++ Future(3 *:: Future.value(4 *:: Future.value(Spool.empty[Int])))
      assert(await(s2 flatMap (_.toSeq)) == Seq(3, 4))
    }

    "flatMap" in {
      val f = (x: Int) =>
        Future(x.toString *:: Future.value((x * 2).toString *:: Future.value(Spool.empty[String])))
      assert(await(s flatMap f) == Spool.empty[Int])
    }

    "fold left" in {
      val fold = s.foldLeft(0) { (x, y) => x + y }
      assert(await(fold) == 0)
    }

    "reduce left" in {
      val fold = s.reduceLeft { (x, y) => x + y }
      intercept[UnsupportedOperationException] {
        await(fold)
      }
    }

    "zip with empty" in {
      val result = s.zip(Spool.empty[Int])
      assert(await(result.toSeq) == Nil)
    }

    "zip with non-empty" in {
      val result = s.zip(Seq(1, 2, 3).toSpool)
      assert(await(result.toSeq) == Nil)
    }

    "take" in {
      assert(s.take(10) == Spool.empty[Int])
    }
  }

  "Simple resolved Spool" should {
    val s = 1 *:: Future.value(2 *:: Future.value(Spool.empty[Int]))

    "iterate over all elements" in {
      val xs = new ArrayBuffer[Int]
      s foreach { xs += _ }
      assert(xs.toSeq == Seq(1, 2))
    }

    "buffer to a sequence" in {
      assert(await(s.toSeq) == Seq(1, 2))
    }

    "map" in {
      assert(await(s.map { _ * 2 }.toSeq) == Seq(2, 4))
    }

    "mapFuture" in {
      val f = s.mapFuture { Future.value(_) }.flatMap { _.toSeq }.poll
      assert(f == Some(Return(Seq(1, 2))))
    }

    "deconstruct" in {
      assert(s match {
        case x *:: rest =>
          assert(x == 1)
          await(rest) match {
            case y *:: rest if y == 2 => await(rest).isEmpty
          }
      })
    }

    "append via ++" in {
      assert(await((s ++ Spool.empty[Int]).toSeq) == Seq(1, 2))
      assert(await((Spool.empty[Int] ++ s).toSeq) == Seq(1, 2))

      val s2 = s ++ (3 *:: Future.value(4 *:: Future.value(Spool.empty[Int])))
      assert(await(s2.toSeq) == Seq(1, 2, 3, 4))
    }

    "append via ++ with Future rhs" in {
      assert(await(s ++ Future.value(Spool.empty[Int]) flatMap (_.toSeq)) == Seq(1, 2))
      assert(await(Spool.empty[Int] ++ Future.value(s) flatMap (_.toSeq)) == Seq(1, 2))

      val s2 = s ++ Future.value(3 *:: Future.value(4 *:: Future.value(Spool.empty[Int])))
      assert(await(s2 flatMap (_.toSeq)) == Seq(1, 2, 3, 4))
    }

    "flatMap" in {
      val f = (x: Int) => Future(Seq(x.toString, (x * 2).toString).toSpool)
      val s2 = s flatMap f
      assert(await(s2 flatMap (_.toSeq)) == Seq("1", "2", "2", "4"))
    }

    "fold left" in {
      val fold = s.foldLeft(0) { (x, y) => x + y }
      assert(await(fold) == 3)
    }

    "reduce left" in {
      val fold = s.reduceLeft { (x, y) => x + y }
      assert(await(fold) == 3)
    }

    "zip with empty" in {
      val zip = s.zip(Spool.empty[Int])
      assert(await(zip.toSeq) == Nil)
    }

    "zip with same size spool" in {
      val zip = s.zip(Seq("a", "b").toSpool)
      assert(await(zip.toSeq) == Seq((1, "a"), (2, "b")))
    }

    "zip with larger spool" in {
      val zip = s.zip(Seq("a", "b", "c", "d").toSpool)
      assert(await(zip.toSeq) == Seq((1, "a"), (2, "b")))
    }

    "be roundtrippable through toSeq/toSpool" in {
      val seq = (0 to 10).toSeq
      assert(await(seq.toSpool.toSeq) == seq)
    }

    "flatten via flatMap of toSpool" in {
      val spool = Seq(Seq(1, 2), Seq(3, 4)).toSpool
      val seq = await(spool.toSeq)

      val flatSpool =
        spool.flatMap { inner => Future.value(inner.toSpool) }

      assert(await(flatSpool.flatMap(_.toSeq)) == seq.flatten)
    }

    "take" in {
      val ls = (1 to 4).toSeq.toSpool
      assert(await(ls.take(2).toSeq) == Seq(1, 2))
      assert(await(ls.take(1).toSeq) == Seq(1))
      assert(await(ls.take(0).toSeq) == Seq.empty)
      assert(await(ls.take(-2).toSeq) == Seq.empty)
    }
  }

  "Simple resolved spool with EOFException" should {
    val p = new Promise[Spool[Int]](Throw(new EOFException("sad panda")))
    val s = 1 *:: Future.value(2 *:: p)

    "EOF iteration on EOFException" in {
      val xs = new ArrayBuffer[Option[Int]]
      s foreachElem { xs += _ }
      assert(xs.toSeq == Seq(Some(1), Some(2), None))
    }
  }

  "Simple resolved spool with error" should {
    val p = new Promise[Spool[Int]](Throw(new Exception("sad panda")))
    val s = 1 *:: Future.value(2 *:: p)

    "return with exception on error" in {
      val xs = new ArrayBuffer[Option[Int]]
      s foreachElem { xs += _ }
      intercept[Exception] {
        await(s.toSeq)
      }
    }

    "return with exception on error in callback" in {
      val xs = new ArrayBuffer[Option[Int]]
      val f = s foreach { _ => throw new Exception("sad panda") }
      intercept[Exception] {
        await(f)
      }
    }

    "return with exception on EOFException in callback" in {
      val xs = new ArrayBuffer[Option[Int]]
      val f = s foreach { _ => throw new EOFException("sad panda") }
      intercept[EOFException] {
        await(f)
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
      assert(xs.toSeq == Seq(1))
      p() = Return(2 *:: p1)
      assert(xs.toSeq == Seq(1, 2))
      p1() = Return(Spool.empty)
      assert(xs.toSeq == Seq(1, 2))
    }

    "EOF iteration on EOFException" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val xs = new ArrayBuffer[Option[Int]]
      s foreachElem { xs += _ }
      assert(xs.toSeq == Seq(Some(1)))
      p() = Throw(new EOFException("sad panda"))
      assert(xs.toSeq == Seq(Some(1), None))
    }

    "return with exception on error" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val xs = new ArrayBuffer[Option[Int]]
      s foreachElem { xs += _ }
      assert(xs.toSeq == Seq(Some(1)))
      p() = Throw(new Exception("sad panda"))
      intercept[Exception] {
        await(s.toSeq)
      }
    }

    "return with exception on error in callback" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val xs = new ArrayBuffer[Option[Int]]
      val f = s foreach { _ => throw new Exception("sad panda") }
      p() = Return(2 *:: p1)
      intercept[Exception] {
        await(f)
      }
    }

    "return with exception on EOFException in callback" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val xs = new ArrayBuffer[Option[Int]]
      val f = s foreach { _ => throw new EOFException("sad panda") }
      p() = Return(2 *:: p1)
      intercept[EOFException] {
        await(f)
      }
    }

    "return a buffered seq when complete" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val f = s.toSeq
      assert(f.isDefined == false)
      p() = Return(2 *:: p1)
      assert(f.isDefined == false)
      p1() = Return(Spool.empty)
      assert(f.isDefined == true)
      assert(await(f) == Seq(1, 2))
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

      assert(f.isDefined == false) // 1 != 2 mod 0
      p() = Return(2 *:: p1)
      assert(f.isDefined == true)
      val s1 = await(f)
      assert(s1 match {
        case x *:: rest if x == 4 && !rest.isDefined => true
        case _ => false
      })
      p1() = Return(3 *:: p2)
      assert(s1 match {
        case x *:: rest if x == 4 && !rest.isDefined => true
        case _ => false
      })
      p2() = Return(4 *:: Future.value(Spool.empty[Int]))
      val s1s = s1.toSeq
      assert(s1s.isDefined == true)
      assert(await(s1s) == Seq(4, 8))
    }

    "fold left" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val f = s.foldLeft(0) { (x, y) => x + y }

      assert(f.isDefined == false)
      p() = Return(2 *:: p1)
      assert(f.isDefined == false)
      p1() = Return(Spool.empty)
      assert(f.isDefined == true)
      assert(await(f) == 3)
    }

    "take while" in {
      val h = new SimpleDelayedSpoolHelper
      import h._

      val taken = s.takeWhile(_ < 3)
      assert(taken.isEmpty == false)
      val f = taken.toSeq

      assert(f.isDefined == false)
      p() = Return(2 *:: p1)
      assert(f.isDefined == false)
      p1() = Return(3 *:: p2)
      // despite the Spool having an unfulfilled tail, the takeWhile is satisfied
      assert(f.isDefined == true)
      assert(await(f) == Seq(1, 2))
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
      applyLazily { spool => Future.value(spool.map(_ + 1)) }
    }

    "mapFuture lazily" in {
      applyLazily { spool => spool.mapFuture(Future.value(_)) }
    }

    "flatMap lazily" in {
      applyLazily { spool => spool.flatMap { item => Future.value((item to (item + 5)).toSpool) } }
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

    "zip lazily" in {
      applyLazily { spool => Future.value(spool.zip(spool).map { case (a, b) => a + b }) }
    }

    "act eagerly when forced" in {
      val (spool, tailReached) =
        applyLazily { spool => Future.value(spool.map(_ + 1)) }
      Await.ready { spool.map(_.force) }
      assert(tailReached.isDefined)
    }

    /**
     * Confirms that the given operation does not consume an entire Spool, and then
     * returns the resulting Spool and tail check for further validation.
     */
    def applyLazily(
      f: Spool[Int] => Future[Spool[Int]]
    ): (Future[Spool[Int]], Future[Spool[Int]]) = {
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

    await(c.flatMap(_.tail).select(b.tail))
    assert(p.isDefined)
  }

  "Spool.merge should merge" in {
    forAll(Arbitrary.arbitrary[List[List[Int]]]) { ss =>
      val all = Spool.merge(ss.map(s => Future.value(s.toSpool)))
      val interleaved = ss.flatMap(_.zipWithIndex).sortBy(_._2).map(_._1)
      assert(await(all.flatMap(_.toSeq)) == interleaved)
    }
  }

  "Spool.merge should merge round robin" in {
    val spools: Seq[Future[Spool[String]]] = Seq(
      "a" *:: Future.value("b" *:: Future.value("c" *:: Future.value(Spool.empty[String]))),
      "1" *:: Future.value("2" *:: Future.value("3" *:: Future.value(Spool.empty[String]))),
      Spool.empty,
      "foo" *:: Future.value("bar" *:: Future.value("baz" *:: Future.value(Spool.empty[String])))
    ).map(Future.value)
    assert(
      await(Spool.merge(spools).flatMap(_.toSeq)) ==
        Seq("a", "1", "foo", "b", "2", "bar", "c", "3", "baz")
    )
  }

  "Spool.distinctBy should distinct" in {
    forAll { (s: List[Char]) =>
      val d = s.toSpool.distinctBy(x => x).toSeq
      assert(await(d).toSet == s.toSet)
    }
  }

  "Spool.distinctBy should distinct by in order" in {
    val spool: Spool[String] =
      "ac" *:: Future.value("bbe" *:: Future.value("ab" *:: Future.value(Spool.empty[String])))
    assert(await(spool.distinctBy(_.length).toSeq) == Seq("ac", "bbe"))
  }
}
