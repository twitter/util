package com.twitter.util.tunable

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.{JsonDeserializer, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.util.{Return, Throw, Try}
import java.net.URL
import scala.collection.JavaConverters._

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

  /**
   * Construct String paths for JSON files starting with `root` using environmentOpt and instanceOpt
   * in priority order. Where environementOpt and instanceOpt are available (env, instance),
   * paths are ordered:
   *
   * i. $root/$env/instance-$id.json
   * i. $root/$env/instances.json
   * i. $root/instance-$id.json
   * i. $root/instances.json
   */
  def pathsByPriority(
    root: String,
    environmentOpt: Option[String],
    instanceIdOpt: Option[Long]
  ): Seq[String] = {

    val template = s"${root}%sinstance%s.json"

    val envPathParams = (environmentOpt, instanceIdOpt) match {
      case (Some(env), Some(id)) => Seq(Seq(s"$env/", s"-$id"), Seq(s"$env/", "s"))
      case (Some(env), None) => Seq(Seq(s"$env/", "s"))
      case (None, _) => Seq.empty[Seq[String]]
    }

    val instancePathParams = instanceIdOpt match {
      case Some(instanceId) => Seq(Seq("", s"-$instanceId"), Seq("", "s"))
      case None => Seq(Seq("", "s"))
    }

    val pathParams = envPathParams ++ instancePathParams

    pathParams.map(params => template.format(params: _*))
  }
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

  private[this] def jsonTunablesToTunableMap(
    jsonTunables: JsonTunables,
    source: String
  ): TunableMap = {
    val ids = jsonTunables.tunables.map(_.id)
    val uniqueIds = ids.distinct

    if (ids.size != uniqueIds.size)
      throw new IllegalArgumentException(
        s"Duplicate Toggle ids found: ${ids.mkString(",")}")

    if (jsonTunables.tunables.isEmpty) {
      NullTunableMap
    } else {
      val tunableMap = TunableMap.newMutable(source)

      jsonTunables.tunables.map { jsonTunable =>
        val valueAsValueType = mapper.convertValue(jsonTunable.value, jsonTunable.valueType)
        tunableMap.put(jsonTunable.id, jsonTunable.valueType, valueAsValueType)
      }
      tunableMap
    }
  }

  /**
   * Parse the contents of the given file URL `url` into a [[TunableMap]]
   */
  private[this] def parse(url: URL): Try[TunableMap] = Try {
    jsonTunablesToTunableMap(mapper.readValue(url, classOf[JsonTunables]), url.toString)
  }

  // Exposed for testing
  private[tunable] def tunableMapForResources(id: String, paths: List[URL]): TunableMap =
    paths match {
      case Nil =>
        NullTunableMap
      case path::Nil =>
        parse(path) match {
          case Throw(t) =>
            throw new IllegalArgumentException(
              s"Failed to parse Tunable configuration file for $id, from $path", t)
          case Return(tunableMap) =>
            tunableMap
        }
      case _ =>
        throw new IllegalArgumentException(
          s"Found multiple Tunable configuration files for $id: ${paths.mkString(", ")}")
    }

  /**
   * Load and parse the JSON file located at `path` in the application's resources.
   *
   * If no configuration files exist, return [[NullTunableMap]].
   * If multiple configuration files exists, return [[IllegalArgumentException]]
   * If the configuration file cannot be parsed, return [[IllegalArgumentException]]
   */
  private[twitter] def loadJsonTunables(id: String, path: String): TunableMap = {
    val classLoader = getClass.getClassLoader
    val files = classLoader.getResources(path).asScala.toList
    tunableMapForResources(id, files)
  }

  /**
   * Parse the given JSON string `json` into a [[TunableMap]]
   */
  def parse(json: String): Try[TunableMap] = Try {
    jsonTunablesToTunableMap(mapper.readValue(json, classOf[JsonTunables]), "JSON String")
  }
}
