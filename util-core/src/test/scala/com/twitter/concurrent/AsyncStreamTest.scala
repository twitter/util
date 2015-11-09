package com.twitter.concurrent

import com.twitter.conversions.time._
import com.twitter.util.{Await, Future, Promise, Throw, Try}
import org.junit.runner.RunWith
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.GeneratorDrivenPropertyChecks

@RunWith(classOf[JUnitRunner])
class AsyncStreamTest extends FunSuite with GeneratorDrivenPropertyChecks {
  import AsyncStream.{mk, of}
  import AsyncStreamTest._

  test("strict head") {
    intercept[Exception] { (undefined: Unit) +:: AsyncStream.empty }
    intercept[Exception] { mk(undefined, AsyncStream.empty) }
    intercept[Exception] { of(undefined) }
  }

  test("lazy tail") {
    var forced = false
    val s = () +:: { forced = true; AsyncStream.empty[Unit] }
    assert(await(s.head) == Some(()))
    assert(!forced)
    await(s.tail)
    assert(forced)

    var forced1 = false
    val t = mk((), { forced1 = true; AsyncStream.empty[Unit] })
    assert(await(t.head) == Some(()))
    assert(!forced1)
    await(t.tail)
    assert(forced1)
  }

  test("call-by-name tail evaluated at most once") {
    var p = new Promise[Unit]
    val s = () +:: {
      if (p.setDone()) of(())
      else AsyncStream.empty[Unit]
    }
    assert(toSeq(s) == toSeq(s))
  }

  test("ops that force tail evaluation") {
    def isForced(f: AsyncStream[_] => Future[_]): Unit = {
      var forced = false
      Await.ready(f(() +:: { forced = true; AsyncStream.empty }))
      assert(forced)
    }

    isForced(_.foldLeft(0)((_, _) => 0))
    isForced(_.foldLeftF(0)((_, _) => Future.value(0)))
    isForced(_.tail)
  }

  test("observe: failure") {
    val s = 1 +:: 2 +:: (undefined: AsyncStream[Int])
    val (x +: y +: Nil, exc) = await(s.observe())

    assert(x == 1)
    assert(y == 2)
    assert(exc.isDefined)
  }

  test("observe: no failure") {
    val s = 1 +:: 2 +:: AsyncStream.empty[Int]
    val (x +: y +: Nil, exc) = await(s.observe())

    assert(x == 1)
    assert(y == 2)
    assert(exc.isEmpty)
  }

  test("fromSeq works on infinite streams") {
    def ones: Stream[Int] = 1 #:: ones
    assert(toSeq(fromSeq(ones).take(3)) == Seq(1, 1, 1))
  }

  test("foreach") {
    val x = new Promise[Unit]
    val y = new Promise[Unit]

    def f() = { x.setDone(); () }
    def g() = { y.setDone(); () }

    val s = () +:: f() +:: g() +:: AsyncStream.empty[Unit]
    assert(!x.isDefined)
    assert(!y.isDefined)

    s.foreach(_ => ())
    assert(x.isDefined)
    assert(y.isDefined)
  }

  test("lazy ops") {
    val p = new Promise[Unit]
    val s = () +:: {
      p.setDone()
      undefined: AsyncStream[Unit]
    }

    s.map(x => 0)
    assert(!p.isDefined)

    s.mapF(x => Future.True)
    assert(!p.isDefined)

    s.flatMap(x => of(x))
    assert(!p.isDefined)

    s.filter(_ => true)
    assert(!p.isDefined)

    s.withFilter(_ => true)
    assert(!p.isDefined)

    s.take(Int.MaxValue)
    assert(!p.isDefined)

    assert(await(s.take(1).toSeq) == Seq(()))
    assert(!p.isDefined)

    s.takeWhile(_ => true)
    assert(!p.isDefined)

    s.uncons
    assert(!p.isDefined)

    s.foldRight(Future.Done) { (_, _) => Future.Done }
    assert(!p.isDefined)

    s.scanLeft(Future.Done) { (_, _) => Future.Done }
    assert(!p.isDefined)

    s ++ s
    assert(!p.isDefined)

    assert(await(s.head) == Some(()))
    assert(!p.isDefined)

    intercept[Exception] { await(s.tail).isEmpty }
    assert(p.isDefined)
  }

  // Note: We could use ScalaCheck's Arbitrary[Function1] for some of the tests
  // below, however ScalaCheck generates only constant functions which return
  // the same value for any input. This makes it quite useless to us. We'll take
  // another look since https://github.com/rickynils/scalacheck/issues/136 might
  // have solved this issue.

  test("map") {
    forAll { (s: List[Int]) =>
      def f(n: Int) = n.toString
      assert(toSeq(fromSeq(s).map(f)) == s.map(f))
    }
  }

