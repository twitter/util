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
  private[this] var compositeBuf: Buf = _
  // create a 2nd composite that is sliced differently from the other
  // to avoid some implementation artifacts changing the perf.
  private[this] var compositeBuf2: Buf = _

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
    compositeBuf = byteArrayBuf.slice(0, size / 2).concat(byteArrayBuf.slice(size / 2, size))
    compositeBuf2 = byteArrayBuf.slice(0, size / 4).concat(byteArrayBuf.slice(size / 4, size))

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
  def equalityByteArrayComposite(): Boolean =
    byteArrayBuf == compositeBuf

  @Benchmark
  def equalityByteBufferByteArray(): Boolean =
    byteBufferBuf == byteArrayBuf

  @Benchmark
  def equalityByteBufferByteBuffer(): Boolean =
    byteBufferBuf == byteBufferBuf

  @Benchmark
  def equalityByteBufferComposite(): Boolean =
    byteBufferBuf == compositeBuf

  @Benchmark
  def equalityCompositeByteArray(): Boolean =
    compositeBuf == byteArrayBuf

  @Benchmark
  def equalityCompositeByteBuffer(): Boolean =
    compositeBuf == byteBufferBuf

  @Benchmark
  def equalityCompositeComposite(): Boolean =
    compositeBuf == compositeBuf2

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
  def hashCodeCompositeBufBaseline(): Buf =
    Buf.ByteArray.Owned(bytes, 0, 5).concat(Buf.ByteArray.Owned(bytes, 5, size))

  // subtract the results of the Baseline run to get the results
  @Benchmark
  @Warmup(iterations = 5)
  @Measurement(iterations = 5)
  def hashCodeCompositeBuf(hole: Blackhole): Int = {
    val buf = hashCodeCompositeBufBaseline()
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
  def sliceCompositeBuf(): Buf =
    slice(compositeBuf)

  private[this] def concat(buf: Buf): Buf =
    buf.concat(buf)

  @Benchmark
  def concatByteArrayBuf(): Buf =
    concat(byteArrayBuf)

  @Benchmark
  def concatByteBufferBuf(): Buf =
    concat(byteBufferBuf)

  @Benchmark
  def concatCompositeBuf(): Buf =
    concat(compositeBuf)

  private[this] def asByteBuffer(buf: Buf): nio.ByteBuffer =
    Buf.ByteBuffer.Owned.extract(buf)

  @Benchmark
  def asByteBufferByteArrayBuf(): nio.ByteBuffer =
    asByteBuffer(byteArrayBuf)

  @Benchmark
  def asByteBufferByteBufferBuf(): nio.ByteBuffer =
    asByteBuffer(byteBufferBuf)

  @Benchmark
  def asByteBufferCompositeBuf(): nio.ByteBuffer =
    asByteBuffer(compositeBuf)

  private[this] def asByteArray(buf: Buf): Array[Byte] =
    Buf.ByteArray.Owned.extract(buf)

  @Benchmark
  def asByteArrayByteArrayBuf(): Array[Byte] =
    asByteArray(byteArrayBuf)

  @Benchmark
  def asByteArrayByteBufferBuf(): Array[Byte] =
    asByteArray(byteBufferBuf)

  @Benchmark
  def asByteArrayCompositeBuf(): Array[Byte] =
    asByteArray(compositeBuf)

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

  private[this] def singleByteGet(buf: Buf): Byte =
    buf.get(0)

  @Benchmark
  def singleByteSliceAndWriteByteArray(): Byte =
    singleByteSliceAndWrite(byteArrayBuf)

  @Benchmark
  def singleByteSliceAndWriteByteBuffer(): Byte =
    singleByteSliceAndWrite(byteBufferBuf)

  @Benchmark
  def singleByteSliceAndWriteCompositeBuf(): Byte =
    singleByteSliceAndWrite(compositeBuf)

  @Benchmark
  def singleByteIndexedByteArray(): Byte =
    singleByteGet(byteArrayBuf)

  @Benchmark
  def singleByteIndexedByteBuffer(): Byte =
    singleByteGet(byteBufferBuf)

  @Benchmark
  def singleByteIndexedCompositeBuf(): Byte =
    singleByteGet(compositeBuf)

}
