package com.twitter.hashing

import com.twitter.util.StdBenchAnnotations
import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class KetamaDistributorBench extends StdBenchAnnotations {

  // See CSL-2733 (internal ticket) on how these numbers are picked.
  val numReps = 640
  val numHosts = 310

  val hosts: scala.collection.immutable.IndexedSeq[String] = 0.until(numHosts).map { i =>
    "abcd-zzz-" + i + "-ab2.qwer.twitter.com"
  }
  val nodes: scala.collection.immutable.IndexedSeq[KetamaNode[String]] = hosts map { h =>
    KetamaNode[String](h + ":12021", 1, h)
  }

  @Benchmark
  def ketama(): KetamaDistributor[String] = new KetamaDistributor(nodes, numReps, false)
}
