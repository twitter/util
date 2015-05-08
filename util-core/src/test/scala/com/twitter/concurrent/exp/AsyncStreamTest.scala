package com.twitter.concurrent.exp

import com.twitter.util.{Await, Promise, Future}
import org.junit.runner.RunWith
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.GeneratorDrivenPropertyChecks

@RunWith(classOf[JUnitRunner])
class AsyncStreamTest extends FunSuite with GeneratorDrivenPropertyChecks {
  import AsyncStream.{mk, fromSeq, of}
  import AsyncStreamTest._

  test("strict head") {
    intercept[Exception] { (undefined: Unit) +:: AsyncStream.empty }
    intercept[Exception] { mk(undefined, AsyncStream.empty) }
    intercept[Exception] { of(undefined) }
  }

  test("lazy tail") {
    val s = () +:: (undefined: AsyncStream[Unit])
    assert(Await.result(s.head) == Some(()))
    intercept[Exception] { Await.result(s.tail).isEmpty }

    val t = mk((), (undefined: AsyncStream[Unit]))
    assert(Await.result(t.head) == Some(()))
    intercept[Exception] { Await.result(t.tail).isEmpty }
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
    val s = () +:: (undefined: AsyncStream[Unit])

    intercept[Exception] { Await.result(s.foldLeft(0)((_, _) => 0)) }
    intercept[Exception] {
      Await.result(s.foldLeftF(0)((_, _) => Future.value(0)))
    }
    intercept[Exception] { Await.result(s.tail).isEmpty }
  }

  test("observe: failure") {
    val s = 1 +:: 2 +:: (undefined: AsyncStream[Int])
    val (x +: y +: Nil, exc) = Await.result(s.observe())

    assert(x == 1)
    assert(y == 2)
    assert(exc.isDefined)
  }

  test("observe: no failure") {
    val s = 1 +:: 2 +:: AsyncStream.empty[Int]
    val (x +: y +: Nil, exc) = Await.result(s.observe())

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

    def f = { x.setDone(); () }
    def g = { y.setDone(); () }

    val s = () +:: f +:: g +:: AsyncStream.empty[Unit]
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

    s.takeWhile(_ => true)
    assert(!p.isDefined)

    s.uncons
    assert(!p.isDefined)

    s.foldRight(Future.Done) { (_, _) => Future.Done }
    assert(!p.isDefined)

    s ++ s
    assert(!p.isDefined)

    assert(Await.result(s.head) == Some(()))
    assert(!p.isDefined)

    intercept[Exception] { Await.result(s.tail).isEmpty }
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
      assert(Await.result(m) == a.foldRight("0")(f))
    }
  }

  test("foldLeft") {
    forAll { (a: List[Int]) =>
      def f(s: String, n: Int) = (s.toLong + n).toString
      assert(Await.result(fromSeq(a).foldLeft("0")(f)) == a.foldLeft("0")(f))
    }
  }

  test("foldLeftF") {
    forAll { (a: List[Int]) =>
      def f(s: String, n: Int) = (s.toLong + n).toString
      val g: (String, Int) => Future[String] = (q, p) => Future.value(f(q, p))
      assert(Await.result(fromSeq(a).foldLeftF("0")(g)) == a.foldLeft("0")(f))
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
      assert(Await.result(fromSeq(a).head) == a.headOption)
    }
  }

  test("isEmpty") {
    val s = AsyncStream.of(1)
    val tail = Await.result(s.tail)
    assert(tail == None)
  }

  test("tail") {
    forAll(Gen.nonEmptyListOf(Arbitrary.arbitrary[Int])) { (a: List[Int]) =>
      val tail = Await.result(fromSeq(a).tail)
      a.tail match {
        case Nil => assert(tail == None)
        case _ => assert(toSeq(tail.get) == a.tail)
      }
    }
  }

  test("uncons") {
    assert(Await.result(AsyncStream.empty.uncons) == None)
    forAll(Gen.nonEmptyListOf(Arbitrary.arbitrary[Int])) { (a: List[Int]) =>
      val Some((h, t)) = Await.result(fromSeq(a).uncons)
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
      assert(Await.result(fromSeq(as).toSeq()) == as)
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
}

private object AsyncStreamTest {
  val genListAndN = for {
    as <- Arbitrary.arbitrary[List[Int]]
    n <- Gen.choose(0, as.length)
  } yield (as, n)

  def undefined[A]: A = throw new Exception

  def toSeq[A](s: AsyncStream[A]): Seq[A] = Await.result(s.toSeq())
}
