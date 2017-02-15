package com.twitter.util.tunable

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.{JsonDeserializer, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.util.Try

private[twitter] object JsonTunableMapper {

  import com.twitter.util.tunable.json._

  private case class JsonTunable(
      @JsonProperty(required = true) id: String,
      @JsonProperty(value = "type", required = true) valueType: Class[Any],
      @JsonProperty(required = true) value: Any)

  private case class JsonTunables(
      @JsonProperty(required = true) tunables: Seq[JsonTunable])

  /**
   * The Deserializers that [[JsonTunableMapper]] uses by default, in addition to Scala data type
   * deserializers afforded by [[com.fasterxml.jackson.module.scala.DefaultScalaModule]].
   *
   * These deserializers are:
   *
   * - [[com.twitter.util.tunable.json.DurationFromString]]
   */
  val DefaultDeserializers: Seq[JsonDeserializer[_]] = Seq(DurationFromString)

  /**
   * Create a new [[JsonTunableMapper]], using the provided deserializers `deserializers`.
   */
  def apply(deserializers: Seq[JsonDeserializer[_ <: Any]]): JsonTunableMapper =
    new JsonTunableMapper(deserializers)

  /**
   * Create a new [[JsonTunableMapper]], using the default deserializers, [[DefaultDeserializers]]
   */
  def apply(): JsonTunableMapper =
    apply(JsonTunableMapper.DefaultDeserializers)
}

/**
 * Parses a given JSON string into a [[TunableMap]]. The expected format is:
 *
 * {
 *    "tunables":
 *      [
 *         {
 *           "id" : "$id1",
 *           "value" : $value,
 *           "type" : "$class"
 *         },
 *         {
 *           "id" : "$id2",
 *           "value" : $value,
 *           "type" : "$class"
 *         }
 *     ]
 * }
 *
 * Where $id1 and $id2 are unique identifiers used to access the [[Tunable]], $value is the value,
 * and $class is the fully-qualified class name (e.g. com.twitter.util.Duration)
 *
 * If the JSON is invalid, or contains duplicate ids for [[Tunable]]s, `parse` will
 * return a [[Throw]]. Otherwise, `parse` returns [[Return[TunableMap]]
 */
private[twitter] final class JsonTunableMapper(deserializers: Seq[JsonDeserializer[_ <: Any]]) {

  import JsonTunableMapper._

  private[this] object DeserializationModule extends SimpleModule {
    deserializers.foreach {
      jd => addDeserializer(jd.handledType().asInstanceOf[Class[Any]], jd)
    }
  }

  private[this] val mapper: ObjectMapper =
    new ObjectMapper().registerModules(DefaultScalaModule, DeserializationModule)

  def parse(json: String): Try[TunableMap] = Try {
    val jsonTunables = mapper.readValue(json, classOf[JsonTunables])

    val ids = jsonTunables.tunables.map(_.id)
    val uniqueIds = ids.distinct

    if (ids.size != uniqueIds.size)
      throw new IllegalArgumentException(
        s"Duplicate Toggle ids found: ${ids.mkString(",")}")

    val tunableMap = TunableMap.newMutable()

    jsonTunables.tunables.map { jsonTunable =>
      val valueAsValueType = mapper.convertValue(jsonTunable.value, jsonTunable.valueType)
      tunableMap.put(jsonTunable.id, jsonTunable.valueType, valueAsValueType)
    }
    tunableMap
  }
}
