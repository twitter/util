package com.twitter.util.jackson

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapperCopier, SerializationFeature}
import com.twitter.util.logging.Logging
import java.io.PrintStream
import scala.util.control.NonFatal

object JsonDiff extends Logging {

  private[this] lazy val mapper: ScalaObjectMapper =
    ScalaObjectMapper.builder.objectMapper

  private[this] lazy val sortingObjectMapper: JacksonScalaObjectMapperType = {
    val newMapper = ObjectMapperCopier.copy(mapper.underlying)
    newMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
    newMapper.setDefaultPropertyInclusion(Include.ALWAYS)
    newMapper
  }

  /**
   * Creates a string representation of the given [[JsonNode]] with entries
   * sorted alphabetically by key.
   *
   * @param jsonNode - input [[JsonNode]]
   * @return string representation of the [[JsonNode]].
   */
  def toSortedString(jsonNode: JsonNode): String = {
    if (jsonNode.isTextual) {
      jsonNode.textValue()
    } else {
      val node = sortingObjectMapper.treeToValue(jsonNode, classOf[Object])
      sortingObjectMapper.writeValueAsString(node)
    }
  }

  /** A [[JsonDiff]] result */
  case class Result(
    expected: JsonNode,
    expectedPrettyString: String,
    actual: JsonNode,
    actualPrettyString: String) {

    private[this] val expectedJsonSorted = JsonDiff.toSortedString(expected)
    private[this] val actualJsonSorted = JsonDiff.toSortedString(actual)

    override val toString: String = {
      val expectedHeader = "Expected: "
      val diffStartIdx =
        actualJsonSorted.zip(expectedJsonSorted).indexWhere { case (x, y) => x != y }

      val message = new StringBuilder
      message.append(" " * (expectedHeader.length + diffStartIdx) + "*\n")
      message.append(expectedHeader + expectedJsonSorted + "\n")
      message.append(s"Actual:   $actualJsonSorted")

      message.toString()
    }
  }

  /**
   * Computes the diff for two snippets of json both of expected type `T`.
   * If a difference is detected a [[Result]] is returned otherwise a None.
   *
   * @param expected the expected json
   * @param actual the actual or received json
   * @return if a difference is detected a [[Result]]
   *         is returned otherwise a None.
   */
  def diff[T](expected: T, actual: T): Option[Result] =
    diff[T](expected, actual, None)

  /**
   * Computes the diff for two snippets of json both of expected type `T`.
   * If a difference is detected a [[Result]] is returned otherwise a None.
   *
   * @param expected the expected json
   * @param actual the actual or received json
   * @param normalizeFn a function to apply to the actual json in order to "normalize" values.
   *
   * @return if a difference is detected a [[Result]]
   *         is returned otherwise a None.
   *
   * ==Usage==
   * {{{
   *
   *   private def normalize(jsonNode: JsonNode): JsonNode = jsonNode match {
   *     case on: ObjectNode => on.put("time", "1970-01-01T00:00:00Z")
   *     case _ => jsonNode
   *   }
   *
   *   val expected = """{"foo": "bar", "time": ""1970-01-01T00:00:00Z"}"""
   *   val actual = ??? ({"foo": "bar", "time": ""2021-05-14T00:00:00Z"})
   *   val result: Option[JsonDiff.Result] = JsonDiff.diff(expected, actual, normalize)
   * }}}
   */
  def diff[T](
    expected: T,
    actual: T,
    normalizeFn: JsonNode => JsonNode
  ): Option[Result] =
    diff[T](expected, actual, Option(normalizeFn))

  /**
   * Asserts that the actual equals the expected. Will throw an [[AssertionError]] with details
   * printed to [[System.out]].
   *
   * @param expected the expected json
   * @param actual the actual or received json
   *
   * @throws AssertionError - when the expected does not match the actual.
   */
  @throws[AssertionError]
  def assertDiff[T](expected: T, actual: T): Unit =
    assert[T](expected, actual, None, System.out)

  /**
   * Asserts that the actual equals the expected. Will throw an [[AssertionError]] with details
   * printed to [[System.out]] using the given normalizeFn to normalize the actual contents.
   *
   * @param expected the expected json
   * @param actual the actual or received json
   * @param normalizeFn a function to apply to the actual json in order to "normalize" values.
   *
   * @throws AssertionError - when the expected does not match the actual.
   */
  @throws[AssertionError]
  def assertDiff[T](
    expected: T,
    actual: T,
    normalizeFn: JsonNode => JsonNode
  ): Unit = assert[T](expected, actual, Option(normalizeFn), System.out)

  /**
   * Asserts that the actual equals the expected. Will throw an [[AssertionError]] with details
   * printed to the given [[PrintStream]].
   *
   * @param expected the expected json
   * @param actual the actual or received json
   * @param p the [[PrintStream]] for reporting details
   *
   * @throws AssertionError - when the expected does not match the actual.
   */
  @throws[AssertionError]
  def assertDiff[T](expected: T, actual: T, p: PrintStream): Unit =
    assert[T](expected, actual, None, p)

  /**
   * Asserts that the actual equals the expected. Will throw an [[AssertionError]] with details
   * printed to the given [[PrintStream]] using the given normalizeFn to normalize the actual contents.
   *
   * @param expected the expected json
   * @param actual the actual or received json
   * @param p the [[PrintStream]] for reporting details
   * @param normalizeFn a function to apply to the actual json in order to "normalize" values.
   *
   * @throws AssertionError - when the expected does not match the actual.
   */
  @throws[AssertionError]
  def assertDiff[T](
    expected: T,
    actual: T,
    normalizeFn: JsonNode => JsonNode,
    p: PrintStream
  ): Unit = assert[T](expected, actual, Option(normalizeFn), p)

  /* Private */

  private[this] def diff[T](
    expected: T,
    actual: T,
    normalizer: Option[JsonNode => JsonNode]
  ): Option[Result] = {
    val actualJson = jsonString(actual)
    val expectedJson = jsonString(expected)

    val actualJsonNode: JsonNode = {
      val jsonNode = tryJsonNodeParse(actualJson)
      normalizer match {
        case Some(normalizerFn) => normalizerFn(jsonNode)
        case _ => jsonNode
      }
    }

    val expectedJsonNode: JsonNode = tryJsonNodeParse(expectedJson)
    if (actualJsonNode != expectedJsonNode) {
      val result = Result(
        expected = expectedJsonNode,
        expectedPrettyString = mapper.writePrettyString(expectedJsonNode),
        actual = actualJsonNode,
        actualPrettyString = mapper.writePrettyString(actualJsonNode)
      )
      Some(result)
    } else None
  }

  private[this] def assert[T](
    expected: T,
    actual: T,
    normalizer: Option[JsonNode => JsonNode],
    p: PrintStream
  ): Unit = {
    diff(expected, actual, normalizer) match {
      case Some(result) =>
        p.println("JSON DIFF FAILED!")
        p.println(result)
        throw new AssertionError(s"${JsonDiff.getClass.getName} failure\n$result")
      case _ => // do nothing
    }
  }

  private[this] def tryJsonNodeParse(expectedJsonStr: String): JsonNode = {
    try {
      mapper.parse[JsonNode](expectedJsonStr)
    } catch {
      case NonFatal(e) =>
        warn(e.getMessage)
        new TextNode(expectedJsonStr)
    }
  }

  private[this] def jsonString(receivedJson: Any): String = {
    receivedJson match {
      case str: String => str
      case _ => mapper.writeValueAsString(receivedJson)
    }
  }
}
