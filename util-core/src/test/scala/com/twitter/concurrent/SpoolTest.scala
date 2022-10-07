package com.twitter.concurrent

import com.twitter.concurrent.Spool.*::
import com.twitter.concurrent.Spool.seqToSpool
import com.twitter.conversions.DurationOps._
import com.twitter.util.Await
import com.twitter.util.Future
import com.twitter.util.Promise
import com.twitter.util.Return
import com.twitter.util.Throw
import java.io.EOFException
import org.scalacheck.Arbitrary
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scala.collection.mutable.ArrayBuffer

class SpoolTest extends AnyFunSuite with ScalaCheckDrivenPropertyChecks {

  def await[A](f: Future[A]): A = Await.result(f, 2.seconds)
  val resolvedSpoolFuture = 1 *:: Future.value(2 *:: Future.value(Spool.empty[Int]))
  val emptySpool = Spool.empty[Int]

  class SimpleDelayedSpoolHelper {
    val p = new Promise[Spool[Int]]
    val p1 = new Promise[Spool[Int]]
    val p2 = new Promise[Spool[Int]]
    val s = 1 *:: p
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

  test("Empty Spool should iterate over all elements") {
    val xs = new ArrayBuffer[Int]
    emptySpool foreach { xs += _ }
    assert(xs.size == 0)
  }

  test("Empty Spool should map") {
    assert((emptySpool map { _ * 2 }) == Spool.empty[Int])
  }

  test("Empty Spool should mapFuture") {
    val mapFuture = emptySpool mapFuture { Future.value(_) }
    assert(mapFuture.poll == Some(Return(emptySpool)))
  }

  test("Empty Spool should deconstruct") {
    assert(emptySpool match {
      case x *:: _ => false
      case _ => true
    })
  }

  test("Empty Spool should append via ++") {
    assert((emptySpool ++ Spool.empty[Int]) == Spool.empty[Int])
    assert((Spool.empty[Int] ++ emptySpool) == Spool.empty[Int])

    val s2 = emptySpool ++ (3 *:: Future.value(4 *:: Future.value(Spool.empty[Int])))
    assert(await(s2.toSeq) == Seq(3, 4))
  }

  test("Empty Spool should append via ++ with Future rhs") {
    assert(await(emptySpool ++ Future(Spool.empty[Int])) == Spool.empty[Int])
    assert(await(Spool.empty[Int] ++ Future(emptySpool)) == Spool.empty[Int])

    val s2 = emptySpool ++ Future(3 *:: Future.value(4 *:: Future.value(Spool.empty[Int])))
    assert(await(s2 flatMap (_.toSeq)) == Seq(3, 4))
  }

  test("Empty Spool should flatMap") {
    val f = (x: Int) =>
      Future(x.toString *:: Future.value((x * 2).toString *:: Future.value(Spool.empty[String])))
    assert(await(emptySpool flatMap f) == Spool.empty[Int])
  }

  test("Empty Spool should fold left") {
    val fold = emptySpool.foldLeft(0) { (x, y) => x + y }
    assert(await(fold) == 0)
  }

  test("Empty Spool should reduce left") {
    val fold = emptySpool.reduceLeft { (x, y) => x + y }
    intercept[UnsupportedOperationException] {
      await(fold)
    }
  }

  test("Empty Spool should zip with empty") {
    val result = emptySpool.zip(Spool.empty[Int])
    assert(await(result.toSeq) == Nil)
  }

  test("Empty Spool should zip with non-empty") {
    val result = emptySpool.zip(Seq(1, 2, 3).toSpool)
    assert(await(result.toSeq) == Nil)
  }

  test("Empty Spool should take") {
    assert(emptySpool.take(10) == Spool.empty[Int])
  }

  test("Simple resolved spool should iterate over all elements") {
    val xs = new ArrayBuffer[Int]
    resolvedSpoolFuture foreach { xs += _ }
    assert(xs.toSeq == Seq(1, 2))
  }

  test("Simple resolved spool should buffer to a sequence") {
    assert(await(resolvedSpoolFuture.toSeq) == Seq(1, 2))
  }

  test("Simple resolved spool should map") {
    assert(await(resolvedSpoolFuture.map { _ * 2 }.toSeq) == Seq(2, 4))
  }

  test("Simple resolved spool should mapFuture") {
    val f = resolvedSpoolFuture.mapFuture { Future.value(_) }.flatMap { _.toSeq }.poll
    assert(f == Some(Return(Seq(1, 2))))
  }

  test("Simple resolved spool should deconstruct") {
    assert(resolvedSpoolFuture match {
      case x *:: rest =>
        assert(x == 1)
        await(rest) match {
          case y *:: rest if y == 2 => await(rest).isEmpty
          case _ => fail("expected 3 elements in spool")
        }
      case _ => fail("expected 3 elements in spool")
    })
  }

