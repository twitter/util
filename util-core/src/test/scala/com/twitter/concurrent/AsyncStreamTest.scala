package com.twitter.concurrent

import com.twitter.conversions.time._
import com.twitter.io.{Buf, Reader}
import com.twitter.util._
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
    val p = new Promise[Unit]
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

    assert(toSeq(s.take(1)) == Seq(()))
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

  class Ctx[A](ops: AsyncStream[Int] => AsyncStream[A]) {
    var once = 0
    val s: AsyncStream[Int] = 2 +:: {
      once = once + 1
      if (once > 1) throw new Exception("evaluated more than once")
      AsyncStream.of(1)
    }

    val ss = ops(s)
    ss.foreach(_ => ())
    // does not throw
    ss.foreach(_ => ())
  }

  test("memoized stream") {
    new Ctx(s => s.map(_ => 0))
    new Ctx(s => s.mapF(_ => Future.value(1)))
    new Ctx(s => s.flatMap(of(_)))
    new Ctx(s => s.filter(_ => true))
    new Ctx(s => s.withFilter(_ => true))
    new Ctx(s => s.take(2))
    new Ctx(s => s.takeWhile(_ => true))
    new Ctx(s => s.scanLeft(Future.Done) { (_, _) => Future.Done })
    new Ctx(s => s ++ s)
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
      assert(toSeq(fromSeq(a) ++ fromSeq(b)) == a ++ b)
    }
  }

  test("++ with a long stream") {
    var count = 0
    def genLongStream(len: Int): AsyncStream[Int] =
      if (len == 0) {
        AsyncStream.of(1)
      } else {
        count = count + 1
        1 +:: genLongStream(len - 1)
      }
    // concat a long stream does not stack overflow
    val s = genLongStream(1000000) ++ genLongStream(3)
    s.foreach(_ => ())
    val first = count
    s.foreach(_ => ())
    // the values are evaluated once
    assert(count == first)
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
    forAll(genListAndSentinel) { case (as, x) =>
      assert(toSeq(fromSeq(as).takeWhile(_ != x)) == as.takeWhile(_ != x))
    }
  }

  test("dropWhile") {
    forAll(genListAndSentinel) { case (as, x) =>
      assert(toSeq(fromSeq(as).dropWhile(_ != x)) == as.dropWhile(_ != x))
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
      assert(expectedRest == toSeq(rest()))
    }
  }

  test("buffer() has the same properties as take() and drop()") {
    // We need items to be non-empty, because AsyncStream.empty ++
    // <something> forces the future to be created.
    val gen = Gen.zip(Gen.nonEmptyListOf(Arbitrary.arbitrary[Char]), Arbitrary.arbitrary[Int])

    forAll(gen) { case (items, n) =>
      var forced1 = false
      val stream1 = fromSeq(items) ++ { forced1 = true; AsyncStream.empty[Char] }
      var forced2 = false
      val stream2 = fromSeq(items) ++ { forced2 = true; AsyncStream.empty[Char] }

      val takeResult = toSeq(stream2.take(n))
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
      assert(toSeq(bufferTail) == toSeq(dropTail))
      assert(forced1)
      assert(forced2)
    }
  }

  test("grouped() works like Seq.grouped") {
    forAll { (items: Seq[Char], groupSize: Int) =>
      // This is a Try so that we can test that bad inputs act the
      // same. (Zero or negative group sizes throw the same
      // exception.)
      val expected = Try(items.grouped(groupSize).toSeq)
      val actual = Try(toSeq(fromSeq(items).grouped(groupSize)))

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
    val gen =
      for {
        // We need items to be non-empty, because AsyncStream.empty ++
        // <something> forces the future to be created.
        items <- Gen.nonEmptyListOf(Arbitrary.arbitrary[Char])

        // We need to make sure that the chunk size (1) is valid and (2)
        // is short enough that forcing the first group does not force
        // the exception.
        groupSize <- Gen.chooseNum(1, items.size)
      } yield (items, groupSize)

    forAll(gen) { case (items, groupSize) =>
      var forced = false
      val stream: AsyncStream[Char] = fromSeq(items) ++ { forced = true; AsyncStream.empty }

      val expected = items.grouped(groupSize).toSeq.headOption
      // This will take up to items.size items from the stream. This
      // does not require forcing the tail.
      val actual = await(stream.grouped(groupSize).head)
      assert(actual == expected)
      assert(!forced)
      val expectedChunks = items.grouped(groupSize).toSeq
      val allChunks = toSeq(stream.grouped(groupSize))
      assert(allChunks == expectedChunks)
      assert(forced)
    }
  }

  test("mapConcurrent preserves items") {
    forAll(Arbitrary.arbitrary[List[Int]], Gen.choose(1, 10)) { (xs, conc) =>
      assert(toSeq(AsyncStream.fromSeq(xs).mapConcurrent(conc)(Future.value)).sorted == xs.sorted)
    }
  }

  test("mapConcurrent makes progress when an item is blocking") {
    forAll(Arbitrary.arbitrary[List[Int]], Gen.choose(2, 10)) { (xs, conc) =>
      // This promise is not satisfied, which would block the evaluation
      // of .map, and should not block .mapConcurrent when conc > 1
      val first = new Promise[Int]

      // This function will return a blocking future the first time it
      // is called and an immediately-available future thereafter.
      var used = false
      def f(x: Int) =
        if (used) {
          Future.value(x)
        } else {
          used = true
          first
        }

      // Concurrently map over the stream. The whole stream should be
      // available, except for one item which is still blocked.
      val mapped = AsyncStream.fromSeq(xs).mapConcurrent(conc)(f)

      // All but the first value, which is still blocking, has been returned
      assert(toSeq(mapped.take(xs.length - 1)).sorted == xs.drop(1).sorted)

      if (xs.nonEmpty) {
        // The stream as a whole is still blocking on the unsatisfied promise
        assert(!mapped.foreach(_ => ()).isDefined)

      // Unblock the first value
        first.setValue(xs.head)
      }

      // Now the whole stream should be available and should contain all
      // of the items, ignoring order (but preserving repetition)
      assert(mapped.foreach(_ => ()).isDefined)
      assert(toSeq(mapped).sorted == xs.sorted)
    }
  }

  test("mapConcurrent is lazy once it reaches its concurrency limit") {
    forAll(Gen.choose(2, 10), Arbitrary.arbitrary[Seq[Int]]) { (conc, xs) =>
      val q = new scala.collection.mutable.Queue[Promise[Unit]]

      val mapped =
        AsyncStream.fromSeq(xs).mapConcurrent(conc) { _ =>
          val p = new Promise[Unit]
          q.enqueue(p)
          p
        }

      // If there are at least `conc` items in the queue, then we should
      // have started exactly `conc` of them. Otherwise, we should have
      // started all of them.
      assert(q.size == conc.min(xs.size))

      if (xs.nonEmpty) {
        assert(!mapped.head.isDefined)

        val p = q.dequeue()
        p.setDone()
      }

      // Satisfying that promise makes the head of the queue available.
      assert(mapped.head.isDefined)

      if (xs.size > 1) {
        // We do not add another element to the queue until the next
        // element is forced.
        assert(q.size == (conc.min(xs.size) - 1))

        val tl = mapped.drop(1)
        assert(!tl.head.isDefined)

        // Forcing the next element of the queue causes us to enqueue
        // one more element (if there are more elements to enqueue)
        assert(q.size == conc.min(xs.size - 1))

        val p = q.dequeue()
        p.setDone()

        // Satisfying that promise causes the head to be available.
        assert(tl.head.isDefined)
      }
    }
  }

  test("mapConcurrent makes progress, even with blocking streams and blocking work") {
    val gen =
      Gen.zip(
        Gen.choose(0, 10).label("numActions"),
        Gen.choose(0, 10).flatMap(Gen.listOfN(_, Arbitrary.arbitrary[Int])),
        Gen.choose(1, 11).label("concurrency")
      )

    forAll(gen) { case (numActions, items, concurrency) =>
      val input: AsyncStream[Int] =
        AsyncStream.fromSeq(items) ++ AsyncStream.fromFuture(Future.never)

      var workStarted = 0
      var workFinished = 0
      val result =
        input.mapConcurrent(concurrency) { i =>
          workStarted += 1
          if (workFinished < numActions) {
            workFinished += 1
            Future.value(i)
          } else {
            // After numActions evaluations, return a Future that
            // will never be satisfied.
            Future.never
          }
        }

      // How much work should have been started by mapConcurrent.
      val expectedStarted = items.size.min(concurrency)
      assert(workStarted == expectedStarted, "work started")

      val expectedFinished = numActions.min(expectedStarted)
      assert(workFinished == expectedFinished, "expected finished")

      // Make sure that all of the finished items are now
      // available. (As a side-effect, this will force more work to
      // be done if concurrency was the limiting factor.)
      val completed = toSeq(result.take(workFinished)).sorted
      val expectedCompleted = items.take(expectedFinished).sorted
      assert(completed == expectedCompleted)
    }
  }

  test("fromReader") {
    forAll { l: List[Byte] =>
      val buf = Buf.ByteArray.Owned(l.toArray)
      val as = AsyncStream.fromReader(Reader.fromBuf(buf), chunkSize = 1)

      assert(toSeq(as).map(b => Buf.ByteArray.Owned.extract(b).head) == l)
    }
  }

  test("sum") {
    forAll { xs: List[Int] =>
      assert(xs.sum == await(AsyncStream.fromSeq(xs).sum))
    }
  }

  test("size") {
    forAll { xs: List[Int] =>
      assert(xs.size == await(AsyncStream.fromSeq(xs).size))
    }
  }

  test("force") {
    forAll { xs: List[Int] =>
      val p = new Promise[Unit]
      // The promise will be defined iff the tail is forced.
      val s = AsyncStream.fromSeq(xs) ++ { p.setDone() ; AsyncStream.empty }

      // If the input is empty, then the tail will be forced right away.
      assert(p.isDefined == xs.isEmpty)

      // Unconditionally force the whole stream
      await(s.force)
      assert(p.isDefined)
    }
  }

  test("withEffect") {
    forAll(genListAndN) { case (xs, n) =>
      var i = 0
      val s = AsyncStream.fromSeq(xs).withEffect(_ => i += 1)

      // Is lazy on initial application (with the exception of the first element)
      assert(i == (if (xs.isEmpty) 0 else 1))

      // Is lazy when consuming the stream
      await(s.take(n).force)

      // If the list is empty, no effects should occur.  If the list is
      // non-empty, the effect will occur for the first item right away,
      // since the head is not lazy. Otherwise, we expect the same
      // number of effects as items demanded.
      val expected = if (xs.isEmpty) 0 else 1.max(xs.length.min(n))
      assert(i == expected)

      // Preserves the elements in the stream
      assert(toSeq(s) == xs)
    }
  }

  test("merge generates a stream equal to all input streams") {
    forAll { (lists: Seq[List[Int]]) =>
      val streams = lists.map(fromSeq)
      val merged = AsyncStream.merge(streams: _*)

      val input = AsyncStream(streams: _*).flatten

      assert(toSeq(input).sorted == toSeq(merged).sorted)
    }
  }

  test("merge fails the result stream if an input stream fails") {
    forAll() { (lists: Seq[List[Int]]) =>
      val s = mk(1, undefined: AsyncStream[Int])
      val streams = s +: lists.map(fromSeq)
      val merged = AsyncStream.merge(streams: _*)

      intercept[Exception](toSeq(merged))
    }
  }

  test("merged stream contains elements as they become available from input streams") {
    forAll { (input: List[Int]) =>
      val promises = List.fill(input.size)(Promise[Int]())

      // grouped into lists of 10 elements each
      val grouped = promises.grouped(10).toList

      // merged list of streams
      val streams = grouped.map(fromSeq(_).flatMap(AsyncStream.fromFuture))
      val merged = AsyncStream.merge(streams: _*).toSeq()

      // build an interleaved list of the promises for the stream
      // [s1(1), s2(1), s3(1), s1(2), s2(2), s3(2), ...]
      val interleavedHeads = grouped.flatMap(_.zipWithIndex).sortBy(_._2).map(_._1)
      interleavedHeads.zip(input).foreach { case (p, i) =>
        p.update(Return(i))
      }

      assert(Await.result(merged) == input)
    }
  }

}

private object AsyncStreamTest {
  val genListAndN = for {
    as <- Arbitrary.arbitrary[List[Int]]
    n <- Gen.choose(0, as.length)
  } yield (as, n)

  val genListAndSentinel = for {
    as <- Arbitrary.arbitrary[List[Int]]
    n <- Gen.choose(0, as.length - 1)
  } yield (as, as(n))

  def await[T](fut: Future[T]) = Await.result(fut, 100.milliseconds)

  def undefined[A]: A = throw new Exception

  def toSeq[A](s: AsyncStream[A]): Seq[A] = await(s.toSeq())

  def fromSeq[A](s: Seq[A]): AsyncStream[A] =
    // Test all AsyncStream constructors: Empty, FromFuture, Cons, Embed.
    s match {
      case Nil => AsyncStream.empty
      case a +: Nil => AsyncStream.of(a)
      case a +: b +: Nil => AsyncStream.embed(Future.value(a +:: AsyncStream.of(b)))
      case a +: as => a +:: fromSeq(as)
    }
}
