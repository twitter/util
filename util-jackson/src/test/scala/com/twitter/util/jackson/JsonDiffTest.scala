package com.twitter.util.jackson

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.{ByteArrayOutputStream, PrintStream}
import org.junit.runner.RunWith
import org.scalacheck.Gen
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scala.util.control.NonFatal

@RunWith(classOf[JUnitRunner])
class JsonDiffTest
    extends AnyFunSuite
    with Matchers
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with JsonGenerator
    with ScalaCheckDrivenPropertyChecks {

  private[this] val mapper = ScalaObjectMapper.builder.objectMapper

  private[this] val baos = new ByteArrayOutputStream()
  private[this] val ps = new PrintStream(baos, true, "utf-8")

  override protected def beforeEach(): Unit = {
    ps.flush()
  }

  override protected def afterAll(): Unit = {
    try {
      ps.close() // closes underlying outputstream
    } catch {
      case NonFatal(_) => // do nothing
    }
    super.afterAll()
  }

  test("JsonDiff#diff success") {
    val a =
      """
      {
        "a": 1,
        "b": 2
      }
      """

    val b =
      """
      {
        "b": 2,
        "a": 1
      }
      """

    val result = JsonDiff.diff(a, b)
    result.isDefined should be(false)
  }

  test("JsonDiff#diff with normalizer success") {
    val expected =
      """
      {
        "a": 1,
        "b": 2,
        "c": 5
      }
      """

    // normalizerFn will "normalize" the value for "c" into the expected value
    val actual =
      """
      {
        "b": 2,
        "a": 1,
        "c": 3
      }
      """

    val result = JsonDiff.diff(
      expected,
      actual,
      normalizeFn = { jsonNode: JsonNode =>
        jsonNode.asInstanceOf[ObjectNode].put("c", 5)
      }
    )
    result.isDefined should be(false)
  }

  test("JsonDiff#diff failure") {
    val expected =
      """
      {
        "a": 1,
        "b": 2
      }
      """

    val actual =
      """
      {
        "b": 22,
        "a": 1
      }
      """

    val result = JsonDiff.diff(expected, actual)
    result.isDefined should be(true)
  }

  test("JsonDiff#diff with normalizer failure") {
    val expected =
      """
      {
        "a": 1,
        "b": 2,
        "c": 5
      }
      """

    // normalizerFn only touches the value for "c" and "b" still doesn't match
    val actual =
      """
      {
        "b": 22,
        "a": 1,
        "c": 3
      }
      """

    val result = JsonDiff.diff(
      expected,
      actual,
      normalizeFn = { jsonNode: JsonNode =>
        jsonNode.asInstanceOf[ObjectNode].put("c", 5)
      }
    )
    result.isDefined should be(true)
  }

  test("JsonDiff#assertDiff pass") {
    val expected =
      """
      {
        "a": 1,
        "b": 2
      }
    """

    val actual =
      """
      {
        "a": 1,
        "b": 2
      }
    """

    JsonDiff.assertDiff(expected, actual, p = ps)
  }

  test("JsonDiff#assertDiff with normalizer pass") {
    val expected =
      """
      {
        "a": 1,
        "b": 2,
        "c": 3
      }
    """

    // normalizerFn will "normalize" the value for "c" into the expected value
    val actual =
      """
      {
        "c": 5,
        "a": 1,
        "b": 2
      }
    """

    JsonDiff.assertDiff(
      expected,
      actual,
      normalizeFn = { jsonNode: JsonNode =>
        jsonNode.asInstanceOf[ObjectNode].put("c", 3)
      },
      p = ps)
  }

  test("JsonDiff#assertDiff with normalizer fail") {
    val expected =
      """
      {
        "a": 1,
        "b": 2,
        "c": 3
      }
    """

    // normalizerFn only touches the value for "c" and "a" still doesn't match
    val actual =
      """
      {
        "c": 5,
        "a": 11,
        "b": 2
      }
    """

    intercept[AssertionError] {
      JsonDiff.assertDiff(
        expected,
        actual,
        normalizeFn = { jsonNode: JsonNode =>
          jsonNode.asInstanceOf[ObjectNode].put("c", 3)
        },
        p = ps)
    }
  }

  test("JsonDiff#diff arbitrary pass") {
    val expected = for {
      depth <- Gen.chooseNum[Int](1, 10)
      on <- objectNode(depth)
    } yield on

    forAll(expected) { node =>
      val json = mapper.writeValueAsString(node)
      JsonDiff.diff(expected = json, actual = json).isEmpty should be(true)
    }
  }

  test("JsonDiff#diff arbitrary fail") {
    val values = for {
      depth <- Gen.chooseNum[Int](1, 5)
      expected <- objectNode(depth)
      actual <- objectNode(depth)
      if expected != actual
    } yield (expected, actual)

    forAll(values) {
      case (expected, actual) =>
        val result = JsonDiff.diff(expected = expected, actual = actual)
        result.isDefined should be(true)
    }
  }

  test("JsonDiff#generate sorted") {
    val before = mapper.parse[JsonNode]("""{"a":1,"c":3,"b":2}""")
    val expected = """{"a":1,"b":2,"c":3}"""
    JsonDiff.toSortedString(before) should equal(expected)
  }
}
