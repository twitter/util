package com.twitter.hashing

import com.twitter.util.StdBenchAnnotations
import org.openjdk.jmh.annotations.{Benchmark, Level, Param, Scope, Setup, State}
import scala.util.Random

@State(Scope.Benchmark)
class KeyHasherBenchmark extends StdBenchAnnotations {

  private[this] var inputs: Array[String] = _

  @Param(Array("50"))
  var len = 50

  private[this] val N = 1024

  @Setup(Level.Trial)
  def setupTrial(): Unit = {
    val rnd = new Random(515144)
    inputs = Array.fill(N) { rnd.nextString(len) }
  }

  private[this] var i = 0

  @Setup(Level.Iteration)
  def setupIter(): Unit =
    i = 0

  private[this] val keyHasher = KeyHasher.KETAMA

  @Benchmark
  def hashStringKetama: Long = {
    i += 1
    val bytes = inputs(i % N).getBytes("UTF-8")
    keyHasher.hashKey(bytes)
  }

}
