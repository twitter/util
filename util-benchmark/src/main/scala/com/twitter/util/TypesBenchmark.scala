package com.twitter.util

import com.twitter.util.reflect.Types
import org.openjdk.jmh.annotations._
import scala.util.control.NonFatal

private object TypesBenchmark {
  class NotCaseClass {
    val a: String = "A"
    val b: String = "B"
  }

  case class IsCaseClass(a: String, b: String)
}

// ./bazel run //util/util-benchmark/src/main/scala:jmh -- 'TypesBenchmark'
@State(Scope.Benchmark)
class TypesBenchmark extends StdBenchAnnotations {
  import TypesBenchmark._

  @Benchmark
  def notCaseClassWithClass(): Unit = {
    try {
      Types.notCaseClass(classOf[NotCaseClass])
    } catch {
      case NonFatal(_) => // avoid throwing exceptions so the benchmark can finish
    }
  }

  @Benchmark
  def notCaseClassWithCaseClass(): Unit = {
    try {
      Types.notCaseClass(classOf[IsCaseClass])
    } catch {
      case NonFatal(_) => // avoid throwing exceptions so the benchmark can finish
    }
  }

  @Benchmark
  def caseClassWithClass(): Unit = {
    try {
      Types.isCaseClass(classOf[NotCaseClass])
    } catch {
      case NonFatal(_) => // avoid throwing exceptions so the benchmark can finish
    }
  }

  @Benchmark
  def caseClassWithCaseClass(): Unit = {
    try {
      Types.isCaseClass(classOf[IsCaseClass])
    } catch {
      case NonFatal(_) => // avoid throwing exceptions so the benchmark can finish
    }
  }
}
