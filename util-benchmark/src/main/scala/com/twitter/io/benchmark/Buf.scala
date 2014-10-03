package com.twitter.io.benchmark

import com.google.caliper.SimpleBenchmark
import com.twitter.io.{Buf, ConcatBuf}

class BufBenchmark extends SimpleBenchmark {
  val N = 1000
  val delta = 10

  def mkConcattedBuf(size: Int, parts: Int): Buf =
    (for (i <- 0 until parts) yield Buf.ByteArray(Array.fill[Byte](size / parts)(i.toByte))) reduce (_ concat _)

  val simple = mkConcattedBuf(N, 4)

  def timeSimpleSlice(nreps: Int) = {
    var i = 0
    while (i < nreps) {
      sliceBench(simple)
      i += 1
    }
  }

  val complex = mkConcattedBuf(N, 100)

  /**
   * We use while loops here so that we don't add closure allocations in the loop.
   */
  @inline
  def sliceBench(buf: Buf) {
    var j = 0
    while (j < N) {
      var k = j
      while (k < N + delta) {
        buf.slice(j, k)
        k += 1
      }
      j += 1
    }
  }

  def timeComplexSlice(nreps: Int) = {
    var i = 0
    while (i < nreps) {
      sliceBench(complex)
      i += 1
    }
  }
}
