package com.twitter.string

import com.twitter.util.StdBenchAnnotations
import org.openjdk.jmh.annotations._
import scala.util.Random

// ./sbt 'project util-benchmark' 'jmh:run StringConcatenationBenchmark'
@State(Scope.Benchmark)
class StringConcatenationBenchmark extends StdBenchAnnotations {

  val Length = 16
  val N = 10000
  val rng = new Random(1010101)
  val words = rng.alphanumeric.grouped(Length).map(_.mkString).take(N).toArray
  var i = 0

  private[this] def word(): String = {
    val result = words(i)
    i = (i + 1) % N
    result
  }

  @Benchmark
  def concatenate: String = {
    word() + " " + word() + " " + word()
  }

  @Benchmark
  def interpolate: String = {
    s"${word()} ${word()} ${word()}"
  }

  @Benchmark
  def format: String = {
    "%s %s %s".format(word(), word(), word())
  }

  @Benchmark
  def stringBuilder: String = {
    new StringBuilder()
      .append(word())
      .append(" ")
      .append(word())
      .append(" ")
      .append(word())
      .toString
  }

  @Benchmark
  def sizedStringBuilder: String = {
    val a = word()
    val b = word()
    val c = word()
    new StringBuilder(a.length + b.length + c.length + 2)
      .append(a)
      .append(" ")
      .append(b)
      .append(" ")
      .append(c)
      .toString
  }

  @Benchmark
  def charArray: String = {
    val a = word()
    val b = word()
    val c = word()
    val chars = new Array[Char](a.length + b.length + c.length + 2)
    var i = 0
    for (char <- a) {
      chars(i) = char
      i += 1
    }
    chars(i) = ' '
    i += 1
    for (char <- b) {
      chars(i) = char
      i += 1
    }
    chars(i) = ' '
    i += 1
    for (char <- c) {
      chars(i) = char
      i += 1
    }
    new String(chars)
  }
}

