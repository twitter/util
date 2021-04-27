package com.twitter.util

import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ValidationBenchmarkTest extends AnyFunSuite {
  private[this] val benchmark = new ValidationBenchmark()

  test("validate case class") {
    benchmark.withValidUser()
    benchmark.withInvalidUser()
  }

  test("validate nested case class") {
    benchmark.withNestedValidUser()
    benchmark.withNestedInvalidUser()
    benchmark.withNestedDuplicateUser()
  }
}