  test("mapF") {
    forAll { (s: List[Int]) =>
      def f(n: Int) = n.toString
      val g = f _ andThen Future.value
      assert(toSeq(fromSeq(s).mapF(g)) == s.map(f))
    }
  }

  test("flatMap") {
    forAll { (s: List[Int]) =>
      def f(n: Int) = n.toString
      def g(a: Int): AsyncStream[String] = of(f(a))
      def h(a: Int): List[String] = List(f(a))
      assert(toSeq(fromSeq(s).flatMap(g)) == s.flatMap(h))
    }
  }

  test("filter") {
    forAll { (s: List[Int]) =>
      def f(n: Int) = n % 3 == 0
      assert(toSeq(fromSeq(s).filter(f)) == s.filter(f))
    }
  }

  test("++") {
    forAll { (a: List[Int], b: List[Int]) =>
      assert(toSeq((fromSeq(a) ++ fromSeq(b))) == a ++ b)
    }
  }

  test("foldRight") {
    forAll { (a: List[Int]) =>
      def f(n: Int, s: String) = (s.toLong + n).toString
      def g(q: Int, p: => Future[String]): Future[String] = p.map(f(q, _))
      val m = fromSeq(a).foldRight(Future.value("0"))(g)
      assert(await(m) == a.foldRight("0")(f))
    }
  }

  test("scanLeft") {
    forAll { (a: List[Int]) =>
      def f(s: String, n: Int) = (s.toLong + n).toString
      assert(toSeq(fromSeq(a).scanLeft("0")(f)) == a.scanLeft("0")(f))
    }
  }

  test("scanLeft is eager") {
    val never = AsyncStream.fromFuture(Future.never)
    val hd = never.scanLeft("hi")((_,_) => ???).head
    assert(hd.isDefined)
    assert(await(hd) == Some("hi"))
  }

  test("foldLeft") {
    forAll { (a: List[Int]) =>
      def f(s: String, n: Int) = (s.toLong + n).toString
      assert(await(fromSeq(a).foldLeft("0")(f)) == a.foldLeft("0")(f))
    }
  }

  test("foldLeftF") {
    forAll { (a: List[Int]) =>
      def f(s: String, n: Int) = (s.toLong + n).toString
      val g: (String, Int) => Future[String] = (q, p) => Future.value(f(q, p))
      assert(await(fromSeq(a).foldLeftF("0")(g)) == a.foldLeft("0")(f))
    }
  }

  test("flatten") {
    val small = Gen.resize(10, Arbitrary.arbitrary[List[List[Int]]])
    forAll(small) { s =>
      assert(toSeq(fromSeq(s.map(fromSeq)).flatten) == s.flatten)
    }
  }

  test("head") {
    forAll { (a: List[Int]) =>
      assert(await(fromSeq(a).head) == a.headOption)
    }
  }

  test("isEmpty") {
    val s = AsyncStream.of(1)
    val tail = await(s.tail)
    assert(tail == None)
  }

  test("tail") {
    forAll(Gen.nonEmptyListOf(Arbitrary.arbitrary[Int])) { (a: List[Int]) =>
      val tail = await(fromSeq(a).tail)
      a.tail match {
        case Nil => assert(tail == None)
        case _ => assert(toSeq(tail.get) == a.tail)
      }
    }
  }

  test("uncons") {
    assert(await(AsyncStream.empty.uncons) == None)
    forAll(Gen.nonEmptyListOf(Arbitrary.arbitrary[Int])) { (a: List[Int]) =>
      val Some((h, t)) = await(fromSeq(a).uncons)
      assert(h == a.head)
      assert(toSeq(t()) == a.tail)
    }
  }

  test("take") {
    forAll(genListAndN) { case (as, n) =>
      assert(toSeq(fromSeq(as).take(n)) == as.take(n))
    }
  }

  test("drop") {
    forAll(genListAndN) { case (as, n) =>
      assert(toSeq(fromSeq(as).drop(n)) == as.drop(n))
    }
  }

  test("takeWhile") {
    forAll { (as: List[Int], p: Int => Boolean) =>
      assert(toSeq(fromSeq(as).takeWhile(p)) == as.takeWhile(p))
    }
  }

  test("dropWhile") {
    forAll { (as: List[Int], p: Int => Boolean) =>
      assert(toSeq(fromSeq(as).dropWhile(p)) == as.dropWhile(p))
    }
  }

  test("toSeq") {
    forAll { (as: List[Int]) =>
      assert(await(fromSeq(as).toSeq()) == as)
    }
  }

  test("identity") {
    val small = Gen.resize(10, Arbitrary.arbitrary[List[Int]])
    forAll(small) { s =>
      val a = fromSeq(s)
      def f(x: Int) = x +:: a

      assert(toSeq(of(1).flatMap(f)) == toSeq(f(1)))
      assert(toSeq(a.flatMap(of)) == toSeq(a))
    }
  }

