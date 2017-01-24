package com.twitter.io

import com.twitter.util.StdBenchAnnotations
import java.nio
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import scala.util.Random

// run via:
// ./sbt 'project util-benchmark' 'jmh:run BufBenchmark'
@State(Scope.Benchmark)
class BufBenchmark extends StdBenchAnnotations {

  @Param(Array("1000"))
  var size: Int = 1000

  private[this] var bytes: Array[Byte] = _

  private[this] var byteArrayBuf: Buf = _
  private[this] var byteBufferBuf: Buf = _
  private[this] var concatBuf: Buf = _
  // create a 2nd composite that is sliced differently from the other
  // to avoid some implementation artifacts changing the perf.
  private[this] var concatBuf2: Buf = _

  private[this] var string: String = _
  private[this] var stringBuf: Buf = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val cap = size * 2
    val start = cap / 4
    val end = start + size
    bytes = 0.until(cap).map(_.toByte).toArray

    val bb = java.nio.ByteBuffer.wrap(bytes, start, size)

    byteArrayBuf = Buf.ByteArray.Owned(bytes, start, end)
    byteBufferBuf = Buf.ByteBuffer.Owned(bb)
    concatBuf = byteArrayBuf.slice(0, size / 2).concat(byteArrayBuf.slice(size / 2, size))
    concatBuf2 = byteArrayBuf.slice(0, size / 4).concat(byteArrayBuf.slice(size / 4, size))

    val rnd = new Random(120412421512L)
    string = rnd.nextString(size)
    stringBuf = Buf.Utf8(string)
  }

  @Benchmark
  def equalityByteArrayByteArray(): Boolean =
    byteArrayBuf == byteArrayBuf

  @Benchmark
  def equalityByteArrayByteBuffer(): Boolean =
    byteArrayBuf == byteBufferBuf

  @Benchmark
  def equalityByteArrayConcat(): Boolean =
    byteArrayBuf == concatBuf

  @Benchmark
  def equalityByteBufferByteArray(): Boolean =
    byteBufferBuf == byteArrayBuf

  @Benchmark
  def equalityByteBufferByteBuffer(): Boolean =
    byteBufferBuf == byteBufferBuf

  @Benchmark
  def equalityByteBufferConcat(): Boolean =
    byteBufferBuf == concatBuf

  @Benchmark
  def equalityConcatByteArray(): Boolean =
    concatBuf == byteArrayBuf

  @Benchmark
  def equalityConcatByteBuffer(): Boolean =
    concatBuf == byteBufferBuf

  @Benchmark
  def equalityConcatConcat(): Boolean =
    concatBuf == concatBuf2

  private[this] def hash(buf: Buf): Int = buf.hashCode()

  @Benchmark
  @Warmup(iterations = 5)
  @Measurement(iterations = 5)
  def hashCodeByteArrayBufBaseline(): Buf =
    Buf.ByteArray.Owned(bytes, 1, size + 1)

  // subtract the results of the Baseline run to get the results
  @Benchmark
  @Warmup(iterations = 5)
  @Measurement(iterations = 5)
  def hashCodeByteArrayBuf(hole: Blackhole): Int = {
    val buf = hashCodeByteArrayBufBaseline()
    hole.consume(buf)
    hash(buf)
  }

  @Benchmark
  @Warmup(iterations = 5)
  @Measurement(iterations = 5)
  def hashCodeByteBufferBufBaseline(): Buf =
    Buf.ByteBuffer.Owned(java.nio.ByteBuffer.wrap(bytes, 1, size))

  // subtract the results of the Baseline run to get the results
  @Benchmark
  @Warmup(iterations = 5)
  @Measurement(iterations = 5)
  def hashCodeByteBufferBuf(hole: Blackhole): Int = {
    val buf = hashCodeByteBufferBufBaseline()
    hole.consume(buf)
    hash(buf)
  }

  @Benchmark
  @Warmup(iterations = 5)
  @Measurement(iterations = 5)
  def hashCodeConcatBufBaseline(): Buf =
    Buf.ByteArray.Owned(bytes, 0, 5).concat(Buf.ByteArray.Owned(bytes, 5, size))

  // subtract the results of the Baseline run to get the results
  @Benchmark
  @Warmup(iterations = 5)
  @Measurement(iterations = 5)
  def hashCodeConcatBuf(hole: Blackhole): Int = {
    val buf = hashCodeConcatBufBaseline()
    hole.consume(buf)
    hash(buf)
  }

  private[this] def slice(buf: Buf): Buf =
    buf.slice(size / 4, size / 4 + size / 2)

  @Benchmark
  def sliceByteArrayBuf(): Buf =
    slice(byteArrayBuf)

  @Benchmark
  def sliceByteBufferBuf(): Buf =
    slice(byteBufferBuf)

  @Benchmark
  def sliceConcatBuf(): Buf =
    slice(concatBuf)

  private[this] def concat(buf: Buf): Buf =
    buf.concat(buf)

  @Benchmark
  def concatByteArrayBuf(): Buf =
    concat(byteArrayBuf)

  @Benchmark
  def concatByteBufferBuf(): Buf =
    concat(byteBufferBuf)

  @Benchmark
  def concatConcatBuf(): Buf =
    concat(concatBuf)

  private[this] def asByteBuffer(buf: Buf): nio.ByteBuffer =
    Buf.ByteBuffer.Owned.extract(buf)

  @Benchmark
  def asByteBufferByteArrayBuf(): nio.ByteBuffer =
    asByteBuffer(byteArrayBuf)

  @Benchmark
  def asByteBufferByteBufferBuf(): nio.ByteBuffer =
    asByteBuffer(byteBufferBuf)

  @Benchmark
  def asByteBufferConcatBuf(): nio.ByteBuffer =
    asByteBuffer(concatBuf)

  private[this] def asByteArray(buf: Buf): Array[Byte] =
    Buf.ByteArray.Owned.extract(buf)

  @Benchmark
  def asByteArrayByteArrayBuf(): Array[Byte] =
    asByteArray(byteArrayBuf)

  @Benchmark
  def asByteArrayByteBufferBuf(): Array[Byte] =
    asByteArray(byteBufferBuf)

  @Benchmark
  def asByteArrayConcatBuf(): Array[Byte] =
    asByteArray(concatBuf)

  @Benchmark
  def stringToUtf8Buf(): Buf =
    Buf.Utf8(string)

  @Benchmark
  def utf8BufToString(): String = {
    val Buf.Utf8(str) = stringBuf
    str
  }

  private val out = new Array[Byte](1)

  private[this] def singleByteSliceAndWrite(buf: Buf): Byte = {
    buf.slice(0, 1).write(out, 0)
    out(0)
  }

  private[this] def singleByteIndexed(buf: Buf): Byte = {
    val idx = Buf.Indexed.coerce(buf)
    idx(0)
  }

  @Benchmark
  def singleByteSliceAndWriteByteArray(): Byte =
    singleByteSliceAndWrite(byteArrayBuf)

  @Benchmark
  def singleByteSliceAndWriteByteBuffer(): Byte =
    singleByteSliceAndWrite(byteBufferBuf)

  @Benchmark
  def singleByteSliceAndWriteConcatBuf(): Byte =
    singleByteSliceAndWrite(concatBuf)

  @Benchmark
  def singleByteIndexedByteArray(): Byte =
    singleByteIndexed(byteArrayBuf)

  @Benchmark
  def singleByteIndexedByteBuffer(): Byte =
    singleByteIndexed(byteBufferBuf)

  @Benchmark
  def singleByteIndexedConcatBuf(): Byte =
    singleByteIndexed(concatBuf)

}
