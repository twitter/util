package com.twitter.io

import com.twitter.util.StdBenchAnnotations
import java.nio.ByteBuffer
import org.openjdk.jmh.annotations.{Benchmark, Param, Scope, State}

object BufConcatBenchmark {
  val bytes: Array[Byte] = Array[Byte](1, 2, 3, 4)

  val byteArrayBuf: Buf = Buf.ByteArray.Owned(bytes)
  val byteBufferBuf: Buf = Buf.ByteBuffer.Owned(ByteBuffer.wrap(bytes))
  val compositeBuf: Buf = byteArrayBuf
    .slice(0, bytes.length / 2)
    .concat(byteArrayBuf.slice(bytes.length / 2, bytes.length))

  private def concatN(n: Int, buf: Buf): Buf = {
    var acc = buf
    var i = 0
    while (i < n) {
      i += 1
      acc = acc.concat(buf)
    }

    acc
  }

  val buf1: Buf = byteArrayBuf
  val buf2: Buf = buf1.concat(buf1)
  val buf3: Buf = buf2.concat(buf1)
  val buf4: Buf = buf3.concat(buf1)

  val bufMatrix: Array[(Buf, Buf)] = Array(
    (buf1, buf1),
    (buf1, buf2),
    (buf1, buf3),
    (buf1, buf4),
    (buf2, buf1),
    (buf2, buf2),
    (buf2, buf3),
    (buf2, buf4),
    (buf3, buf1),
    (buf3, buf2),
    (buf3, buf3),
    (buf3, buf4),
    (buf4, buf1),
    (buf4, buf2),
    (buf4, buf3),
    (buf4, buf4)
  )

  @State(Scope.Thread)
  class Position {
    var i: Int = 0
    def inputs: Array[(Buf, Buf)] = bufMatrix
  }
}

// run via:
// ./sbt 'project util-benchmark' 'jmh:run BufConcatBenchmark'
@State(Scope.Benchmark)
class BufConcatBenchmark extends StdBenchAnnotations {
  import BufConcatBenchmark._

  @Param(Array("1", "5", "10", "20", "40"))
  var concats: Int = _

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

@State(Scope.Benchmark)
class ConcatTwoBufsBenchmark extends StdBenchAnnotations {
  import BufConcatBenchmark._

  @Param(
    Array("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15")
  )
  var index: Int = _

  @Benchmark
  def concatTwoBufs(): Buf = {
    bufMatrix(index)._1.concat(bufMatrix(index)._2)
  }
}
