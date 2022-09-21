package com.twitter.conversions

import com.twitter.conversions.U64Ops._
import com.twitter.util.StdBenchAnnotations
import org.openjdk.jmh.annotations._

// ./bazel run //util/util-benchmark/src/main/scala:jmh -- 'U64Benchmark'
@State(Scope.Benchmark)
class U64Benchmark extends StdBenchAnnotations {
  private[this] val rng = new scala.util.Random(42)
  private[this] val hexes = Seq.fill(97)(rng.nextLong()) :+ 0L :+ Long.MaxValue :+ Long.MinValue
  private[this] val longToHex: Long => String = { long: Long => long.toU64HexString }
  private[this] val javaLongToHex: Long => String = (java.lang.Long.toHexString _)
  private[this] val leadingZerosJavaLongToHex: Long => String = { long: Long =>
    "%16x".format(long)
  }

  @Benchmark
  def u64Hexes: Seq[String] = {
    hexes.map(longToHex)
  }

  @Benchmark
  def javaHexes: Seq[String] = {
    hexes.map(javaLongToHex)
  }

  @Benchmark
  def leadingZerosJavaHexes: Seq[String] = {
    hexes.map(leadingZerosJavaLongToHex)
  }

}
