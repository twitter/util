package com.twitter.io

import com.twitter.util.StdBenchAnnotations
import java.nio.ByteBuffer
import org.openjdk.jmh.annotations.{Benchmark, Param, Scope, State}

// run via:
// ./sbt 'project util-benchmark' 'jmh:run BufConcatBenchmark'
@State(Scope.Benchmark)
class BufConcatBenchmark extends StdBenchAnnotations {

  @Param(Array("1", "5", "10", "20", "40"))
  var concats: Int = _

  val bytes = Array[Byte](1, 2, 3, 4)

  val byteArrayBuf = Buf.ByteArray.Owned(bytes)
  val byteBufferBuf = Buf.ByteBuffer.Owned(ByteBuffer.wrap(bytes))
  val compositeBuf = byteArrayBuf
    .slice(0, bytes.length / 2)
    .concat(byteArrayBuf.slice(bytes.length / 2, bytes.length))

  private[this] def concatN(n: Int, buf: Buf): Buf = {
    var acc = buf
    var i = 0
    while (i < n) {
      i += 1
      acc = acc.concat(buf)
    }

    acc
  }

  @Benchmark
  def concatByteBufferBuf(): Buf =
    concatN(concats, byteBufferBuf)

  @Benchmark
  def concatByteArrayBuf(): Buf =
    concatN(concats, byteArrayBuf)

  @Benchmark
  def concatCompositeBuf(): Buf =
    concatN(concats, compositeBuf)
}