  test("Simple resolved spool should append via ++") {
    assert(await((resolvedSpoolFuture ++ Spool.empty[Int]).toSeq) == Seq(1, 2))
    assert(await((Spool.empty[Int] ++ resolvedSpoolFuture).toSeq) == Seq(1, 2))

    val s2 = resolvedSpoolFuture ++ (3 *:: Future.value(4 *:: Future.value(Spool.empty[Int])))
    assert(await(s2.toSeq) == Seq(1, 2, 3, 4))
  }

  test("Simple resolved spool should append via ++ with Future rhs") {
    assert(
      await(resolvedSpoolFuture ++ Future.value(Spool.empty[Int]) flatMap (_.toSeq)) == Seq(1, 2))
    assert(
      await(Spool.empty[Int] ++ Future.value(resolvedSpoolFuture) flatMap (_.toSeq)) == Seq(1, 2))

    val s2 =
      resolvedSpoolFuture ++ Future.value(3 *:: Future.value(4 *:: Future.value(Spool.empty[Int])))
    assert(await(s2 flatMap (_.toSeq)) == Seq(1, 2, 3, 4))
  }

  test("Simple resolved spool should flatMap") {
    val f = (x: Int) => Future(Seq(x.toString, (x * 2).toString).toSpool)
    val s2 = resolvedSpoolFuture flatMap f
    assert(await(s2 flatMap (_.toSeq)) == Seq("1", "2", "2", "4"))
  }

  test("Simple resolved spool should fold left") {
    val fold = resolvedSpoolFuture.foldLeft(0) { (x, y) => x + y }
    assert(await(fold) == 3)
  }

  test("Simple resolved spool should reduce left") {
    val fold = resolvedSpoolFuture.reduceLeft { (x, y) => x + y }
    assert(await(fold) == 3)
  }

  test("Simple resolved spool should zip with empty") {
    val zip = resolvedSpoolFuture.zip(Spool.empty[Int])
    assert(await(zip.toSeq) == Nil)
  }

  test("Simple resolved spool should zip with same size spool") {
    val zip = resolvedSpoolFuture.zip(Seq("a", "b").toSpool)
    assert(await(zip.toSeq) == Seq((1, "a"), (2, "b")))
  }

  test("Simple resolved spool should zip with larger spool") {
    val zip = resolvedSpoolFuture.zip(Seq("a", "b", "c", "d").toSpool)
    assert(await(zip.toSeq) == Seq((1, "a"), (2, "b")))
  }

  test("Simple resolved spool should be roundtrippable through toSeq/toSpool") {
    val seq = (0 to 10).toSeq
    assert(await(seq.toSpool.toSeq) == seq)
  }

  test("Simple resolved spool should flatten via flatMap of toSpool") {
    val spool = Seq(Seq(1, 2), Seq(3, 4)).toSpool
    val seq = await(spool.toSeq)

    val flatSpool =
      spool.flatMap { inner => Future.value(inner.toSpool) }

    assert(await(flatSpool.flatMap(_.toSeq)) == seq.flatten)
  }

  test("Simple resolved spool should take") {
    val ls = (1 to 4).toSeq.toSpool
    assert(await(ls.take(2).toSeq) == Seq(1, 2))
    assert(await(ls.take(1).toSeq) == Seq(1))
    assert(await(ls.take(0).toSeq) == Seq.empty)
    assert(await(ls.take(-2).toSeq) == Seq.empty)
  }

  test("Simple resolved spool with EOF iteration on EOFException") {
    val p = new Promise[Spool[Int]](Throw(new EOFException("sad panda")))
    val s = 1 *:: Future.value(2 *:: p)
    val xs = new ArrayBuffer[Option[Int]]
    s foreachElem { xs += _ }
    assert(xs.toSeq == Seq(Some(1), Some(2), None))
  }

  test("Simple resolved spool with error should return with exception on error") {
    val p = new Promise[Spool[Int]](Throw(new Exception("sad panda")))
    val s = 1 *:: Future.value(2 *:: p)
    val xs = new ArrayBuffer[Option[Int]]
    s foreachElem { xs += _ }
    intercept[Exception] {
      await(s.toSeq)
    }
  }

  test("Simple resolved spool with error should return with exception on error in callback") {
    val p = new Promise[Spool[Int]](Throw(new Exception("sad panda")))
    val s = 1 *:: Future.value(2 *:: p)
    val xs = new ArrayBuffer[Option[Int]]
    val f = s foreach { _ => throw new Exception("sad panda") }
    intercept[Exception] {
      await(f)
    }
  }

