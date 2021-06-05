package com.twitter.util.jackson

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.{
  ArrayNode,
  IntNode,
  JsonNodeFactory,
  ObjectNode,
  TextNode
}
import org.scalacheck.Gen._
import org.scalacheck._
import scala.jdk.CollectionConverters._

object JsonGenerator extends JsonGenerator

trait JsonGenerator {

  private[this] val jsonNodeFactory: JsonNodeFactory = new JsonNodeFactory(false)

  /** Generate either a ArrayNode or a ObjectNode */
  def jsonNode(depth: Int): Gen[JsonNode] = oneOf(arrayNode(depth), objectNode(depth))

  /** Generate a ArrayNode */
  def arrayNode(depth: Int): Gen[ArrayNode] = for {
    n <- choose(1, 4)
    vals <- values(n, depth)
  } yield new ArrayNode(jsonNodeFactory).addAll(vals.asJava)

  /** Generate a ObjectNode */
  def objectNode(depth: Int): Gen[ObjectNode] = for {
    n <- choose(1, 4)
    ks <- keys(n)
    vals <- values(n, depth)
  } yield {
    val kids: Map[String, JsonNode] = Map(ks.zip(vals): _*)
    new ObjectNode(jsonNodeFactory, kids.asJava)
  }

  /** Generate a list of keys to be used in the map of a ObjectNode */
  private[this] def keys(n: Int): Gen[List[String]] = {
    val key: Gen[String] = for {
      str <- alphaStr.filter(_.nonEmpty).map(_.toLowerCase)
    } yield {
      if (str.length > 4) str.substring(0, 3)
      else str
    }
    listOfN(n, key)
  }

  /**
   * Generate a list of values to be used in the map of a ObjectNode or in the list of an ArrayNode.
   */
  private[this] def values(n: Int, depth: Int): Gen[List[JsonNode]] = listOfN(n, value(depth))

  /**
   * Generate a value to be used in the map of a ObjectNode or in the list of an ArrayNode.
   */
  private[this] def value(depth: Int): Gen[JsonNode] =
    if (depth == 0) terminalNode
    else oneOf(jsonNode(depth - 1), terminalNode)

  /** Generate a terminal node */
  private[this] def terminalNode: Gen[JsonNode] =
    oneOf(
      new IntNode(4),
      new IntNode(2),
      new TextNode("b"),
      new TextNode("i"),
      new TextNode("r"),
      new TextNode("d"))
}
