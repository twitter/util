package com.twitter.concurrent

import com.twitter.conversions.time._
import com.twitter.util.{Await, Future, StdBenchAnnotations}
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class AsyncStreamBenchmark extends StdBenchAnnotations {

  /** Number of elements in the AsyncStream */
  @Param(Array("10"))
  var size: Int = _

  private[this] val timeout = 5.seconds

  private[this] var as: AsyncStream[Int] = _

  private[this] def genLongStream(len: Int): AsyncStream[Int] =
    if (len == 0) {
      AsyncStream.of(1)
    } else {
      1 +:: genLongStream(len - 1)
    }
  private[this] var longs: AsyncStream[Int] = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    as = AsyncStream.fromSeq(0.until(size))
    longs = genLongStream(1000)
  }

  @Benchmark
  def baseline(): Seq[Int] =
    Await.result(as.toSeq(), timeout)

  private[this] val MapFn: Int => Int =
    x => x + 1

  @Benchmark
  def map(): Seq[Int] =
    Await.result(as.map(MapFn).toSeq())

  private[this] val FlatMapFn: Int => AsyncStream[Int] =
    x => AsyncStream(x)

  @Benchmark
  def flatMap(): Seq[Int] =
    Await.result(as.flatMap(FlatMapFn).toSeq())

  private[this] val MapConcurrentFn: Int => Future[Int] =
    x => Future.value(x + 1)

  @Benchmark
  def mapConcurrent(): Seq[Int] =
    Await.result(as.mapConcurrent(2)(MapConcurrentFn).toSeq())

  private[this] val FilterFn: Int => Boolean =
    x => x % 2 == 0

  @Benchmark
  def filter(): Seq[Int] =
    Await.result(as.filter(FilterFn).toSeq())

  private[this] val TakeWhileFn: Int => Boolean =
    x => x < size / 2

  @Benchmark
  def takeWhile(): Seq[Int] =
    Await.result(as.takeWhile(TakeWhileFn).toSeq())

  @Benchmark
  def ++(): Int =
    Await.result((longs ++ as).foldLeft(0)(_ + _))
}