  test(
    "Simple resolved spool with error should return with exception on EOFException in callback") {
    val p = new Promise[Spool[Int]](Throw(new Exception("sad panda")))
    val s = 1 *:: Future.value(2 *:: p)
    val xs = new ArrayBuffer[Option[Int]]
    val f = s foreach { _ => throw new EOFException("sad panda") }
    intercept[EOFException] {
      await(f)
    }
  }

  test("Simple delayed Spool should iterate as results become available") {
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

  test("Simple delayed Spool should EOF iteration on EOFException") {
    val h = new SimpleDelayedSpoolHelper
    import h._

    val xs = new ArrayBuffer[Option[Int]]
    s foreachElem { xs += _ }
    assert(xs.toSeq == Seq(Some(1)))
    p() = Throw(new EOFException("sad panda"))
    assert(xs.toSeq == Seq(Some(1), None))
  }

  test("Simple delayed Spool should return with exception on error") {
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

  test("Simple delayed Spool should return with exception on error in callback") {
    val h = new SimpleDelayedSpoolHelper
    import h._

    val xs = new ArrayBuffer[Option[Int]]
    val f = s foreach { _ => throw new Exception("sad panda") }
    p() = Return(2 *:: p1)
    intercept[Exception] {
      await(f)
    }
  }

  test("Simple delayed Spool should return with exception on EOFException in callback") {
    val h = new SimpleDelayedSpoolHelper
    import h._

    val xs = new ArrayBuffer[Option[Int]]
    val f = s foreach { _ => throw new EOFException("sad panda") }
    p() = Return(2 *:: p1)
    intercept[EOFException] {
      await(f)
    }
  }

  test("Simple delayed Spool should return a buffered seq when complete") {
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

  test("Simple delayed Spool should deconstruct") {
    val h = new SimpleDelayedSpoolHelper
    import h._

    assert(s match {
      case fst *:: rest if fst == 1 && !rest.isDefined => true
      case _ => false
    })
  }

  test("Simple delayed Spool should collect") {
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

  test("Simple delayed Spool should fold left") {
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

  test("Simple delayed Spool should take while") {
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

  // These next few tests assert that any method called on Spool consumes only the
  // head, and doesn't force the tail. An exception is collect, which consumes
  // up to the first defined value. This is because collect takes a
  // PartialFunction, which if not defined for head, recurses on the tail,
  // forcing it.
  test("Lazily evaluated Spool should be constructed lazily") {
    applyLazily(Future.value _)
  }

  test("Lazily evaluated Spool should collect lazily") {
    applyLazily { spool =>
      spool.collect {
        case x if x % 2 == 0 => x
      }
    }
  }

  test("Lazily evaluated Spool should map lazily") {
    applyLazily { spool => Future.value(spool.map(_ + 1)) }
  }

  test("Lazily evaluated Spool should mapFuture lazily") {
    applyLazily { spool => spool.mapFuture(Future.value(_)) }
  }

  test("Lazily evaluated Spool should flatMap lazily") {
    applyLazily { spool => spool.flatMap { item => Future.value((item to (item + 5)).toSpool) } }
  }

  test("Lazily evaluated Spool should takeWhile lazily") {
    applyLazily { spool =>
      Future.value {
        spool.takeWhile(_ < Int.MaxValue)
      }
    }
  }

  test("Lazily evaluated Spool should take lazily") {
    applyLazily { spool =>
      Future.value {
        spool.take(2)
      }
    }
  }

  test("Lazily evaluated Spool should zip lazily") {
    applyLazily { spool => Future.value(spool.zip(spool).map { case (a, b) => a + b }) }
  }

  test("Lazily evaluated Spool should act eagerly when forced") {
    val (spool, tailReached) =
      applyLazily { spool => Future.value(spool.map(_ + 1)) }
    Await.ready { spool.map(_.force) }
    assert(tailReached.isDefined)
  }

  // Note: ++ is different from the other methods because it doesn't force its
  // argument.
  test("lazy ++") {
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

  test("Spool.merge should merge") {
    forAll(Arbitrary.arbitrary[List[List[Int]]]) { ss =>
      val all = Spool.merge(ss.map(s => Future.value(s.toSpool)))
      val interleaved = ss.flatMap(_.zipWithIndex).sortBy(_._2).map(_._1)
      assert(await(all.flatMap(_.toSeq)) == interleaved)
    }
  }

  test("Spool.merge should merge round robin") {
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

  test("Spool.distinctBy should distinct") {
    forAll { (s: List[Char]) =>
      val d = s.toSpool.distinctBy(x => x).toSeq
      assert(await(d).toSet == s.toSet)
    }
  }

  test("Spool.distinctBy should distinct by in order") {
    val spool: Spool[String] =
      "ac" *:: Future.value("bbe" *:: Future.value("ab" *:: Future.value(Spool.empty[String])))
    assert(await(spool.distinctBy(_.length).toSeq) == Seq("ac", "bbe"))
  }
}
