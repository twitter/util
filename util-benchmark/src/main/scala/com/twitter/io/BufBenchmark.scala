package com.twitter.io

import com.twitter.util.StdBenchAnnotations
import java.nio
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import scala.util.Random

@State(Scope.Benchmark)
class BufBenchmark extends StdBenchAnnotations {

  @Param(Array("1000"))
  var size: Int = 1000

  private[this] var byteArrayBuf: Buf = _
  private[this] var byteBufferBuf: Buf = _
  private[this] var concatBuf: Buf = _
  private[this] var all: Array[Buf] = _

  private[this] var string: String = _
  private[this] var stringBuf: Buf = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val cap = size * 2
    val start = cap / 4
    val end = start + size
    val raw = 0.until(cap).map(_.toByte).toArray

    val bb = java.nio.ByteBuffer.wrap(raw, start, size)

    byteArrayBuf = Buf.ByteArray.Owned(raw, start, end)
    byteBufferBuf = Buf.ByteBuffer.Owned(bb)
    concatBuf = byteArrayBuf.slice(0, size / 2).concat(byteArrayBuf.slice(size / 2, size))
    all = Array(byteArrayBuf, byteBufferBuf, concatBuf)

    val rnd = new Random(120412421512L)
    string = rnd.nextString(size)
    stringBuf = Buf.Utf8(string)
  }

  private[this] def equality(buf: Buf, hole: Blackhole): Unit = {
    var i = 0
    while (i < all.length) {
      hole.consume(buf == all(i))
      i += 1
    }
  }

  @Benchmark
  def equalityByteArrayBuf(hole: Blackhole): Unit =
    equality(byteArrayBuf, hole)

  @Benchmark
  def equalityByteBufferBuf(hole: Blackhole): Unit =
    equality(byteBufferBuf, hole)

  @Benchmark
  def equalityConcatBuf(hole: Blackhole): Unit =
    equality(concatBuf, hole)

  private[this] def hash(buf: Buf): Int = buf.hashCode()

  @Benchmark
  def hashCodeByteArrayBuf(): Int =
    hash(byteArrayBuf)

  @Benchmark
  def hashCodeByteBufferBuf(): Int =
    hash(byteBufferBuf)

  @Benchmark
  def hashCodeConcatBuf(): Int =
    hash(concatBuf)

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

}
