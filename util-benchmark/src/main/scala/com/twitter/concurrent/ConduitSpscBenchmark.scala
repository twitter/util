package com.twitter.concurrent

import com.twitter.io.{Buf, Reader}
import com.twitter.util.{Await, Future, StdBenchAnnotations}
import org.openjdk.jmh.annotations.{Benchmark, Param, Scope, State}

/**
 * These benchmarks measure the overhead of various implementations of a
 * conduit between a `source: () => Future[Buf]` and `sink: Buf =>
 * Future[Unit]`.
 *
 * The steps are as follows:
 *
 * 1. connect the source to the conduit
 * 2. drain three items from the conduit into the sink
 *
 * The control benchmark measures the baseline when there is no conduit, i.e.,
 * the source feeds directly into the sink.
 */
@State(Scope.Benchmark)
class ConduitSpscBenchmark extends StdBenchAnnotations {
  @Param(Array("1", "2", "5", "10"))
  var size: Int = _

  private[this] val buf = Buf.Utf8("howdy")

  private[this] val b = new Broker[Buf]
  private[this] val q = new AsyncQueue[Buf]
  private[this] val r = Reader.writable()

  private[this] def sink(buf: Any): Future[Boolean] = Future.True
  private[this] def source(): Future[Buf] = Future.value(buf)

  private[this] def runControl(n: Int): Future[Boolean] =
    if (n <= 1) source().flatMap(sink)
    else source().flatMap(sink).flatMap(_ => runControl(n - 1))

  /**
   * The control benchmark.
   *
   * `source` is plugged directly into `sink` without a conduit.
   */
  @Benchmark
  def control: Boolean =
    Await.result(runControl(size))

  private[this] def feedQueue(n: Int): Future[Unit] =
    if (n <= 0) Future.Done
    else source().map(q.offer).flatMap(_ => feedQueue(n - 1))

  private[this] def consumeQueue(n: Int): Future[Boolean] =
    if (n <= 1) q.poll().flatMap(sink)
    else q.poll().flatMap(sink).flatMap(_ => consumeQueue(n - 1))

  @Benchmark
  def asyncQueue: Boolean = Await.result({
    // When the producer outpaces the consumer, AsyncQueue are buffers writes,
    // so it's impossible for the producer to rendezvous with the reader. The
    // offers aren't coordinated with the polls. As a result, the AsyncQueue
    // has a little speed advantage.

    // Produce
    feedQueue(size)

    // Consume
    consumeQueue(size)
  })

  private[this] def mkAsyncStream(n: Int): AsyncStream[Buf] =
    if (n <= 0) AsyncStream.empty
    else if (n == 1) AsyncStream.fromFuture(source())
    else AsyncStream.fromFuture(source()) ++ mkAsyncStream(n - 1)

  @Benchmark
  def asyncStream: Boolean = Await.result({
    // AsyncStream is a persistent structure, so access is effectively
    // memoized, which means we have to create one new for each benchmark,
    // otherwise it'd be cheating.

    // Produce
    val stream = mkAsyncStream(size)

    // Consume
    stream.foldLeftF(false) { (_, buf) => sink(buf) }
  })

  private[this] def feedBroker(n: Int): Future[Unit] =
    if (n <= 0) Future.Done
    else source().flatMap(b.send(_).sync()) before feedBroker(n - 1)

  private[this] def consumeBroker(n: Int): Future[Boolean] =
    if (n <= 1) b.recv.sync().flatMap(sink)
    else b.recv.sync().flatMap(sink).flatMap(_ => consumeBroker(n - 1))

  @Benchmark
  def broker: Boolean = Await.result({
    // Produce
    feedBroker(size)

    // Consume
    consumeBroker(size)
  })

  private[this] def feedReader(n: Int): Future[Unit] =
    if (n <= 0) Future.Done
    else source().flatMap(r.write) before feedReader(n - 1)

  private[this] def consumeReader(n: Int): Future[Boolean] =
    if (n <= 1) r.read(Int.MaxValue).flatMap(sink)
    else r.read(Int.MaxValue).flatMap(sink).flatMap(_ => consumeReader(n - 1))

  @Benchmark
  def reader: Boolean = Await.result({
    // Produce
    feedReader(size)

    // Consume
    consumeReader(size)
  })

  private[this] def mkSpool(n: Int): Future[Spool[Buf]] =
    if (n <= 0) Future.value(Spool.empty)
    else source().map(_ *:: mkSpool(n - 1))

  private[this] def consumeSpool[A](spool: Spool[A], b: Boolean): Future[Boolean] =
    if (spool.isEmpty) Future.value(b)
    else sink(spool.head).flatMap { newB =>
      spool.tail.flatMap { tail =>
        consumeSpool(tail, newB)
      }
    }

  @Benchmark
  def spool: Boolean = Await.result({
    // Produce
    val f = mkSpool(size)

    // Consume
    f.flatMap { s => consumeSpool(s, false) }
  })
}