  test("associativity") {
    val small = Gen.resize(10, Arbitrary.arbitrary[List[Int]])
    forAll(small, small, small) { (s, t, u) =>
      val a = fromSeq(s)
      val b = fromSeq(t)
      val c = fromSeq(u)

      def f(x: Int) = x +:: b
      def g(x: Int) = x +:: c

      val v = a.flatMap(f).flatMap(g)
      val w = a.flatMap(x => f(x).flatMap(g))
      assert(toSeq(v) == toSeq(w))
    }
  }

  test("buffer() works like Seq.splitAt") {
    forAll { (items: List[Char], bufferSize: Int) =>
      val (expectedBuffer, expectedRest) = items.splitAt(bufferSize)
      val (buffer, rest) = await(fromSeq(items).buffer(bufferSize))
      assert(expectedBuffer == buffer)
      assert(expectedRest == await(rest().toSeq))
    }
  }

  test("buffer() has the same properties as take() and drop()") {
    // We need items to be non-empty, because AsyncStream.empty ++
    // <something> forces the future to be created.
    forAll(Gen.nonEmptyListOf(Arbitrary.arbitrary[Char])) { items =>
      forAll { n: Int =>
        var forced1 = false
        val stream1 = fromSeq(items) ++ { forced1 = true; AsyncStream.empty }
        var forced2 = false
        val stream2 = fromSeq(items) ++ { forced2 = true; AsyncStream.empty }

        val takeResult = await(stream2.take(n).toSeq)
        val (bufferResult, bufferRest) = await(stream1.buffer(n))
        assert(takeResult == bufferResult)

        // Strictness property: we should only need to force the full
        // stream if we asked for more items that were present in the
        // stream.
        assert(forced1 == (n > items.size))
        assert(forced1 == forced2)
        val wasForced = forced1

        // Strictness property: Since AsyncStream contains a Future
        // rather than a thunk, we need to evaluate the next element in
        // order to get the result of drop and the rest of the stream
        // after buffering.
        val bufferTail = bufferRest()
        val dropTail = stream2.drop(n)
        assert(forced1 == (n >= items.size))
        assert(forced1 == forced2)

        // This is the only case that should have caused the item to be forced.
        assert((wasForced == forced1) || n == items.size)

        // Forcing the rest of the sequence should always cause evaluation.
        assert(await(bufferTail.toSeq) == await(dropTail.toSeq))
        assert(forced1)
        assert(forced2)
      }
    }
  }

  test("grouped() works like Seq.grouped") {
    forAll { (items: Seq[Char], groupSize: Int) =>
      // This is a Try so that we can test that bad inputs act the
      // same. (Zero or negative group sizes throw the same
      // exception.)
      val expected = Try(items.grouped(groupSize).toSeq)
      val actual = Try(await(fromSeq(items).grouped(groupSize).toSeq))

      // If they are both exceptions, then pass if the exceptions are
      // the same type (don't require them to define equality or have
      // the same exception message)
      (actual, expected) match {
        case (Throw(e1), Throw(e2)) => assert(e1.getClass == e2.getClass)
        case _ => assert(actual == expected)
      }
    }
  }

  test("grouped should be lazy") {
    // We need items to be non-empty, because AsyncStream.empty ++
    // <something> forces the future to be created.
    forAll(Gen.nonEmptyListOf(Arbitrary.arbitrary[Char])) { items =>
      // We need to make sure that the chunk size (1) is valid and (2)
      // is short enough that forcing the first group does not force
      // the exception.
      forAll(Gen.chooseNum(1, items.size)) { groupSize =>
        var forced = false
        val stream: AsyncStream[Char] = fromSeq(items) ++ { forced = true; AsyncStream.empty }

        val expected = items.grouped(groupSize).toSeq.headOption
        // This will take up to items.size items from the stream. This
        // does not require forcing the tail.
        val actual = await(stream.grouped(groupSize).head)
        assert(actual == expected)
        assert(!forced)
        val expectedChunks = items.grouped(groupSize).toSeq
        val allChunks = await(stream.grouped(groupSize).toSeq)
        assert(allChunks == expectedChunks)
        assert(forced)
      }
    }
  }
}

private object AsyncStreamTest {
  val genListAndN = for {
    as <- Arbitrary.arbitrary[List[Int]]
    n <- Gen.choose(0, as.length)
  } yield (as, n)

  def await[T](fut: Future[T]) = Await.result(fut, 100.milliseconds)

  def undefined[A]: A = throw new Exception

  def toSeq[A](s: AsyncStream[A]): Seq[A] = await(s.toSeq())

  def fromSeq[A](s: Seq[A]): AsyncStream[A] =
    s match {
      case Nil => AsyncStream.empty
      case a +: Nil => AsyncStream.of(a)
      case a +: b +: Nil => AsyncStream.m(Future.value(a +:: AsyncStream.of(b)))
      case a +: as => a +:: fromSeq(as)
    }
}
